package org.fourz.BarterShops.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.fourz.BarterShops.Main;
import java.util.HashMap;
import java.util.Map;

public class CommandManager implements CommandExecutor {
    private final Main plugin;
    private final Map<String, BaseCommand> commands;
    public static final String COMMAND_NAME = "shop";

    public CommandManager(Main plugin) {
        this.plugin = plugin;
        this.commands = new HashMap<>();
        registerCommands();
        plugin.getCommand(COMMAND_NAME).setExecutor(this);
    }

    private void registerCommands() {
        commands.put("list", new ListCommand(plugin));
        commands.put("nearby", new NearbyCommand(plugin));
        commands.put("reload", new ReloadCommand(plugin));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase(COMMAND_NAME)) {
            if (args.length == 0) {
                showHelp(sender);
                return true;
            }

            BaseCommand cmd = commands.get(args[0].toLowerCase());
            if (cmd == null) {
                sender.sendMessage(plugin.getConfigManager().getMessage("generic.error") + " Unknown command!");
                return true;
            }

            if (!sender.hasPermission(cmd.getPermission())) {
                sender.sendMessage(plugin.getConfigManager().getMessage("generic.error") + " No permission!");
                return true;
            }

            String[] newArgs = new String[args.length - 1];
            System.arraycopy(args, 1, newArgs, 0, args.length - 1);
            cmd.execute(sender, newArgs);
            return true;
        }
        return false;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(plugin.getConfigManager().getMessage("generic.help"));
        for (Map.Entry<String, BaseCommand> entry : commands.entrySet()) {
            if (sender.hasPermission(entry.getValue().getPermission())) {
                sender.sendMessage("§6/shop " + entry.getKey() + "§f - " + entry.getValue().getDescription());
            }
        }
    }
    
    public void cleanup() {
        plugin.getCommand(COMMAND_NAME).setExecutor(null);
        commands.clear();
    }
}
