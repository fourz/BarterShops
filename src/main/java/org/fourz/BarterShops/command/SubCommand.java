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
    /**
     * Worked examples for this subcommand, served by {@code /shop help <verb>}.
     *
     * <p>Return concrete, runnable lines with real-looking arguments — not a restatement of
     * {@link #getUsage()}, which the help prints above them. A line beginning with two spaces
     * renders as an indented note under the example above it.</p>
     *
     * <p>Examples ship in the jar rather than in {@code docs/plugins/commands/shop.md} so they are
     * fetched per verb and cannot drift from the build. The doc page keeps what no command can
     * print: the sign UI model, the trade flow and the backend detail. (#1981)</p>
     *
     * <p>Default is empty; {@code /shop help} marks which verbs carry examples.</p>
     *
     * @return example lines, or an empty list
     */
    default List<String> getExamples() {
        return List.of();
    }

    default boolean requiresPlayer() {
        return false;
    }
}
