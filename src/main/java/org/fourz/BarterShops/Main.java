/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fourz.BarterShops;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.BarterShops.command.CommandManager;
import org.fourz.BarterShops.config.ConfigManager;

public class Main extends JavaPlugin{
    private ConfigManager configManager;
    private CommandManager commandManager;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.commandManager = new CommandManager(this);
        getLogger().info("BarterShops has been loaded");
    }

    @Override
    public void onDisable() {
        getLogger().info("BarterShops has been unloaded");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("shops")) {
            return commandManager.handleCommand(sender, cmd, label, args);
        }
        return false;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
    
}
