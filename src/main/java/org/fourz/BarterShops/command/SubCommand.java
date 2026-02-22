package org.fourz.BarterShops.command;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Interface for subcommands of the main shop command.
 * Follows RVNKCore pattern for subcommand registration.
 */
public interface SubCommand {

    /**
     * Executes the subcommand.
     *
     * @param sender The command sender (player or console)
     * @param args Arguments for the subcommand
     * @return true if the command was executed successfully
     */
    boolean execute(CommandSender sender, String[] args);

    /**
     * Gets a description of the subcommand.
     *
     * @return The description shown in help
     */
    String getDescription();

    /**
     * Gets the usage string for this subcommand.
     *
     * @return The usage string (e.g., "/shop create <name>")
     */
    String getUsage();

    /**
     * Checks if the sender has permission to use this subcommand.
     *
     * @param sender The command sender
     * @return true if the sender has permission
     */
    boolean hasPermission(CommandSender sender);

    /**
     * Gets the permission node for this subcommand.
     *
     * @return The permission string
     */
    String getPermission();

    /**
     * Gets tab completions for the current arguments.
     *
     * @param sender The command sender
     * @param args Current arguments
     * @return List of tab completions
     */
    List<String> getTabCompletions(CommandSender sender, String[] args);

    /**
     * Checks if this command requires a player sender.
     * Default is false (console support).
     *
     * @return true if the command can only be used by players
     */
    default boolean requiresPlayer() {
        return false;
    }
}
