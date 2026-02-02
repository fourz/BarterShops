package org.fourz.BarterShops.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.data.dto.StatsDataDTO;
import org.fourz.BarterShops.service.IStatsService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Subcommand for viewing shop and player statistics.
 * Usage: /shop stats [player|server]
 *
 * <p>Displays comprehensive analytics including:
 * - Player: shops owned, trades completed, average rating, most traded items
 * - Server: total shops, total trades, top shops, player engagement
 * </p>
 */
public class ShopStatsSubCommand implements SubCommand {

    private final BarterShops plugin;
    private final IStatsService statsService;

    public ShopStatsSubCommand(BarterShops plugin, IStatsService statsService) {
        this.plugin = plugin;
        this.statsService = statsService;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (statsService == null) {
            sender.sendMessage(ChatColor.RED + "Statistics service is not available.");
            return true;
        }

        // Determine target: self, player, or server
        if (args.length == 0) {
            // Show stats for self (requires player)
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Usage: /shop stats <player|server>");
                sender.sendMessage(ChatColor.GRAY + "Console must specify a player or 'server'.");
                return true;
            }

            showPlayerStats(sender, player.getUniqueId(), player.getName());
            return true;
        }

        String target = args[0].toLowerCase();

        if ("server".equals(target)) {
            // Show server-wide statistics
            showServerStats(sender);
            return true;
        }

