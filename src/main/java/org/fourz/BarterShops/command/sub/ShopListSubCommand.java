package org.fourz.BarterShops.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.data.dto.ShopDataDTO;

import java.util.ArrayList;
import java.util.List;
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
        if (plugin.getShopRepository() == null) {
            sender.sendMessage(ChatColor.RED + "Shop database not available.");
            return true;
        }

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

        // Query database directly - source of truth for all shops
        List<ShopDataDTO> allShops;
        try {
            if (filterOwner != null) {
                allShops = plugin.getShopRepository().findByOwner(filterOwner).join();
            } else {
                allShops = plugin.getShopRepository().findAllActive().join();
            }
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to query shops from database.");
            return true;
        }

        if (allShops.isEmpty()) {
            if (filterOwner != null) {
                sender.sendMessage(ChatColor.YELLOW + "No shops found for that player.");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "No shops found!");
            }
            return true;
        }

        // Pagination
        int totalPages = (int) Math.ceil(allShops.size() / (double) ITEMS_PER_PAGE);
        page = Math.max(1, Math.min(page, totalPages));
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allShops.size());

        // Header
        String header = filterOwner != null
                ? "=== Shops by " + plugin.getPlayerLookup().getPlayerName(filterOwner) + " ==="
                : "=== All Barter Shops ===";
        sender.sendMessage(ChatColor.GREEN + header);
        sender.sendMessage(ChatColor.GRAY + String.format("%-5s %-16s %-12s %-20s",
                "ID", "Owner", "Type", "Location"));
        sender.sendMessage(ChatColor.GRAY + "------------------------------------------------");

        // Shop entries
        for (int i = startIndex; i < endIndex; i++) {
            ShopDataDTO shop = allShops.get(i);

            String ownerName = plugin.getPlayerLookup().getPlayerName(shop.ownerUuid());
            if (ownerName.length() > 15) ownerName = ownerName.substring(0, 12) + "...";

            String locationStr = shop.locationWorld() != null
                    ? String.format("%d,%d,%d",
                        (int) shop.locationX(), (int) shop.locationY(), (int) shop.locationZ())
                    : "N/A";

            String shopName = shop.shopName() != null ? shop.shopName() : "";
            String typeStr = shop.shopType() != null ? shop.shopType().name() : "BARTER";

            String row = String.format("%-5d %-16s %-12s %-20s",
                    shop.shopId(),
                    ownerName,
                    typeStr,
                    locationStr);

            sender.sendMessage(ChatColor.WHITE + row);
            if (!shopName.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "      " + shopName);
            }
        }

        // Footer
        sender.sendMessage(ChatColor.GRAY + "------------------------------------------------");
        sender.sendMessage(ChatColor.GREEN + "Total: " + ChatColor.WHITE + allShops.size() +
                ChatColor.GRAY + " | Page " + page + "/" + totalPages);

        if (totalPages > 1) {
            String navHint = filterOwner != null
                    ? "/shop list " + plugin.getPlayerLookup().getPlayerName(filterOwner) + " <page>"
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
