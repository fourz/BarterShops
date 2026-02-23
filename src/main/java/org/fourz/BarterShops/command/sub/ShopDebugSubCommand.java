package org.fourz.BarterShops.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SeedSubCommand;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.rvnkcore.data.FallbackTracker;
import org.fourz.BarterShops.data.IConnectionProvider;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.BarterShops.sign.SignDisplay;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Debug command for BarterShops.
 * Provides diagnostic information and access to seed commands.
 *
 * Usage:
 *   /shop debug - Show debug info
 *   /shop debug loglevel [level] - View/change log level
 *   /shop debug seed <action> - Seed test data
 *   /shop debug diagnostics - System diagnostics
 */
public class ShopDebugSubCommand implements SubCommand {

    private static final List<String> SUB_COMMANDS = Arrays.asList("loglevel", "seed", "diagnostics", "changeowner");
    private static final List<String> LOG_LEVELS = Arrays.asList("DEBUG", "INFO", "WARN", "OFF");

    private final BarterShops plugin;
    private final LogManager logger;
    private final SeedSubCommand seedCommand;

    public ShopDebugSubCommand(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ShopDebugSubCommand");
        this.seedCommand = new SeedSubCommand(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        // Check for subcommands
        if (args.length > 0) {
            String subCommand = args[0].toLowerCase();
            String[] subArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];

            switch (subCommand) {
                case "loglevel":
                    return handleLogLevel(sender, subArgs);
                case "seed":
                    return seedCommand.execute(sender, subArgs);
                case "diagnostics":
                    return handleDiagnostics(sender);
                case "changeowner":
                    return handleChangeOwner(sender, subArgs);
                default:
                    sender.sendMessage(ChatColor.RED + "Unknown debug subcommand: " + subCommand);
                    showUsage(sender);
                    return true;
            }
        }

        // Default: show debug info
        showDebugInfo(sender);
        return true;
    }

