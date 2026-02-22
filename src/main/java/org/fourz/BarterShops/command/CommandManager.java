package org.fourz.BarterShops.command;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.fourz.BarterShops.BarterShops;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages command registration and execution for the BarterShops plugin.
 * Follows RVNKCore CommandManager pattern.
 */
public class CommandManager {

    private final BarterShops plugin;
    private final LogManager logger;
    private final Map<String, CommandExecutor> commands = new HashMap<>();

    public static final String COMMAND_NAME = "shop";

    public CommandManager(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "CommandManager");
        registerCommands();
    }

    /**
     * Registers all commands for the plugin.
     */
    private void registerCommands() {
        logger.debug("Registering commands...");
        registerCommand(COMMAND_NAME, new ShopCommand(plugin));
        logger.debug("Commands registered successfully");
    }

    /**
     * Registers a command with the server.
     *
     * @param commandName The name of the command
     * @param executor The executor for the command
     */
    private void registerCommand(String commandName, CommandExecutor executor) {
        logger.debug("Registering command: " + commandName);
        PluginCommand command = plugin.getCommand(commandName);

        if (command == null) {
            logger.warning("Failed to register command: " + commandName + " (not found in plugin.yml)");
            return;
        }

        command.setExecutor(executor);

        // If the executor also implements TabCompleter, register it
        if (executor instanceof TabCompleter) {
            command.setTabCompleter((TabCompleter) executor);
            logger.debug("Tab completer registered for command: " + commandName);
        }

        commands.put(commandName, executor);
        logger.debug("Command registered: " + commandName);
    }

    /**
     * Gets a registered command executor.
     *
     * @param commandName The name of the command
     * @return The command executor, or null if not found
     */
    public CommandExecutor getCommand(String commandName) {
        return commands.get(commandName);
    }

    /**
     * Gets the ShopCommand instance.
     *
     * @return The ShopCommand, or null if not registered
     */
    public ShopCommand getShopCommand() {
        CommandExecutor executor = commands.get(COMMAND_NAME);
        return executor instanceof ShopCommand ? (ShopCommand) executor : null;
    }

    /**
     * Cleans up command registrations.
     */
    public void cleanup() {
        logger.debug("Cleaning up command registrations...");
        PluginCommand command = plugin.getCommand(COMMAND_NAME);
        if (command != null) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
        commands.clear();
        logger.info("CommandManager cleanup completed");
    }
}
