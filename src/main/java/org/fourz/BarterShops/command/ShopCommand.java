package org.fourz.BarterShops.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.sub.*;
import org.fourz.BarterShops.economy.EconomyManager;
import org.fourz.BarterShops.economy.ShopFeeCalculator;
import org.fourz.BarterShops.service.IShopGroupService;
import org.fourz.BarterShops.service.IRatingService;
import org.fourz.BarterShops.service.IStatsService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main command handler for the /shop command.
 * Dispatches to appropriate subcommands based on arguments.
 * Follows RVNKCore CommandManager pattern.
 */
public class ShopCommand implements CommandExecutor, TabCompleter {

    private final BarterShops plugin;
    private final LogManager logger;
    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public ShopCommand(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ShopCommand");
        registerSubCommands();
    }

    /**
     * Registers all subcommands.
     */
    private void registerSubCommands() {
        logger.debug("Registering shop subcommands...");

        // Core shop commands
        registerSubCommand("create", new ShopCreateSubCommand(plugin));
        registerSubCommand("list", new ShopListSubCommand(plugin));
        registerSubCommand("info", new ShopInfoSubCommand(plugin));
        registerSubCommand("history", new ShopHistorySubCommand(plugin));
        registerSubCommand("remove", new ShopRemoveSubCommand(plugin));
        registerSubCommand("nearby", new ShopNearbySubCommand(plugin));

        // Template commands
        registerSubCommand("template", new ShopTemplateSubCommand(plugin));

        // Notification commands
        registerSubCommand("notifications", new ShopNotificationsSubCommand(plugin));

        // Rating commands (feat-06)
        IRatingService ratingService = plugin.getRatingService();
        if (ratingService != null) {
            registerSubCommand("rate", new ShopRateSubCommand(plugin, ratingService));
            registerSubCommand("reviews", new ShopReviewsSubCommand(plugin, ratingService));
            logger.debug("Registered rating commands");
        } else {
            logger.debug("RatingService not available - rating commands not registered");
        }

        // Statistics commands (feat-07)
        IStatsService statsService = plugin.getStatsService();
        if (statsService != null) {
            registerSubCommand("stats", new ShopStatsSubCommand(plugin, statsService));
            logger.debug("Registered statistics commands");
        } else {
            logger.debug("StatsService not available - statistics commands not registered");
        }

        // Economy commands (feat-02) - conditional on EconomyManager availability
        EconomyManager economyManager = plugin.getEconomyManager();
        if (economyManager != null) {
            ShopFeeCalculator calculator = plugin.getFeeCalculator();
            registerSubCommand("fee", new ShopFeeSubCommand(plugin, economyManager));
            registerSubCommand("tax", new ShopTaxSubCommand(plugin, economyManager, calculator));
            logger.debug("Registered economy commands (fee, tax)");
        } else {
            logger.debug("EconomyManager not available - economy commands not registered");
        }

        // Region protection commands
        registerSubCommand("region", new ShopRegionSubCommand(plugin, plugin.getProtectionManager()));

        // Admin commands
        registerSubCommand("admin", new ShopAdminSubCommand(plugin));
        registerSubCommand("admingui", new ShopAdminGUISubCommand(plugin));
        registerSubCommand("inspect", new ShopInspectSubCommand(plugin));
        registerSubCommand("clear", new ShopClearSubCommand(plugin));
        registerSubCommand("reload", new ShopReloadSubCommand(plugin));
        registerSubCommand("trade", new ShopTradeSubCommand(plugin));

        // Debug commands (feat-01)
        registerSubCommand("debug", new ShopDebugSubCommand(plugin));

        // Shop group commands (conditional on ShopGroupService availability)
        IShopGroupService groupService = plugin.getShopGroupService();
        if (groupService != null) {
            registerSubCommand("group", new ShopGroupSubCommand(plugin, groupService));
            registerSubCommand("share", new ShopShareSubCommand(plugin, groupService));
            registerSubCommand("unshare", new ShopUnshareSubCommand(plugin, groupService));
            registerSubCommand("shared", new ShopSharedSubCommand(plugin, groupService));
            logger.debug("Registered shop group commands (group, share, unshare, shared)");
        } else {
            logger.debug("ShopGroupService not available - group commands not registered");
        }

        logger.debug("Registered " + subCommands.size() + " subcommands");
    }

