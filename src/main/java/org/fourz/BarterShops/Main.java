package org.fourz.BarterShops;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.BarterShops.command.CommandManager;
import org.fourz.BarterShops.config.ConfigManager;
import org.fourz.BarterShops.sign.SignManager;
import org.fourz.BarterShops.container.ContainerManager;
import org.fourz.BarterShops.util.Debug;
import java.util.logging.Level;

public class Main extends JavaPlugin {
    private ConfigManager configManager;
    private CommandManager commandManager;
    private SignManager signManager;
    private ContainerManager containerManager;
    private Debug debugger;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.debugger = new Debug(this, "BarterShops", configManager.getLogLevel()) {};
        this.commandManager = new CommandManager(this);
        this.signManager = new SignManager(this);
        this.containerManager = new ContainerManager(this);
        debugger.info("BarterShops has been loaded");
    }

    @Override
    public void onDisable() {
        // Early return if debugger is null, but log to server console as fallback
        if (debugger == null) {
            getLogger().warning("Debugger was null during shutdown");
            return;
        }
        
        debugger.info("BarterShops is shutting down...");
        
        try {
            cleanupManagers();
        } catch (RuntimeException e) {  // Be more specific about exception type
            debugger.error(String.format("Failed to cleanup managers: %s", e.getMessage()), e);
            e.printStackTrace();  // Log stack trace for debugging
        } finally {
            debugger.info("BarterShops has been unloaded");
            debugger = null;
        }
    }

    private void cleanupManagers() {
        // Use specific exception types for each manager
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
    }

    private void cleanupManager(String managerName, Runnable cleanupTask) {
        try {
            debugger.debug("Cleaning up " + managerName + " manager...");
            cleanupTask.run();
        } catch (RuntimeException e) {
            debugger.error(String.format("Failed to cleanup %s manager: %s", 
                managerName, e.getMessage()), e);
            if (debugger.getLogLevel() == Level.FINE) {
                e.printStackTrace();
            }
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

    public Debug getDebugger() {
        return debugger;
    }
}
