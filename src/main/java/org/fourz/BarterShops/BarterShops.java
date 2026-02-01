package org.fourz.BarterShops;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.BarterShops.command.CommandManager;
import org.fourz.BarterShops.config.ConfigManager;
import org.fourz.BarterShops.data.DatabaseManager;
import org.fourz.BarterShops.notification.NotificationManager;
import org.fourz.BarterShops.service.IShopService;
import org.fourz.BarterShops.sign.SignManager;
import org.fourz.BarterShops.container.ContainerManager;
import org.fourz.BarterShops.shop.ShopManager;
import org.fourz.BarterShops.trade.TradeEngine;
import org.fourz.BarterShops.trade.TradeConfirmationGUI;
import org.fourz.BarterShops.template.TemplateManager;
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
    private DatabaseManager databaseManager;
    private final LogManager logger;

    // Plugin lifecycle tracking
    private long startTime;

    // RVNKCore integration
    private boolean rvnkCoreAvailable = false;
    private Object rvnkCoreInstance = null;

    public BarterShops() {
        this.logger = LogManager.getInstance(this, "BarterShops");
    }

    @Override
    public void onEnable() {
        this.startTime = System.currentTimeMillis();

        this.configManager = new ConfigManager(this);
        LogManager.setPluginLogLevel(this, configManager.getLogLevel());
        this.notificationManager = new NotificationManager(this);
        this.templateManager = new TemplateManager(this);
        this.commandManager = new CommandManager(this);
        this.signManager = new SignManager(this);
        this.containerManager = new ContainerManager(this);
        this.shopManager = new ShopManager(this);
        this.tradeEngine = new TradeEngine(this);
        this.tradeConfirmationGUI = new TradeConfirmationGUI(this);

        // TODO: Initialize DatabaseManager when repository implementation is complete
        // this.databaseManager = new DatabaseFactory().createDatabaseManager(this);

        // Register with RVNKCore ServiceRegistry if available
        registerWithRVNKCore();

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

            // TODO: Register ITradeService when TradeServiceImpl is implemented
            // registerMethod.invoke(serviceRegistry, ITradeService.class, tradeService);

            rvnkCoreAvailable = true;
            rvnkCoreInstance = coreInstance;
            logger.info("RVNKCore integration enabled - services registered");

        } catch (ClassNotFoundException e) {
            logger.info("RVNKCore classes not found - running in standalone mode");
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
            return implClass.getConstructor(BarterShops.class).newInstance(this);
        } catch (ClassNotFoundException e) {
            // ShopServiceImpl not yet implemented - this is expected during development
            logger.debug("ShopServiceImpl not found - impl-11 pending");
            return null;
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
            unregisterMethod.invoke(serviceRegistry, IShopService.class);
            // unregisterMethod.invoke(serviceRegistry, ITradeService.class);
            // unregisterMethod.invoke(serviceRegistry, IShopDatabaseService.class);

            logger.info("Services unregistered from RVNKCore");

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

        cleanupManager("database", () -> {
            if (databaseManager != null) {
                try {
                    databaseManager.disconnect();
                } catch (Exception e) {
                    logger.warning("Failed to disconnect database: " + e.getMessage());
                }
                databaseManager = null;
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

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public TemplateManager getTemplateManager() {
        return templateManager;
    }
}