    /**
     * Shows general debug information.
     */
    private void showDebugInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "=== BarterShops Debug Info ===");

        // Database info
        IConnectionProvider connProvider = plugin.getConnectionProvider();
        if (connProvider != null) {
            sender.sendMessage(ChatColor.GOLD + "Database Type: " +
                ChatColor.WHITE + connProvider.getDatabaseType());
        } else {
            sender.sendMessage(ChatColor.GOLD + "Database Type: " +
                ChatColor.RED + "Not available");
        }

        // Fallback status
        FallbackTracker fallback = plugin.getFallbackTracker();
        if (fallback != null) {
            sender.sendMessage(ChatColor.GOLD + "Fallback Mode: " +
                (fallback.isInFallbackMode() ? ChatColor.YELLOW + "Active" : ChatColor.GREEN + "No"));
            if (fallback.isInFallbackMode()) {
                sender.sendMessage(ChatColor.GOLD + "Fallback Reason: " +
                    ChatColor.GRAY + fallback.getFallbackReason());
            }
        }

        // RVNKCore integration
        sender.sendMessage(ChatColor.GOLD + "RVNKCore Integration: " +
            (plugin.isRVNKCoreAvailable() ? ChatColor.GREEN + "Enabled" : ChatColor.GRAY + "Standalone"));

        // Uptime
        long uptimeMs = System.currentTimeMillis() - plugin.getStartTime();
        long uptimeSec = uptimeMs / 1000;
        long uptimeMin = uptimeSec / 60;
        long uptimeHrs = uptimeMin / 60;
        sender.sendMessage(ChatColor.GOLD + "Uptime: " +
            ChatColor.WHITE + String.format("%dh %dm %ds", uptimeHrs, uptimeMin % 60, uptimeSec % 60));

        // Log level
        String currentLevel = plugin.getConfig().getString("general.logLevel", "INFO");
        sender.sendMessage(ChatColor.GOLD + "Log Level: " + ChatColor.WHITE + currentLevel);

        // Available subcommands
        sender.sendMessage(ChatColor.GRAY + "Subcommands: /shop debug loglevel|seed|diagnostics|changeowner");
    }

    /**
     * Handle the loglevel subcommand.
     * Usage: /shop debug loglevel [DEBUG|INFO|WARN|OFF]
     */
    private boolean handleLogLevel(CommandSender sender, String[] args) {
        if (args.length == 0) {
            // Show current log level from config
            String currentLevel = plugin.getConfig().getString("general.logLevel", "INFO");
            sender.sendMessage(ChatColor.GOLD + "Current log level: " +
                ChatColor.WHITE + currentLevel);
            sender.sendMessage(ChatColor.GRAY + "Usage: /shop debug loglevel <DEBUG|INFO|WARN|OFF>");
            return true;
        }

        String levelStr = args[0].toUpperCase();
        Level level = LogManager.parseLevel(levelStr);

        // Set log level for all BarterShops loggers
        LogManager.setPluginLogLevel(plugin, level);

        // Update config for persistence
        plugin.getConfig().set("general.logLevel", levelStr);
        plugin.saveConfig();

        sender.sendMessage(ChatColor.GREEN + "Log level set to: " + ChatColor.WHITE + levelStr);
        sender.sendMessage(ChatColor.GRAY + "(Saved to config.yml)");

        return true;
    }

    /**
     * Handle the diagnostics subcommand.
     * Shows detailed system diagnostics.
     */
    private boolean handleDiagnostics(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "=== BarterShops Diagnostics ===");

        // Memory usage
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        sender.sendMessage(ChatColor.GOLD + "Memory: " +
            ChatColor.WHITE + usedMemory + "MB / " + maxMemory + "MB");

        // Managers status
        sender.sendMessage(ChatColor.GOLD + "--- Manager Status ---");
        sender.sendMessage(ChatColor.GRAY + "ShopManager: " +
            (plugin.getShopManager() != null ? ChatColor.GREEN + "Active" : ChatColor.RED + "Null"));
        sender.sendMessage(ChatColor.GRAY + "SignManager: " +
            (plugin.getSignManager() != null ? ChatColor.GREEN + "Active" : ChatColor.RED + "Null"));
        sender.sendMessage(ChatColor.GRAY + "ContainerManager: " +
            (plugin.getContainerManager() != null ? ChatColor.GREEN + "Active" : ChatColor.RED + "Null"));
        sender.sendMessage(ChatColor.GRAY + "TradeEngine: " +
            (plugin.getTradeEngine() != null ? ChatColor.GREEN + "Active" : ChatColor.RED + "Null"));
        sender.sendMessage(ChatColor.GRAY + "TemplateManager: " +
            (plugin.getTemplateManager() != null ? ChatColor.GREEN + "Active" : ChatColor.RED + "Null"));
        sender.sendMessage(ChatColor.GRAY + "ProtectionManager: " +
            (plugin.getProtectionManager() != null ? ChatColor.GREEN + "Active" : ChatColor.RED + "Null"));

        // Services status
        sender.sendMessage(ChatColor.GOLD + "--- Service Status ---");
        sender.sendMessage(ChatColor.GRAY + "RatingService: " +
            (plugin.getRatingService() != null ? ChatColor.GREEN + "Registered" : ChatColor.GRAY + "Not available"));
        sender.sendMessage(ChatColor.GRAY + "StatsService: " +
            (plugin.getStatsService() != null ? ChatColor.GREEN + "Registered" : ChatColor.GRAY + "Not available"));

        // Database layer
        sender.sendMessage(ChatColor.GOLD + "--- Database Layer ---");
        sender.sendMessage(ChatColor.GRAY + "ConnectionProvider: " +
            (plugin.getConnectionProvider() != null ? ChatColor.GREEN + "Active" : ChatColor.RED + "Null"));
        sender.sendMessage(ChatColor.GRAY + "ShopRepository: " +
            (plugin.getShopRepository() != null ? ChatColor.GREEN + "Active" : ChatColor.RED + "Null"));

        FallbackTracker fallback = plugin.getFallbackTracker();
        if (fallback != null) {
            sender.sendMessage(ChatColor.GRAY + "FallbackTracker: " + ChatColor.GREEN + "Active");
            sender.sendMessage(ChatColor.GRAY + "  Failure Count: " + ChatColor.WHITE + fallback.getFailureCount() + "/" + fallback.getMaxFailures());
        }

        return true;
    }

    /**
     * Handle the changeowner subcommand.
     * Usage: /shop debug changeowner <shopId> <playerName>
     * Delegates to IShopOwnershipService for real-time ownership transfer.
     */
    private boolean handleChangeOwner(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /shop debug changeowner <shopId> <playerName>");
            return true;
        }

        int shopId;
        try {
            shopId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid shop ID: " + args[0]);
            return true;
        }

        String playerName = args[1];

        // Resolve player name to UUID
        Player targetPlayer = Bukkit.getPlayer(playerName);
        UUID newOwnerUUID;

        if (targetPlayer != null) {
            // Player is online
            newOwnerUUID = targetPlayer.getUniqueId();
        } else {
            // Try offline player lookup
            var offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            if (offlinePlayer.getUniqueId() == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + playerName);
                return true;
            }
            newOwnerUUID = offlinePlayer.getUniqueId();
        }

        // Delegate to ownership service
        plugin.getOwnershipService().transferOwnership(shopId, newOwnerUUID, sender)
            .thenAccept(result -> {
                if (result.success()) {
                    sender.sendMessage(ChatColor.GREEN + "✓ " + result.message());
                    sender.sendMessage(ChatColor.GRAY + "  Shop: " + ChatColor.WHITE + "#" + shopId);
                    sender.sendMessage(ChatColor.GRAY + "  Old owner: " + ChatColor.WHITE + result.oldOwner());
                    sender.sendMessage(ChatColor.GRAY + "  New owner: " + ChatColor.WHITE + result.newOwner());
                    sender.sendMessage(ChatColor.GRAY + "  Sessions invalidated: " + ChatColor.YELLOW +
                        result.sessionsInvalidated());
                } else {
                    sender.sendMessage(ChatColor.RED + "✗ " + result.message());
                }
            })
            .exceptionally(ex -> {
                sender.sendMessage(ChatColor.RED + "✗ Error: " + ex.getMessage());
                logger.error("Error during ownership transfer", ex);
                return null;
            });

        sender.sendMessage(ChatColor.YELLOW + "* Changing shop owner...");
        return true;
    }

    private void showUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Shop Debug Commands ===");
        sender.sendMessage(ChatColor.GRAY + "/shop debug" + ChatColor.DARK_GRAY + " - Show debug info");
        sender.sendMessage(ChatColor.GRAY + "/shop debug loglevel [level]" + ChatColor.DARK_GRAY + " - View/change log level");
        sender.sendMessage(ChatColor.GRAY + "/shop debug seed <action>" + ChatColor.DARK_GRAY + " - Seed test data");
        sender.sendMessage(ChatColor.GRAY + "/shop debug diagnostics" + ChatColor.DARK_GRAY + " - System diagnostics");
        sender.sendMessage(ChatColor.GRAY + "/shop debug changeowner <shopId> <playerName>" + ChatColor.DARK_GRAY + " - Change shop owner");
    }

    @Override
    public String getDescription() {
        return "Debug and diagnostic commands";
    }

    @Override
    public String getUsage() {
        return "/shop debug [loglevel|seed|diagnostics]";
    }

    @Override
    public String getPermission() {
        return "bartershops.admin";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.hasPermission("bartershops.admin");
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String cmd : SUB_COMMANDS) {
                if (cmd.startsWith(partial)) {
                    completions.add(cmd);
                }
            }
        } else if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            String partial = args[1].toUpperCase();

            if (subCmd.equals("loglevel")) {
                for (String level : LOG_LEVELS) {
                    if (level.startsWith(partial)) {
                        completions.add(level);
                    }
                }
            } else if (subCmd.equals("changeowner")) {
                // Return actual shop IDs sorted newest first
                final String shopIdPartial = args[1].toLowerCase();

                // Get all shops from SignManager
                var allShops = plugin.getSignManager().getBarterSigns();

                // Collect shop IDs and sort newest first (assuming higher ID = newer)
                allShops.values().stream()
                    .filter(shop -> shop.getShopId() > 0) // Exclude -1 IDs
                    .map(shop -> String.valueOf(shop.getShopId()))
                    .sorted((a, b) -> Integer.compare(Integer.parseInt(b), Integer.parseInt(a))) // Descending
                    .filter(id -> id.startsWith(shopIdPartial))
                    .forEach(completions::add);
            } else if (subCmd.equals("seed")) {
                return seedCommand.getTabCompletions(sender, Arrays.copyOfRange(args, 1, args.length));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("changeowner")) {
            // Tab completion for player names
            String partial = args[2].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length > 2 && args[0].equalsIgnoreCase("seed")) {
            return seedCommand.getTabCompletions(sender, Arrays.copyOfRange(args, 1, args.length));
        }

        return completions;
    }

    @Override
    public boolean requiresPlayer() {
        return false; // Console-compatible
    }
}
