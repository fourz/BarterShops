package org.fourz.BarterShops;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.BarterShops.command.CommandManager;
import org.fourz.BarterShops.config.ConfigManager;
import org.fourz.BarterShops.sign.SignManager;
import org.fourz.BarterShops.container.ContainerManager;

public class Main extends JavaPlugin {
    private ConfigManager configManager;
    private CommandManager commandManager;
    private SignManager signManager;
    private ContainerManager containerManager;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.commandManager = new CommandManager(this);
        this.signManager = new SignManager(this);
        this.containerManager = new ContainerManager(this);
        getLogger().info("BarterShops has been loaded");
    }

    @Override
    public void onDisable() {
        getLogger().info("BarterShops has been unloaded");
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
}
