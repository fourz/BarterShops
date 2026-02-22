package org.fourz.BarterShops.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.data.IConnectionProvider;
import org.fourz.BarterShops.data.ShopsTestDataGenerator;
import org.fourz.rvnkcore.testing.TestDataGenerator.DataCategory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Debug subcommand for seeding test data into the BarterShops database.
 *
 * <p>Usage:
 * <ul>
 *   <li>/shop admin seed minimal|standard|stress - Seed test data</li>
 *   <li>/shop admin seed cleanup - Remove all test data</li>
 *   <li>/shop admin seed cleanup [player-uuid] - Remove data for specific player</li>
 *   <li>/shop admin seed status - Show seeding status</li>
 * </ul>
 * </p>
 */
public class SeedSubCommand implements SubCommand {

    private static final List<String> ACTIONS = Arrays.asList("minimal", "standard", "stress", "cleanup", "status");

    private final BarterShops plugin;
    private ShopsTestDataGenerator generator;
    private boolean seeding = false;

    public SeedSubCommand(BarterShops plugin) {
        this.plugin = plugin;
    }

    public String getName() {
        return "seed";
    }

    @Override
    public String getDescription() {
        return "Seed test data into database";
    }

    @Override
    public String getUsage() {
        return "/shop admin seed <minimal|standard|stress|cleanup|status>";
    }

    @Override
    public String getPermission() {
        return "bartershops.admin.seed";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission());
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        String action = args[0].toLowerCase();

        // Initialize generator if needed
        IConnectionProvider connProvider = plugin.getConnectionProvider();
        if (connProvider == null) {
            sender.sendMessage(ChatColor.RED + "Database is not available. Cannot perform seed operations.");
            return true;
        }

        if (generator == null) {
            generator = new ShopsTestDataGenerator(connProvider);
        }

        switch (action) {
            case "minimal":
            case "standard":
            case "stress":
                return executeSeed(sender, DataCategory.valueOf(action.toUpperCase()));
            case "cleanup":
                if (args.length > 1) {
                    return executeCleanupPlayer(sender, args[1]);
                }
                return executeCleanup(sender);
            case "status":
                return executeStatus(sender);
            default:
                sender.sendMessage(ChatColor.RED + "Unknown action: " + action);
                showUsage(sender);
                return true;
        }
    }

    private void showUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Shop Seed Commands ===");
        sender.sendMessage(ChatColor.GRAY + "/shop admin seed minimal" + ChatColor.DARK_GRAY + " - Seed 10 base records");
        sender.sendMessage(ChatColor.GRAY + "/shop admin seed standard" + ChatColor.DARK_GRAY + " - Seed 100 base records");
        sender.sendMessage(ChatColor.GRAY + "/shop admin seed stress" + ChatColor.DARK_GRAY + " - Seed 1000 base records");
        sender.sendMessage(ChatColor.GRAY + "/shop admin seed cleanup" + ChatColor.DARK_GRAY + " - Remove all test data");
        sender.sendMessage(ChatColor.GRAY + "/shop admin seed cleanup <uuid>" + ChatColor.DARK_GRAY + " - Remove player's test data");
        sender.sendMessage(ChatColor.GRAY + "/shop admin seed status" + ChatColor.DARK_GRAY + " - Show current status");
    }

    private boolean executeSeed(CommandSender sender, DataCategory category) {
        if (seeding) {
            sender.sendMessage(ChatColor.RED + "A seed operation is already in progress.");
            return true;
        }

        seeding = true;
        sender.sendMessage(ChatColor.GOLD + "Seeding " + category.name() + " test data...");

        generator.seed(category).thenAccept(count -> {
            seeding = false;
            if (count > 0) {
                sender.sendMessage(ChatColor.GREEN + "Seed complete: " + count + " total records created");
            } else {
                sender.sendMessage(ChatColor.RED + "Seed failed. Check console for details.");
            }
        }).exceptionally(ex -> {
            seeding = false;
            sender.sendMessage(ChatColor.RED + "Seed failed: " + ex.getMessage());
            plugin.getLogger().severe("Seed operation failed: " + ex.getMessage());
            return null;
        });

        return true;
    }

    private boolean executeCleanup(CommandSender sender) {
        if (seeding) {
            sender.sendMessage(ChatColor.RED + "A seed operation is in progress. Wait for it to complete.");
            return true;
        }

        seeding = true;
        sender.sendMessage(ChatColor.GOLD + "Cleaning up all test data...");

        generator.cleanup().thenAccept(success -> {
            seeding = false;
            if (success) {
                sender.sendMessage(ChatColor.GREEN + "Cleanup complete");
            } else {
                sender.sendMessage(ChatColor.RED + "Cleanup failed. Check console for details.");
            }
        }).exceptionally(ex -> {
            seeding = false;
            sender.sendMessage(ChatColor.RED + "Cleanup failed: " + ex.getMessage());
            plugin.getLogger().severe("Cleanup operation failed: " + ex.getMessage());
            return null;
        });

        return true;
    }

    private boolean executeCleanupPlayer(CommandSender sender, String uuidStr) {
        UUID playerUuid;
        try {
            playerUuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Invalid UUID format: " + uuidStr);
            return true;
        }

        if (seeding) {
            sender.sendMessage(ChatColor.RED + "A seed operation is in progress. Wait for it to complete.");
            return true;
        }

        seeding = true;
        sender.sendMessage(ChatColor.GOLD + "Cleaning up data for player: " + uuidStr.substring(0, 8) + "...");

        generator.cleanupByPlayer(playerUuid).thenAccept(count -> {
            seeding = false;
            sender.sendMessage(ChatColor.GREEN + "Cleaned up " + count + " records for player");
        }).exceptionally(ex -> {
            seeding = false;
            sender.sendMessage(ChatColor.RED + "Cleanup failed: " + ex.getMessage());
            plugin.getLogger().severe("Player cleanup operation failed: " + ex.getMessage());
            return null;
        });

        return true;
    }

    private boolean executeStatus(CommandSender sender) {
        IConnectionProvider connProvider = plugin.getConnectionProvider();

        sender.sendMessage(ChatColor.GOLD + "=== Shop Seed Status ===");
        sender.sendMessage(ChatColor.GRAY + "Database Available: " + (connProvider != null ? ChatColor.GREEN + "Yes (" + connProvider.getDatabaseType() + ")" : ChatColor.RED + "No"));
        sender.sendMessage(ChatColor.GRAY + "Generator Initialized: " + (generator != null ? ChatColor.GREEN + "Yes" : ChatColor.GRAY + "No"));
        sender.sendMessage(ChatColor.GRAY + "Seeding In Progress: " + (seeding ? ChatColor.YELLOW + "Yes" : ChatColor.GRAY + "No"));

        return true;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String action : ACTIONS) {
                if (action.startsWith(partial)) {
                    completions.add(action);
                }
            }
        }

        return completions;
    }
}
