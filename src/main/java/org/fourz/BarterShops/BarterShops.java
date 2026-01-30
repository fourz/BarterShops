package org.fourz.BarterShops;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.BarterShops.command.CommandManager;
import org.fourz.BarterShops.config.ConfigManager;
import org.fourz.BarterShops.sign.SignManager;
import org.fourz.BarterShops.container.ContainerManager;
import org.fourz.BarterShops.shop.ShopManager;  
import org.fourz.rvnkcore.util.log.LogManager;

public class BarterShops extends JavaPlugin {
    private ConfigManager configManager;
    private CommandManager commandManager;
    private SignManager signManager;
    private ContainerManager containerManager;
    private ShopManager shopManager;
    private final LogManager logger;

    public BarterShops() {
        this.logger = LogManager.getInstance(this, "BarterShops");
    }

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        LogManager.setPluginLogLevel(this, configManager.getLogLevel());
        this.commandManager = new CommandManager(this);
        this.signManager = new SignManager(this);
        this.containerManager = new ContainerManager(this);
        this.shopManager = new ShopManager(this);
        logger.info("BarterShops has been loaded");
    }

    @Override
    public void onDisable() {
        if (logger == null) {
            getLogger().warning("Logger was null during shutdown");
            return;
        }
        
        logger.info("BarterShops is shutting down...");
        
        try {
            cleanupManagers();
        } catch (RuntimeException e) {
            logger.error("Failed to cleanup managers", e);
        } finally {
            logger.info("BarterShops has been unloaded");
        }
    }

    private void cleanupManagers() {
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
}