package org.fourz.BarterShops.command.sub;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.preferences.PlayerShopPreferences;
import org.fourz.BarterShops.preferences.ShopPreferenceManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Subcommand for managing shop inspection preferences.
 * Allows players to control shift+click info display behavior.
 *
 * Usage:
 * /shop inspect - Show current preferences
 * /shop inspect toggle - Enable/disable shift+click info display
 * /shop inspect format <chat|actionbar> - Set display format
 * /shop inspect own - Toggle "own shops only" restriction
 *
 * Console-friendly: No (requires player)
 */
public class ShopInspectSubCommand implements SubCommand {
    private final BarterShops plugin;

    public ShopInspectSubCommand(BarterShops plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command");
            return true;
        }

        ShopPreferenceManager prefs = plugin.getPreferenceManager();
        if (prefs == null) {
            player.sendMessage(ChatColor.RED + "Preference system not initialized");
            return true;
        }

        // No arguments - show current preferences
        if (args.length < 1) {
            showCurrentPreferences(player);
            return true;
        }

        String setting = args[0].toLowerCase();

        switch (setting) {
            case "toggle":
                handleToggle(player, prefs);
                break;
            case "format":
                handleFormat(player, prefs, args);
                break;
            case "own":
                handleOwnShopsOnly(player, prefs);
                break;
            default:
                player.sendMessage(ChatColor.RED + "Unknown setting: " + setting);
                showUsage(player);
        }

        return true;
    }

    /**
     * Shows current preferences to the player.
     */
    private void showCurrentPreferences(Player player) {
        PlayerShopPreferences current = plugin.getPreferenceManager().getPreferences(player.getUniqueId());

        player.sendMessage(ChatColor.GOLD + "=== Shop Inspection Settings ===");
        player.sendMessage(ChatColor.YELLOW + "Info Display: " + ChatColor.WHITE +
                (current.infoDisplayEnabled() ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
        player.sendMessage(ChatColor.YELLOW + "Format: " + ChatColor.WHITE +
                (current.chatFormat() ? "Chat" : "Action Bar"));
        player.sendMessage(ChatColor.YELLOW + "Own Shops Only: " + ChatColor.WHITE +
                (current.ownShopsOnly() ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
        player.sendMessage(ChatColor.GOLD + "================================");
        player.sendMessage(ChatColor.GRAY + "Use /shop inspect <toggle|format|own> to change settings");
    }

    /**
     * Toggles info display on/off.
     */
    private void handleToggle(Player player, ShopPreferenceManager prefs) {
        boolean current = prefs.isInfoDisplayEnabled(player.getUniqueId());
        boolean newValue = !current;
        prefs.setPreference(player.getUniqueId(), "infoDisplay", newValue);

        player.sendMessage(ChatColor.GREEN + "✓ Info display: " +
                (newValue ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
        player.sendMessage(ChatColor.GRAY + "Shift+Right-click signs to view info");
    }

    /**
     * Changes display format (chat or actionbar).
     */
    private void handleFormat(Player player, ShopPreferenceManager prefs, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /shop inspect format <chat|actionbar>");
            return;
        }

        String format = args[1].toLowerCase();
        if (!format.equals("chat") && !format.equals("actionbar")) {
            player.sendMessage(ChatColor.RED + "Format must be: chat or actionbar");
            return;
        }

        boolean isChat = format.equals("chat");
        prefs.setPreference(player.getUniqueId(), "chatFormat", isChat);

        player.sendMessage(ChatColor.GREEN + "✓ Format set to: " +
                (isChat ? ChatColor.AQUA + "Chat" : ChatColor.LIGHT_PURPLE + "Action Bar"));
    }

    /**
     * Toggles "own shops only" restriction.
     */
    private void handleOwnShopsOnly(Player player, ShopPreferenceManager prefs) {
        boolean current = prefs.showOwnShopsOnly(player.getUniqueId());
        boolean newValue = !current;
        prefs.setPreference(player.getUniqueId(), "ownShopsOnly", newValue);

        player.sendMessage(ChatColor.GREEN + "✓ Show own shops only: " +
                (newValue ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
        if (!newValue) {
            player.sendMessage(ChatColor.GRAY + "You can now see info for any shop (with permission)");
        } else {
            player.sendMessage(ChatColor.GRAY + "You can only see info for shops you own");
        }
    }

    /**
     * Shows usage information.
     */
    private void showUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "Shop Inspection Commands:");
        player.sendMessage(ChatColor.YELLOW + "/shop inspect" + ChatColor.WHITE + " - Show current settings");
        player.sendMessage(ChatColor.YELLOW + "/shop inspect toggle" + ChatColor.WHITE + " - Enable/disable info display");
        player.sendMessage(ChatColor.YELLOW + "/shop inspect format <chat|actionbar>" + ChatColor.WHITE + " - Set display format");
        player.sendMessage(ChatColor.YELLOW + "/shop inspect own" + ChatColor.WHITE + " - Toggle own shops only restriction");
    }

    @Override
    public String getDescription() {
        return "Manage shop inspection preferences (shift+click info display)";
    }

    @Override
    public String getUsage() {
        return "/shop inspect [toggle|format|own]";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        // Anyone can manage their own preferences (no special permission needed)
        // Just needs to be a player (checked in execute)
        return true;
    }

    @Override
    public String getPermission() {
        return "bartershops.admin";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("toggle", "format", "own");
        }
        if (args.length == 2 && "format".equalsIgnoreCase(args[0])) {
            return Arrays.asList("chat", "actionbar");
        }
        return Collections.emptyList();
    }

    @Override
    public boolean requiresPlayer() {
        return true;
    }
}
