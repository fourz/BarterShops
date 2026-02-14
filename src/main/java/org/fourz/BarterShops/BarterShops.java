package org.fourz.BarterShops;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.BarterShops.command.CommandManager;
import org.fourz.BarterShops.config.ConfigManager;
import org.fourz.BarterShops.config.TypeAvailabilityManager;
import org.fourz.BarterShops.data.FallbackTracker;
import org.fourz.BarterShops.data.IConnectionProvider;
import org.fourz.BarterShops.data.repository.IShopRepository;
import org.fourz.BarterShops.data.repository.impl.ConnectionProviderImpl;
import org.fourz.BarterShops.data.repository.impl.ShopRepositoryImpl;
import org.fourz.BarterShops.economy.EconomyManager;
import org.fourz.BarterShops.economy.ShopFeeCalculator;
import org.fourz.BarterShops.notification.NotificationManager;
import org.fourz.BarterShops.protection.ProtectionManager;
import org.fourz.BarterShops.data.repository.IRatingRepository;
import org.fourz.BarterShops.data.repository.ITradeRepository;
import org.fourz.BarterShops.data.repository.impl.RatingRepositoryImpl;
import org.fourz.BarterShops.data.repository.impl.TradeRepositoryImpl;
import org.fourz.BarterShops.service.IShopService;
import org.fourz.BarterShops.service.IRatingService;
import org.fourz.BarterShops.service.IStatsService;
import org.fourz.BarterShops.service.ITradeService;
import org.fourz.BarterShops.service.impl.RatingServiceImpl;
import org.fourz.BarterShops.service.impl.StatsServiceImpl;
import org.fourz.BarterShops.service.impl.TradeServiceImpl;
import org.fourz.BarterShops.sign.SignManager;
import org.fourz.BarterShops.container.ContainerManager;
import org.fourz.BarterShops.shop.ShopManager;
import org.fourz.BarterShops.trade.TradeEngine;
import org.fourz.BarterShops.trade.TradeConfirmationGUI;
import org.fourz.BarterShops.template.TemplateManager;
import org.fourz.rvnkcore.util.PlayerLookup;
import org.fourz.rvnkcore.util.log.LogManager;

public class BarterShops extends JavaPlugin {
    private ConfigManager configManager;
    private CommandManager commandManager;
    private SignManager signManager;
    private ContainerManager containerManager;
    private ShopManager shopManager;
    private TradeEngine tradeEngine;
    private TradeConfirmationGUI tradeConfirmationGUI;
    private NotificationManager notificationManager;
    private TemplateManager templateManager;
    private ProtectionManager protectionManager;
    private IRatingService ratingService;
    private IStatsService statsService;
    private EconomyManager economyManager;
    private ShopFeeCalculator feeCalculator;
    private TypeAvailabilityManager typeAvailabilityManager;
    private LogManager logger;
    private PlayerLookup playerLookup;

    // Database layer (impl-11)
    private ConnectionProviderImpl connectionProvider;
    private FallbackTracker fallbackTracker;
    private IShopRepository shopRepository;
    private ITradeRepository tradeRepository;
    private TradeServiceImpl tradeService;

    // Plugin lifecycle tracking
    private long startTime;

    // RVNKCore integration
    private boolean rvnkCoreAvailable = false;
    private Object rvnkCoreInstance = null;

    @Override
    public void onEnable() {
        this.startTime = System.currentTimeMillis();
        this.logger = LogManager.getInstance(this, "BarterShops");

        // Log version for deployment verification
        logger.info("BarterShops v" + this.getDescription().getVersion() + " - Enabling");

        this.configManager = new ConfigManager(this);
        LogManager.setPluginLogLevel(this, configManager.getLogLevel());
        logger.info("Log level set to: " + configManager.getLogLevel());
        this.notificationManager = new NotificationManager(this);
        this.templateManager = new TemplateManager(this);
        this.protectionManager = new ProtectionManager(this);
        this.economyManager = new EconomyManager(this);
        this.feeCalculator = new ShopFeeCalculator(economyManager);
        this.typeAvailabilityManager = new TypeAvailabilityManager(this, configManager, economyManager);

        // Initialize database layer BEFORE SignManager (bug-35: SignManager needs shopRepository in constructor)
        initializeDatabaseLayer();

        this.signManager = new SignManager(this);
        this.containerManager = new ContainerManager(this);
        this.shopManager = new ShopManager(this);
        this.tradeEngine = new TradeEngine(this);
        this.tradeConfirmationGUI = new TradeConfirmationGUI(this);

        // Load signs from database now that both signManager and shopRepository are initialized
        if (signManager != null && shopRepository != null) {
            signManager.loadSignsFromDatabase();
        }

        // Initialize RatingService (requires database layer)
        initializeRatingService();

        // Initialize StatsService (requires ShopService + RatingService)
        initializeStatsService();

        // Register with RVNKCore ServiceRegistry if available
        registerWithRVNKCore();

        // CommandManager after services so conditional subcommands (rate/reviews/stats) register (bug-11)
        this.commandManager = new CommandManager(this);

        // Initialize PlayerLookup (after RVNKCore registration so PlayerService is available)
        this.playerLookup = new PlayerLookup(this);

        logger.info("BarterShops has been loaded");
    }

