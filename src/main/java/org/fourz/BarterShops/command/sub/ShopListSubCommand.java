package org.fourz.BarterShops.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.sign.BarterSign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Subcommand for listing shops.
 * Usage: /shop list [player]
 * Console-friendly: Yes
 */
public class ShopListSubCommand implements SubCommand {

    private final BarterShops plugin;
    private static final int ITEMS_PER_PAGE = 10;

    public ShopListSubCommand(BarterShops plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Map<Block, BarterSign> allShops = plugin.getSignManager().getBarterSigns();

        // Filter by player if specified
        UUID filterOwner = null;
        int page = 1;

        if (args.length > 0) {
            // Check if first arg is a player name or page number
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                // Try to find player
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                if (target.hasPlayedBefore() || target.isOnline()) {
                    filterOwner = target.getUniqueId();
                } else {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                    return true;
                }
            }
        }

        // If second arg exists, it's the page number
        if (args.length > 1 && filterOwner != null) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid page number: " + args[1]);
                return true;
            }
        }

        // Filter shops
        final UUID ownerFilter = filterOwner;
        List<Map.Entry<Block, BarterSign>> filteredShops = allShops.entrySet().stream()
                .filter(entry -> ownerFilter == null || entry.getValue().getOwner().equals(ownerFilter))
                .collect(Collectors.toList());

        if (filteredShops.isEmpty()) {
            if (filterOwner != null) {
                sender.sendMessage(ChatColor.YELLOW + "No shops found for that player.");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "No shops found!");
            }
            return true;
        }

        // Pagination
        int totalPages = (int) Math.ceil(filteredShops.size() / (double) ITEMS_PER_PAGE);
        page = Math.max(1, Math.min(page, totalPages));
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, filteredShops.size());

        // Header
        String header = filterOwner != null
                ? "=== Shops by " + Bukkit.getOfflinePlayer(filterOwner).getName() + " ==="
                : "=== All Barter Shops ===";
        sender.sendMessage(ChatColor.GREEN + header);
        sender.sendMessage(ChatColor.GRAY + String.format("%-16s %-12s %-20s %-10s",
                "Owner", "Type", "Location", "Mode"));
        sender.sendMessage(ChatColor.GRAY + "------------------------------------------------");

        // Shop entries
        for (int i = startIndex; i < endIndex; i++) {
            Map.Entry<Block, BarterSign> entry = filteredShops.get(i);
            Block block = entry.getKey();
            BarterSign sign = entry.getValue();

            String ownerName = Bukkit.getOfflinePlayer(sign.getOwner()).getName();
            if (ownerName == null) ownerName = "Unknown";
            if (ownerName.length() > 15) ownerName = ownerName.substring(0, 12) + "...";

            String location = String.format("%d,%d,%d",
                    block.getX(), block.getY(), block.getZ());

            String row = String.format("%-16s %-12s %-20s %-10s",
                    ownerName,
                    sign.getType(),
                    location,
                    sign.getMode());

            sender.sendMessage(ChatColor.WHITE + row);
        }

        // Footer
        sender.sendMessage(ChatColor.GRAY + "------------------------------------------------");
        sender.sendMessage(ChatColor.GREEN + "Total: " + ChatColor.WHITE + filteredShops.size() +
                ChatColor.GRAY + " | Page " + page + "/" + totalPages);

        if (totalPages > 1) {
            String navHint = filterOwner != null
                    ? "/shop list " + Bukkit.getOfflinePlayer(filterOwner).getName() + " <page>"
                    : "/shop list <page>";
            sender.sendMessage(ChatColor.GRAY + "Use " + navHint + " to navigate pages");
        }

        return true;
    }

    @Override
    public String getDescription() {
        return "List all shops or shops by player";
    }

    @Override
    public String getUsage() {
        return "/shop list [player] [page]";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.isOp();
    }

    @Override
    public String getPermission() {
        return "bartershops.command.list";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();

            // Suggest online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }

            // Suggest page number
            if ("1".startsWith(partial)) {
                completions.add("1");
            }
        } else if (args.length == 2) {
            // Page number
            completions.add("1");
            completions.add("2");
        }

        return completions;
    }

    @Override
    public boolean requiresPlayer() {
        return false; // Console-friendly
    }
}
