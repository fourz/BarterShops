package org.fourz.BarterShops.command.sub;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.notification.NotificationPreferencesDTO;
import org.fourz.BarterShops.notification.NotificationType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Subcommand for managing notification preferences.
 * Usage: /shop notifications [on|off|toggle <type>]
 * Player-only command (requires notification preferences).
 */
public class ShopNotificationsSubCommand implements SubCommand {

    private final BarterShops plugin;

    /** Notification types players can toggle. Trade events are system-managed. */
    private static final Set<NotificationType> PLAYER_CONFIGURABLE_TYPES = EnumSet.of(
            NotificationType.SHOP_STOCK_LOW,
            NotificationType.SHOP_SALE,
            NotificationType.REVIEW_RECEIVED,
            NotificationType.PRICE_CHANGE,
            NotificationType.SYSTEM
    );

    public ShopNotificationsSubCommand(BarterShops plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        UUID targetUUID;
        int argOffset;

        if (!(sender instanceof Player)) {
            // Console: /shop notifications <player> [action] [type]
            if (args.length == 0) {
                sender.sendMessage(ChatColor.RED + "Usage: /shop notifications <player> [on|off|toggle <type>|list]");
                return true;
            }
            Player target = plugin.getServer().getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found or not online: " + args[0]);
                return true;
            }
            targetUUID = target.getUniqueId();
            argOffset = 1;
        } else {
            targetUUID = ((Player) sender).getUniqueId();
            argOffset = 0;
        }

        if (args.length <= argOffset) {
            showNotificationStatus(sender, targetUUID);
            return true;
        }

        String action = args[argOffset].toLowerCase();

        if (action.equals("list")) {
            showNotificationStatus(sender, targetUUID);
            return true;
        }

        switch (action) {
            case "on" -> {
                NotificationPreferencesDTO current = plugin.getNotificationManager().getPreferences(targetUUID);
                if (current.masterEnabled()) {
                    sender.sendMessage(ChatColor.YELLOW + "Shop notifications are already enabled.");
                    return true;
                }
                plugin.getNotificationManager().toggleMasterEnabled(targetUUID);
                sender.sendMessage(ChatColor.GREEN + "Shop notifications enabled.");
                return true;
            }
            case "off" -> {
                NotificationPreferencesDTO current = plugin.getNotificationManager().getPreferences(targetUUID);
                if (!current.masterEnabled()) {
                    sender.sendMessage(ChatColor.YELLOW + "Shop notifications are already disabled.");
                    return true;
                }
                plugin.getNotificationManager().toggleMasterEnabled(targetUUID);
                sender.sendMessage(ChatColor.RED + "Shop notifications disabled.");
                return true;
            }
            case "toggle" -> {
                int typeArgIdx = argOffset + 1;
                if (args.length <= typeArgIdx) {
                    sender.sendMessage(ChatColor.RED + "Usage: /shop notifications toggle <type>");
                    sender.sendMessage(ChatColor.GRAY + "Types: " +
                            PLAYER_CONFIGURABLE_TYPES.stream()
                                    .map(t -> t.name().toLowerCase())
                                    .sorted()
                                    .collect(Collectors.joining(", ")));
                    return true;
                }

                String typeName = args[typeArgIdx].toUpperCase();
                try {
                    NotificationType type = NotificationType.valueOf(typeName);
                    if (!PLAYER_CONFIGURABLE_TYPES.contains(type)) {
                        sender.sendMessage(ChatColor.RED + "That notification type cannot be toggled.");
                        return true;
                    }

                    NotificationPreferencesDTO currentPrefs = plugin.getNotificationManager().getPreferences(targetUUID);
                    boolean newState = !currentPrefs.enabledTypes().getOrDefault(type, true);
                    plugin.getNotificationManager().toggleNotificationType(targetUUID, type);
                    String status = newState ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled";
                    sender.sendMessage(ChatColor.GOLD + type.getDisplayName() + " notifications " + status);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(ChatColor.RED + "Unknown notification type: " + args[typeArgIdx]);
                    sender.sendMessage(ChatColor.GRAY + "Types: " +
                            PLAYER_CONFIGURABLE_TYPES.stream()
                                    .map(t -> t.name().toLowerCase())
                                    .sorted()
                                    .collect(Collectors.joining(", ")));
                }
                return true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown action: " + action);
                sender.sendMessage(ChatColor.GRAY + "Available: on, off, toggle");
                return true;
            }
        }
    }

    private void showNotificationStatus(CommandSender sender, UUID targetUUID) {
        NotificationPreferencesDTO prefs = plugin.getNotificationManager().getPreferences(targetUUID);

        sender.sendMessage(ChatColor.GOLD + "=== Notification Settings ===");

        String masterStatus = prefs.masterEnabled()
                ? ChatColor.GREEN + "enabled" + ChatColor.GRAY + " (/shop notifications off to disable)"
                : ChatColor.RED + "disabled" + ChatColor.GRAY + " (/shop notifications on to enable)";
        sender.sendMessage(ChatColor.YELLOW + "All notifications: " + masterStatus);

        if (prefs.masterEnabled()) {
            sender.sendMessage(ChatColor.GOLD + "Types:");
            for (NotificationType type : PLAYER_CONFIGURABLE_TYPES) {
                boolean enabled = prefs.enabledTypes().getOrDefault(type, true);
                String status = enabled ? ChatColor.GREEN + "on" : ChatColor.RED + "off";
                sender.sendMessage(ChatColor.GRAY + "  " + type.getDisplayName() + ": " + status);
            }
            sender.sendMessage(ChatColor.GRAY + "Use /shop notifications toggle <type> to change.");
        }
    }

    @Override
    public String getDescription() {
        return "Manage notification preferences";
    }

    @Override
    public String getUsage() {
        return "/shop notifications [on|off|toggle <type>]";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.hasPermission("bartershops.admin");
    }

    @Override
    public String getPermission() {
        return "bartershops.create";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String action : Arrays.asList("list", "on", "off", "toggle")) {
                if (action.startsWith(partial)) {
                    completions.add(action);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
            String partial = args[1].toLowerCase();
            for (NotificationType type : PLAYER_CONFIGURABLE_TYPES) {
                String typeName = type.name().toLowerCase();
                if (typeName.startsWith(partial)) {
                    completions.add(typeName);
                }
            }
        }

        return completions;
    }

    @Override
    public boolean requiresPlayer() {
        return false;
    }
}
