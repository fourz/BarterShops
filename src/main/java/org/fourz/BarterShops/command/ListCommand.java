package org.fourz.BarterShops.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.ChatColor;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.sign.BarterSign;
import java.util.Map;

public class ListCommand implements BaseCommand {
    private final BarterShops plugin;

    public ListCommand(BarterShops plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Map<Location, BarterSign> shops = plugin.getSignManager().getBarterSigns();

        if (shops.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No shops found!");
            return;
        }

        // Header
        sender.sendMessage(ChatColor.GREEN + "=== Barter Shops ===");
        sender.sendMessage(ChatColor.GRAY + String.format("%-16s %-12s %-24s %-10s",
            "Owner", "Type", "Location", "Mode"));
        sender.sendMessage(ChatColor.GRAY + "------------------------------------------------");

        // Shop entries
        for (Map.Entry<Location, BarterSign> entry : shops.entrySet()) {
            Location location = entry.getKey();
            BarterSign sign = entry.getValue();

            String ownerName = plugin.getPlayerLookup().getPlayerName(sign.getOwner());
            if (ownerName.length() > 15) ownerName = ownerName.substring(0, 12) + "...";

            String locationStr = String.format("%d,%d,%d",
                location.getBlockX(), location.getBlockY(), location.getBlockZ());

            String row = String.format("%-16s %-12s %-24s %-10s",
                ownerName,
                sign.getType(),
                locationStr,
                sign.getMode());

            sender.sendMessage(ChatColor.WHITE + row);
        }

        // Footer
        sender.sendMessage(ChatColor.GRAY + "------------------------------------------------");
        sender.sendMessage(ChatColor.GREEN + "Total shops: " + ChatColor.WHITE + shops.size());
    }

    @Override
    public String getPermission() {
        return "bartershops.command.list";
    }

    @Override
    public String getDescription() {
        return "Lists all shops";
    }

    @Override
    public String getUsage() {
        return "/shop list";
    }
}
