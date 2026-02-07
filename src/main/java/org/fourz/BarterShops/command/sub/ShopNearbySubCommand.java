package org.fourz.BarterShops.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.sign.BarterSign;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Subcommand for finding nearby shops.
 * Usage: /shop nearby [radius]
 * Player-only: Yes (needs location)
 */
public class ShopNearbySubCommand implements SubCommand {

    private final BarterShops plugin;
    private static final int DEFAULT_RADIUS = 50;
    private static final int MAX_RADIUS = 200;

    public ShopNearbySubCommand(BarterShops plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        int radius = DEFAULT_RADIUS;
        if (args.length > 0) {
            try {
                radius = Integer.parseInt(args[0]);
                radius = Math.max(1, Math.min(radius, MAX_RADIUS));
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid radius: " + args[0]);
                return true;
            }
        }

        Location playerLoc = player.getLocation();
        Map<Location, BarterSign> allShops = plugin.getSignManager().getBarterSigns();

        // Find shops within radius
        final int searchRadius = radius;
        List<Map.Entry<Location, BarterSign>> nearbyShops = allShops.entrySet().stream()
                .filter(entry -> {
                    Location shopLocation = entry.getKey();
                    if (!shopLocation.getWorld().equals(playerLoc.getWorld())) return false;
                    return shopLocation.distance(playerLoc) <= searchRadius;
                })
                .sorted(Comparator.comparingDouble(entry ->
                        entry.getKey().distance(playerLoc)))
                .toList();

        if (nearbyShops.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No shops found within " + radius + " blocks.");
            return true;
        }

        // Display results
        sender.sendMessage(ChatColor.GREEN + "=== Shops within " + radius + " blocks ===");
        sender.sendMessage(ChatColor.GRAY + String.format("%-16s %-12s %-8s",
                "Owner", "Type", "Distance"));
        sender.sendMessage(ChatColor.GRAY + "------------------------------------");

        for (Map.Entry<Location, BarterSign> entry : nearbyShops) {
            Location shopLocation = entry.getKey();
            BarterSign sign = entry.getValue();

            String ownerName = Bukkit.getOfflinePlayer(sign.getOwner()).getName();
            if (ownerName == null) ownerName = "Unknown";
            if (ownerName.length() > 15) ownerName = ownerName.substring(0, 12) + "...";

            int distance = (int) shopLocation.distance(playerLoc);

            String row = String.format("%-16s %-12s %-8s",
                    ownerName,
                    sign.getType(),
                    distance + "m");

            sender.sendMessage(ChatColor.WHITE + row);
        }

        sender.sendMessage(ChatColor.GRAY + "------------------------------------");
        sender.sendMessage(ChatColor.GREEN + "Found " + nearbyShops.size() + " shop(s)");

        return true;
    }

    @Override
    public String getDescription() {
        return "Find shops near your location";
    }

    @Override
    public String getUsage() {
        return "/shop nearby [radius]";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.isOp();
    }

    @Override
    public String getPermission() {
        return "bartershops.command.nearby";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0];
            // Suggest common radius values
            for (String radius : List.of("25", "50", "100", "200")) {
                if (radius.startsWith(partial)) {
                    completions.add(radius);
                }
            }
        }

        return completions;
    }

    @Override
    public boolean requiresPlayer() {
        return true;
    }
}