    @Override
    public void onDisable() {
        if (logger == null) {
            getLogger().warning("Logger was null during shutdown");
            return;
        }

        logger.info("BarterShops is shutting down...");

        // Unregister from RVNKCore before cleanup
        unregisterFromRVNKCore();

        try {
            cleanupManagers();
        } catch (RuntimeException e) {
            logger.error("Failed to cleanup managers", e);
        } finally {
            logger.info("BarterShops has been unloaded");
        }
    }

    /**
     * Initializes the database layer (ConnectionProvider, FallbackTracker, ShopRepository).
     */
    private void initializeDatabaseLayer() {
        try {
            this.fallbackTracker = new FallbackTracker(this);
            this.connectionProvider = new ConnectionProviderImpl(this);
            this.connectionProvider.initialize();
            this.shopRepository = new ShopRepositoryImpl(this, connectionProvider, fallbackTracker);
            this.tradeRepository = new TradeRepositoryImpl(this, connectionProvider, fallbackTracker);
            this.tradeService = new TradeServiceImpl(this, tradeRepository, fallbackTracker);

            logger.info("Database layer initialized successfully (" + connectionProvider.getDatabaseType() + ")");
        } catch (Exception e) {
            logger.warning("Failed to initialize database layer: " + e.getMessage());
            logger.warning("Running in fallback mode (in-memory storage)");
            // Fallback: create a fallback tracker in failed state
            if (fallbackTracker != null) {
                fallbackTracker.enterFallbackMode("Database initialization failed: " + e.getMessage());
            }
        }
    }

    /**
     * Initializes the RatingService with its repository dependency.
     */
    private void initializeRatingService() {
        if (connectionProvider == null || fallbackTracker == null) {
            logger.info("RatingService skipped — database layer not available");
            return;
        }
        try {
            IRatingRepository ratingRepo = new RatingRepositoryImpl(this, connectionProvider, fallbackTracker);
            this.ratingService = new RatingServiceImpl(this, ratingRepo, shopRepository);
            logger.info("RatingService initialized");
        } catch (Exception e) {
            logger.warning("Failed to initialize RatingService: " + e.getMessage());
        }
    }

    /**
     * Initializes the StatsService with ShopService and RatingService dependencies.
     */
    private void initializeStatsService() {
        Object shopServiceObj = createShopService();
        if (shopServiceObj == null) {
            logger.info("StatsService skipped — ShopServiceImpl not available");
            return;
        }
        try {
            this.statsService = new StatsServiceImpl(this, (IShopService) shopServiceObj, ratingService);
            logger.info("StatsService initialized");
        } catch (Exception e) {
            logger.warning("Failed to initialize StatsService: " + e.getMessage());
        }
    }

    /**
     * Gets the plugin start time in milliseconds since epoch.
     *
     * @return Plugin start timestamp
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Checks if RVNKCore integration is available.
     *
     * @return true if RVNKCore services are registered
     */
    public boolean isRVNKCoreAvailable() {
        return rvnkCoreAvailable;
    }