    /**
     * Registers a subcommand.
     *
     * @param name The name of the subcommand
     * @param subCommand The subcommand implementation
     */
    private void registerSubCommand(String name, SubCommand subCommand) {
        subCommands.put(name.toLowerCase(), subCommand);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommandName = args[0].toLowerCase();

        // Handle help as special case (not a registered subcommand). With a verb argument it
        // serves that verb's usage and worked examples (#1981).
        if (subCommandName.equals("help") || subCommandName.equals("?")) {
            if (args.length >= 2) {
                showVerbHelp(sender, args[1].toLowerCase());
            } else {
                showHelp(sender);
            }
            return true;
        }

        SubCommand subCommand = subCommands.get(subCommandName);

        if (subCommand == null) {
            sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCommandName);
            sender.sendMessage(ChatColor.GRAY + "Use /shop help to see available commands.");
            return true;
        }

        // Check permission
        if (!subCommand.hasPermission(sender)) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        // Check if command requires player
        if (subCommand.requiresPlayer() && !(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        // Remove the subcommand name from args
        String[] subCommandArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subCommandArgs, 0, args.length - 1);

        logger.debug("Executing subcommand: " + subCommandName + " for " + sender.getName());
        return subCommand.execute(sender, subCommandArgs);
    }

    /**
     * Shows help information to the sender.
     *
     * @param sender Command sender to show help to
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== BarterShops Commands =====");

        List<String> names = new ArrayList<>(subCommands.keySet());
        java.util.Collections.sort(names);

        boolean anyExamples = false;
        for (String name : names) {
            SubCommand sub = subCommands.get(name);
            if (sub == null || !sub.hasPermission(sender)) {
                continue;
            }
            boolean hasExamples = !sub.getExamples().isEmpty();
            anyExamples |= hasExamples;
            sender.sendMessage(ChatColor.YELLOW + sub.getUsage()
                    + (hasExamples ? ChatColor.AQUA + " *" : "")
                    + ChatColor.WHITE + " - " + sub.getDescription());
        }

        if (anyExamples) {
            sender.sendMessage(ChatColor.AQUA + "*" + ChatColor.GRAY + " has worked examples - "
                    + ChatColor.WHITE + "/shop help <subcommand>");
        }
        sender.sendMessage(ChatColor.GRAY + "Use /shop help <command> for usage and examples.");
    }

    /**
     * {@code /shop help <verb>} — one subcommand's usage and worked examples (#1981).
     *
     * <p>The examples ship inside the jar, so they are fetched per verb and cannot drift from the
     * build the way a second copy in {@code docs/plugins/commands/shop.md} does.</p>
     */
    private void showVerbHelp(CommandSender sender, String verb) {
        SubCommand sub = subCommands.get(verb);
        if (sub == null) {
            sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + verb);
            sender.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/shop help"
                    + ChatColor.GRAY + " for the list.");
            return;
        }
        if (!sub.hasPermission(sender)) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "===== /shop " + verb + " =====");
        sender.sendMessage(ChatColor.WHITE + sub.getDescription());
        sender.sendMessage(ChatColor.YELLOW + "Usage: " + ChatColor.WHITE + sub.getUsage());
        if (sub.requiresPlayer()) {
            sender.sendMessage(ChatColor.GRAY + "Players only - not available from console.");
        }
        if (sub.getPermission() != null && !sub.getPermission().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Permission: " + sub.getPermission());
        }

        List<String> examples = sub.getExamples();
        if (examples.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY
                    + "No further examples - the usage line above is the whole grammar.");
            return;
        }
        sender.sendMessage(ChatColor.YELLOW + "Examples:");
        for (String example : examples) {
            if (example.startsWith("  ")) {
                sender.sendMessage(ChatColor.DARK_GRAY + "     " + example.trim());
            } else {
                sender.sendMessage(ChatColor.WHITE + "  " + example);
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Complete subcommand names
            String partial = args[0].toLowerCase();
            for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
                if (entry.getValue().hasPermission(sender) &&
                        entry.getKey().startsWith(partial)) {
                    completions.add(entry.getKey());
                }
            }
        } else if (args.length > 1) {
            // Pass to subcommand for completion
            String subCommandName = args[0].toLowerCase();
            SubCommand subCommand = subCommands.get(subCommandName);

            if (subCommand != null && subCommand.hasPermission(sender)) {
                String[] subCommandArgs = new String[args.length - 1];
                System.arraycopy(args, 1, subCommandArgs, 0, args.length - 1);

                List<String> subCommandCompletions = subCommand.getTabCompletions(sender, subCommandArgs);
                if (subCommandCompletions != null) {
                    completions.addAll(subCommandCompletions);
                }
            }
        }

        return completions;
    }

    /**
     * Gets a subcommand by name.
     *
     * @param name The subcommand name
     * @return The subcommand, or null if not found
     */
    public SubCommand getSubCommand(String name) {
        return subCommands.get(name.toLowerCase());
    }

    /**
     * Gets all registered subcommands.
     *
     * @return Map of subcommand names to implementations
     */
    public Map<String, SubCommand> getSubCommands() {
        return new HashMap<>(subCommands);
    }
}
