/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fourz.BarterShops;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin{
    private static Main instance;

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("BarterShops has been loaded");
    }

    @Override
    public void onDisable() {
        getLogger().info("BarterShops has been unloaded");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if(sender instanceof Player){
            Player player = (Player)sender;
            player.sendMessage("Hello, "+player.getName()+"!");
        }else{
            sender.sendMessage("This command can only be run by players!");
        }
        return true;
    }
}
