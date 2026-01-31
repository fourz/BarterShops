package org.fourz.BarterShops.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.sub.*;
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
        registerSubCommand("remove", new ShopRemoveSubCommand(plugin));
        registerSubCommand("nearby", new ShopNearbySubCommand(plugin));

        // Admin commands
        registerSubCommand("admin", new ShopAdminSubCommand(plugin));

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
        SubCommand subCommand = subCommands.get(subCommandName);

        if (subCommand == null) {
            sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCommandName);
            showHelp(sender);
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

        for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
            if (entry.getValue().hasPermission(sender)) {
                sender.sendMessage(ChatColor.YELLOW + entry.getValue().getUsage() +
                        ChatColor.WHITE + " - " + entry.getValue().getDescription());
            }
        }

        sender.sendMessage(ChatColor.GRAY + "Use /shop <command> for more info.");
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
