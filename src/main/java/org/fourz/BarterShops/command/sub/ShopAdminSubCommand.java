package org.fourz.BarterShops.command.sub;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin subcommand with nested admin operations.
 * Usage: /shop admin <reload|debug|stats>
 * Console-friendly: Yes
 */
public class ShopAdminSubCommand implements SubCommand {

    private final BarterShops plugin;
    private final Map<String, AdminAction> adminActions = new HashMap<>();

    public ShopAdminSubCommand(BarterShops plugin) {
        this.plugin = plugin;
        registerAdminActions();
    }

    private void registerAdminActions() {
        adminActions.put("reload", this::executeReload);
        adminActions.put("debug", this::executeDebug);
        adminActions.put("stats", this::executeStats);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            showAdminHelp(sender);
            return true;
        }

        String action = args[0].toLowerCase();
        AdminAction adminAction = adminActions.get(action);

        if (adminAction == null) {
            sender.sendMessage(ChatColor.RED + "Unknown admin action: " + action);
            showAdminHelp(sender);
            return true;
        }

        // Shift args for the action
        String[] actionArgs = new String[args.length - 1];
        System.arraycopy(args, 1, actionArgs, 0, args.length - 1);

        return adminAction.execute(sender, actionArgs);
    }

    private void showAdminHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== Shop Admin Commands =====");
        sender.sendMessage(ChatColor.YELLOW + "/shop admin reload" +
                ChatColor.WHITE + " - Reload configuration");
        sender.sendMessage(ChatColor.YELLOW + "/shop admin debug [on|off]" +
                ChatColor.WHITE + " - Toggle debug mode");
        sender.sendMessage(ChatColor.YELLOW + "/shop admin stats" +
                ChatColor.WHITE + " - Show plugin statistics");
    }

    private boolean executeReload(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.YELLOW + "Reloading BarterShops configuration...");

        try {
            plugin.reloadConfig();
            plugin.getConfigManager().reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "Configuration reloaded successfully!");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to reload configuration: " + e.getMessage());
            return false;
        }

        return true;
    }

    private boolean executeDebug(CommandSender sender, String[] args) {
        boolean currentDebug = plugin.getConfigManager().getBoolean("debug", false);

        if (args.length > 0) {
            boolean newDebug = args[0].equalsIgnoreCase("on") ||
                    args[0].equalsIgnoreCase("true") ||
                    args[0].equals("1");
            plugin.getConfig().set("debug", newDebug);
            plugin.saveConfig();
            sender.sendMessage(ChatColor.GREEN + "Debug mode " +
                    (newDebug ? "enabled" : "disabled"));
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Debug mode is currently " +
                    (currentDebug ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled"));
            sender.sendMessage(ChatColor.GRAY + "Use /shop admin debug <on|off> to toggle");
        }

        return true;
    }

    private boolean executeStats(CommandSender sender, String[] args) {
        int shopCount = plugin.getSignManager().getBarterSigns().size();
        int sessionCount = plugin.getShopManager().getActiveSessions().size();

        sender.sendMessage(ChatColor.GOLD + "===== BarterShops Statistics =====");
        sender.sendMessage(ChatColor.YELLOW + "Total Shops: " + ChatColor.WHITE + shopCount);
        sender.sendMessage(ChatColor.YELLOW + "Active Sessions: " + ChatColor.WHITE + sessionCount);
        sender.sendMessage(ChatColor.YELLOW + "Plugin Version: " + ChatColor.WHITE +
                plugin.getDescription().getVersion());

        // Memory usage
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        sender.sendMessage(ChatColor.YELLOW + "Memory: " + ChatColor.WHITE +
                usedMemory + "MB / " + maxMemory + "MB");

        return true;
    }

    @Override
    public String getDescription() {
        return "Admin commands (reload, debug, stats)";
    }

    @Override
    public String getUsage() {
        return "/shop admin <reload|debug|stats>";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.isOp();
    }

    @Override
    public String getPermission() {
        return "bartershops.admin";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String action : adminActions.keySet()) {
                if (action.startsWith(partial)) {
                    completions.add(action);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            String partial = args[1].toLowerCase();
            for (String option : List.of("on", "off")) {
                if (option.startsWith(partial)) {
                    completions.add(option);
                }
            }
        }

        return completions;
    }

    @Override
    public boolean requiresPlayer() {
        return false; // Console-friendly
    }

    @FunctionalInterface
    private interface AdminAction {
        boolean execute(CommandSender sender, String[] args);
    }
}