        // Show stats for specified player
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(target);
        if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + target);
            return true;
        }

        String playerName = targetPlayer.getName() != null ? targetPlayer.getName() : target;
        showPlayerStats(sender, targetPlayer.getUniqueId(), playerName);
        return true;
    }

    /**
     * Shows player statistics.
     */
    private void showPlayerStats(CommandSender sender, UUID playerUuid, String playerName) {
        sender.sendMessage(ChatColor.GOLD + "Fetching statistics for " + ChatColor.YELLOW + playerName + ChatColor.GOLD + "...");

        statsService.getPlayerStats(playerUuid).thenAccept(stats -> {
            // Header
            sender.sendMessage(ChatColor.GOLD + "===== " + ChatColor.YELLOW + playerName + "'s Statistics" + ChatColor.GOLD + " =====");
            sender.sendMessage("");

            // Shop ownership
            sender.sendMessage(ChatColor.AQUA + "Shops Owned: " + ChatColor.WHITE + stats.totalShopsOwned());

            // Trading activity
            sender.sendMessage(ChatColor.AQUA + "Trades Completed: " + ChatColor.WHITE + stats.totalTradesCompleted());
            sender.sendMessage(ChatColor.AQUA + "Items Traded: " + ChatColor.WHITE + stats.totalItemsTraded());

            // Rating
            if (stats.totalRatings() > 0) {
                sender.sendMessage(ChatColor.AQUA + "Average Rating: " + ChatColor.WHITE + stats.getFormattedRating());
            } else {
                sender.sendMessage(ChatColor.AQUA + "Average Rating: " + ChatColor.GRAY + "No ratings yet");
            }

            // Most traded items
            if (!stats.mostTradedItems().isEmpty()) {
                sender.sendMessage("");
                sender.sendMessage(ChatColor.GOLD + "Most Traded Items:");
                List<String> topItems = stats.getTopTradedItems();
                for (int i = 0; i < topItems.size(); i++) {
                    sender.sendMessage(ChatColor.GRAY + "  " + (i + 1) + ". " + ChatColor.WHITE + topItems.get(i));
                }
            }

            sender.sendMessage("");
            sender.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/shop stats server" + ChatColor.GRAY + " for server-wide statistics.");

        }).exceptionally(ex -> {
            sender.sendMessage(ChatColor.RED + "Failed to fetch statistics: " + ex.getMessage());
            plugin.getLogger().warning("Error fetching player stats: " + ex.getMessage());
            return null;
        });
    }

    /**
     * Shows server-wide statistics.
     */
    private void showServerStats(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Fetching server statistics...");

        statsService.getServerStats().thenAccept(stats -> {
            StatsDataDTO.ServerStats serverStats = stats.serverStats();

            if (serverStats == null) {
                sender.sendMessage(ChatColor.RED + "Server statistics not available.");
                return;
            }

            // Header
            sender.sendMessage(ChatColor.GOLD + "===== " + ChatColor.YELLOW + "Server Statistics" + ChatColor.GOLD + " =====");
            sender.sendMessage("");

            // Shop statistics
            sender.sendMessage(ChatColor.AQUA + "Total Shops: " + ChatColor.WHITE + serverStats.totalShops());
            sender.sendMessage(ChatColor.AQUA + "Active Shops: " + ChatColor.WHITE + serverStats.activeShops());
            sender.sendMessage(ChatColor.AQUA + "Shop Owners: " + ChatColor.WHITE + serverStats.totalPlayers());
            sender.sendMessage("");

            // Trading statistics
            sender.sendMessage(ChatColor.AQUA + "Total Trades: " + ChatColor.WHITE + serverStats.totalTrades());
            sender.sendMessage(ChatColor.AQUA + "Items Traded: " + ChatColor.WHITE + serverStats.totalItemsTraded());
            sender.sendMessage(ChatColor.AQUA + "Avg Trades/Shop: " + ChatColor.WHITE + String.format("%.1f", serverStats.averageTradesPerShop()));
            sender.sendMessage("");

            // Top shops by trades
            if (!serverStats.topShops().isEmpty()) {
                sender.sendMessage(ChatColor.GOLD + "Top Shops (by trades):");
                List<StatsDataDTO.TopShop> topShops = serverStats.topShops().stream()
                    .limit(5)
                    .toList();

                for (int i = 0; i < topShops.size(); i++) {
                    StatsDataDTO.TopShop shop = topShops.get(i);
                    sender.sendMessage(ChatColor.GRAY + "  " + (i + 1) + ". " +
                        ChatColor.YELLOW + shop.shopName() +
                        ChatColor.WHITE + " (" + shop.tradeCount() + " trades) - " +
                        ChatColor.GRAY + shop.ownerName());
                }
                sender.sendMessage("");
            }

            // Most traded items
            if (!serverStats.mostTradedItems().isEmpty()) {
                sender.sendMessage(ChatColor.GOLD + "Most Traded Items:");
                serverStats.mostTradedItems().entrySet().stream()
                    .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(5)
                    .forEach(entry -> {
                        sender.sendMessage(ChatColor.GRAY + "  • " + ChatColor.WHITE +
                            entry.getKey() + ChatColor.GRAY + " (" + entry.getValue() + ")");
                    });
                sender.sendMessage("");
            }

            sender.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/shop stats <player>" + ChatColor.GRAY + " for player statistics.");

        }).exceptionally(ex -> {
            sender.sendMessage(ChatColor.RED + "Failed to fetch server statistics: " + ex.getMessage());
            plugin.getLogger().warning("Error fetching server stats: " + ex.getMessage());
            return null;
        });
    }

    @Override
    public String getDescription() {
        return "View shop and player statistics";
    }

    @Override
    public String getUsage() {
        return "/shop stats [player|server]";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        // Anyone can view their own stats
        // Admin permission required for server stats or other players
        if (sender.hasPermission("bartershops.stats.admin") || sender.isOp()) {
            return true;
        }
        return sender.hasPermission(getPermission());
    }

    @Override
    public String getPermission() {
        return "bartershops.stats";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();

            // Suggest "server" option
            if ("server".startsWith(partial)) {
                completions.add("server");
            }

            // Suggest online player names if admin
            if (sender.hasPermission("bartershops.stats.admin") || sender.isOp()) {
                Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .forEach(completions::add);
            }
        }

        return completions;
    }

    @Override
    public boolean requiresPlayer() {
        // Console can use this command with arguments
        return false;
    }
}