    /**
     * Registers services with RVNKCore ServiceRegistry if available.
     * Uses reflection to avoid hard dependency on RVNKCore classes.
     */
    private void registerWithRVNKCore() {
        Plugin rvnkCorePlugin = getServer().getPluginManager().getPlugin("RVNKCore");
        if (rvnkCorePlugin == null || !rvnkCorePlugin.isEnabled()) {
            logger.info("RVNKCore not found - running in standalone mode");
            return;
        }

        try {
            // Get RVNKCore instance via static getInstance() method
            Class<?> rvnkCoreClass = Class.forName("org.fourz.rvnkcore.RVNKCore");
            Object coreInstance = rvnkCoreClass.getMethod("getInstance").invoke(null);
            if (coreInstance == null) {
                logger.warning("RVNKCore instance is null - services not registered");
                return;
            }

            // Get the ServiceRegistry from RVNKCore
            Object serviceRegistry = rvnkCoreClass.getMethod("getServiceRegistry").invoke(coreInstance);
            if (serviceRegistry == null) {
                logger.warning("RVNKCore ServiceRegistry is null - services not registered");
                return;
            }

            // Get the registerService method
            Class<?> registryClass = serviceRegistry.getClass();
            java.lang.reflect.Method registerMethod = registryClass.getMethod("registerService", Class.class, Object.class);

            // Register ShopServiceImpl if available
            // ShopServiceImpl is the concrete implementation of IShopService
            Object shopService = createShopService();
            if (shopService != null) {
                registerMethod.invoke(serviceRegistry, IShopService.class, shopService);
                logger.info("Registered IShopService with RVNKCore");
            } else {
                logger.info("ShopServiceImpl not available - skipping IShopService registration");
            }

            // Register IRatingService
            if (ratingService != null) {
                registerMethod.invoke(serviceRegistry, IRatingService.class, ratingService);
                logger.info("Registered IRatingService with RVNKCore");
            }

            // Register IStatsService
            if (statsService != null) {
                registerMethod.invoke(serviceRegistry, IStatsService.class, statsService);
                logger.info("Registered IStatsService with RVNKCore");
            }

            // Register ITradeService
            if (tradeService != null) {
                registerMethod.invoke(serviceRegistry, ITradeService.class, tradeService);
                logger.info("Registered ITradeService with RVNKCore");
            }

            rvnkCoreAvailable = true;
            rvnkCoreInstance = coreInstance;
            logger.info("RVNKCore integration enabled - services registered");

        } catch (ClassNotFoundException e) {
            logger.info("RVNKCore classes not found - running in standalone mode");
        } catch (NoClassDefFoundError e) {
            // Extract just the class name from the message for clarity
            String missingClass = e.getMessage().replaceAll(".*/", "").replaceAll("\\s.*", "");
            logger.info("Running in standalone mode (missing dependency: " + missingClass + ")");
        } catch (Exception e) {
            logger.warning("Failed to register with RVNKCore: " + e.getMessage());
            logger.warning("Running in standalone mode");
        }
    }

    /**
     * Creates the ShopServiceImpl instance for RVNKCore registration.
     * Returns null if the implementation is not yet available.
     *
     * @return ShopServiceImpl instance or null
     */
    private Object createShopService() {
        try {
            // Try to instantiate ShopServiceImpl if it exists
            Class<?> implClass = Class.forName("org.fourz.BarterShops.service.impl.ShopServiceImpl");
            // Pass plugin and repository to constructor
            return implClass.getConstructor(BarterShops.class, IShopRepository.class)
                    .newInstance(this, shopRepository);
        } catch (ClassNotFoundException e) {
            // ShopServiceImpl not yet implemented - this is expected during development
            logger.debug("ShopServiceImpl not found - impl-11 pending");
            return null;
        } catch (NoSuchMethodException e) {
            // Fall back to plugin-only constructor if repository constructor not available
            try {
                Class<?> implClass = Class.forName("org.fourz.BarterShops.service.impl.ShopServiceImpl");
                return implClass.getConstructor(BarterShops.class).newInstance(this);
            } catch (Exception ex) {
                logger.warning("Failed to create ShopServiceImpl: " + ex.getMessage());
                return null;
            }
        } catch (Exception e) {
            logger.warning("Failed to create ShopServiceImpl: " + e.getMessage());
            return null;
        }
    }


    /**
     * Unregisters services from RVNKCore ServiceRegistry.
     */
    private void unregisterFromRVNKCore() {
        if (!rvnkCoreAvailable || rvnkCoreInstance == null) {
            return;
        }

        try {
            Class<?> rvnkCoreClass = rvnkCoreInstance.getClass();
            Object serviceRegistry = rvnkCoreClass.getMethod("getServiceRegistry").invoke(rvnkCoreInstance);
            if (serviceRegistry == null) {
                return;
            }

            Class<?> registryClass = serviceRegistry.getClass();
            java.lang.reflect.Method unregisterMethod = registryClass.getMethod("unregisterService", Class.class);

            // Unregister services in reverse order
            unregisterMethod.invoke(serviceRegistry, IStatsService.class);
            unregisterMethod.invoke(serviceRegistry, IShopService.class);
            unregisterMethod.invoke(serviceRegistry, IRatingService.class);
            unregisterMethod.invoke(serviceRegistry, ITradeService.class);
            // unregisterMethod.invoke(serviceRegistry, IShopDatabaseService.class);

            logger.info("Services unregistered from RVNKCore");

        } catch (NoClassDefFoundError e) {
            // Soft-dependency classes not available during shutdown - safe to ignore
        } catch (Exception e) {
            logger.warning("Failed to unregister from RVNKCore: " + e.getMessage());
        }

        rvnkCoreAvailable = false;
        rvnkCoreInstance = null;
    }

