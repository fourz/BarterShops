package org.fourz.BarterShops.command;

import org.bukkit.command.CommandSender;
import org.fourz.BarterShops.Main;

public class ReloadCommand implements BaseCommand {
    private final Main plugin;

    public ReloadCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        plugin.getConfigManager().reloadConfig();
        sender.sendMessage("Configuration reloaded!");
    }

    @Override
    public String getPermission() {
        return "bartershops.command.reload";
    }

    @Override
    public String getDescription() {
        return "Reloads the plugin configuration";
    }

    @Override
    public String getUsage() {
        return "/shop reload";
    }
}
