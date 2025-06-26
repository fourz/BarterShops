package org.fourz.BarterShops.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;

public class NearbyCommand implements BaseCommand {
    private final BarterShops plugin;

    public NearbyCommand(BarterShops plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
            return;
        }
        sender.sendMessage("Finding nearby shops..."); // Implement actual nearby shop logic
    }

    @Override
    public String getPermission() {
        return "bartershops.command.nearby";
    }

    @Override
    public String getDescription() {
        return "Shows nearby shops";
    }

    @Override
    public String getUsage() {
        return "/shop nearby";
    }
}