    private void cleanupManagers() {
        cleanupManager("notification", () -> {
            if (notificationManager != null) {
                notificationManager.shutdown();
                notificationManager = null;
            }
        });

        cleanupManager("protection", () -> {
            if (protectionManager != null) {
                protectionManager.cleanup();
                protectionManager = null;
            }
        });

        cleanupManager("container", () -> {
            if (containerManager != null) {
                containerManager.cleanup();
                containerManager = null;
            }
        });

        cleanupManager("sign", () -> {
            if (signManager != null) {
                signManager.cleanup();
                signManager = null;
            }
        });

        cleanupManager("command", () -> {
            if (commandManager != null) {
                commandManager.cleanup();
                commandManager = null;
            }
        });

        cleanupManager("config", () -> {
            if (configManager != null) {
                configManager.cleanup();
                configManager = null;
            }
        });

        cleanupManager("shop", () -> {
            if (shopManager != null) {
                shopManager.cleanup();
                shopManager = null;
            }
        });

        cleanupManager("trade", () -> {
            if (tradeEngine != null) {
                tradeEngine.shutdown();
                tradeEngine = null;
            }
        });

        cleanupManager("tradeGUI", () -> {
            if (tradeConfirmationGUI != null) {
                tradeConfirmationGUI.shutdown();
                tradeConfirmationGUI = null;
            }
        });

        cleanupManager("template", () -> {
            if (templateManager != null) {
                templateManager.cleanup();
                templateManager = null;
            }
        });

        cleanupManager("tradeService", () -> {
            tradeService = null;
        });

        cleanupManager("tradeRepository", () -> {
            if (tradeRepository != null && tradeRepository instanceof TradeRepositoryImpl) {
                ((TradeRepositoryImpl) tradeRepository).shutdown();
                tradeRepository = null;
            }
        });

        cleanupManager("shopRepository", () -> {
            if (shopRepository != null && shopRepository instanceof ShopRepositoryImpl) {
                ((ShopRepositoryImpl) shopRepository).shutdown();
                shopRepository = null;
            }
        });

        cleanupManager("connectionProvider", () -> {
            if (connectionProvider != null) {
                connectionProvider.shutdown();
                connectionProvider = null;
            }
        });
    }

    private void cleanupManager(String managerName, Runnable cleanupTask) {
        try {
            logger.debug("Cleaning up " + managerName + " manager...");
            cleanupTask.run();
        } catch (RuntimeException e) {
            logger.error("Failed to cleanup " + managerName + " manager", e);
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public SignManager getSignManager() {
        return signManager;
    }

    public ContainerManager getContainerManager() {
        return containerManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public TradeEngine getTradeEngine() {
        return tradeEngine;
    }

    public TradeConfirmationGUI getTradeConfirmationGUI() {
        return tradeConfirmationGUI;
    }

    public NotificationManager getNotificationManager() {
        return notificationManager;
    }

    public TemplateManager getTemplateManager() {
        return templateManager;
    }

    public ProtectionManager getProtectionManager() {
        return protectionManager;
    }

    public IRatingService getRatingService() {
        return ratingService;
    }

    public void setRatingService(IRatingService ratingService) {
        this.ratingService = ratingService;
    }

    public IStatsService getStatsService() {
        return statsService;
    }

    public void setStatsService(IStatsService statsService) {
        this.statsService = statsService;
    }

    public IShopRepository getShopRepository() {
        return shopRepository;
    }

    public IConnectionProvider getConnectionProvider() {
        return connectionProvider;
    }

    public FallbackTracker getFallbackTracker() {
        return fallbackTracker;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public ShopFeeCalculator getFeeCalculator() {
        return feeCalculator;
    }

    public TradeServiceImpl getTradeService() {
        return tradeService;
    }

    public ITradeRepository getTradeRepository() {
        return tradeRepository;
    }

    public PlayerLookup getPlayerLookup() {
        return playerLookup;
    }

    public TypeAvailabilityManager getTypeAvailabilityManager() {
        return typeAvailabilityManager;
    }
}
