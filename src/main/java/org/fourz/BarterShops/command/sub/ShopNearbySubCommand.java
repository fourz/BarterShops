package org.fourz.BarterShops.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.BarterShops.sign.renderer.SignRenderUtil;

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
        sender.sendMessage(ChatColor.GRAY + String.format("%-10s %-6s %-22s %s",
                "TYPE", "Dist", "Offering", "Owner"));

        for (Map.Entry<Location, BarterSign> entry : nearbyShops) {
            Location shopLocation = entry.getKey();
            BarterSign sign = entry.getValue();

            String ownerName = plugin.getPlayerLookup().getPlayerName(sign.getOwner());
            if (ownerName.length() > 16) ownerName = ownerName.substring(0, 13) + "...";

            int distance = (int) shopLocation.distance(playerLoc);

            String offeringStr = "";
            if (sign.getItemOffering() != null) {
                offeringStr = SignRenderUtil.formatItemName(sign.getItemOffering()) +
                        " x" + sign.getItemOffering().getAmount();
            }

            String row = String.format("%-10s %-6s %-22s %s",
                    sign.getType(),
                    distance + "m",
                    offeringStr,
                    ownerName);

            sender.sendMessage(ChatColor.WHITE + row);
        }

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
        return "bartershops.use";
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
