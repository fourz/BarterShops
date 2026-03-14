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
        Player player = (Player) sender;

        if (args.length == 0) {
            showNotificationStatus(player);
            return true;
        }

        String action = args[0].toLowerCase();

        // Handle "list" as alias for showing notifications
        if (action.equals("list")) {
            showNotificationStatus(player);
            return true;
        }

        switch (action) {
            case "on" -> {
                NotificationPreferencesDTO current = plugin.getNotificationManager().getPreferences(player.getUniqueId());
                if (current.masterEnabled()) {
                    player.sendMessage(ChatColor.YELLOW + "Shop notifications are already enabled.");
                    return true;
                }
                plugin.getNotificationManager().toggleMasterEnabled(player.getUniqueId());
                player.sendMessage(ChatColor.GREEN + "Shop notifications enabled.");
                return true;
            }
            case "off" -> {
                NotificationPreferencesDTO current = plugin.getNotificationManager().getPreferences(player.getUniqueId());
                if (!current.masterEnabled()) {
                    player.sendMessage(ChatColor.YELLOW + "Shop notifications are already disabled.");
                    return true;
                }
                plugin.getNotificationManager().toggleMasterEnabled(player.getUniqueId());
                player.sendMessage(ChatColor.RED + "Shop notifications disabled.");
                return true;
            }
            case "toggle" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /shop notifications toggle <type>");
                    player.sendMessage(ChatColor.GRAY + "Types: " +
                            PLAYER_CONFIGURABLE_TYPES.stream()
                                    .map(t -> t.name().toLowerCase())
                                    .sorted()
                                    .collect(Collectors.joining(", ")));
                    return true;
                }

                String typeName = args[1].toUpperCase();
                try {
                    NotificationType type = NotificationType.valueOf(typeName);
                    if (!PLAYER_CONFIGURABLE_TYPES.contains(type)) {
                        player.sendMessage(ChatColor.RED + "That notification type cannot be toggled.");
                        return true;
                    }

                    NotificationPreferencesDTO currentPrefs = plugin.getNotificationManager().getPreferences(player.getUniqueId());
                    boolean newState = !currentPrefs.enabledTypes().getOrDefault(type, true);
                    plugin.getNotificationManager().toggleNotificationType(player.getUniqueId(), type);
                    String status = newState ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled";
                    player.sendMessage(ChatColor.GOLD + type.getDisplayName() + " notifications " + status);
                } catch (IllegalArgumentException e) {
                    player.sendMessage(ChatColor.RED + "Unknown notification type: " + args[1]);
                    player.sendMessage(ChatColor.GRAY + "Types: " +
                            PLAYER_CONFIGURABLE_TYPES.stream()
                                    .map(t -> t.name().toLowerCase())
                                    .sorted()
                                    .collect(Collectors.joining(", ")));
                }
                return true;
            }
            default -> {
                player.sendMessage(ChatColor.RED + "Unknown action: " + action);
                player.sendMessage(ChatColor.GRAY + "Available: on, off, toggle");
                return true;
            }
        }
    }

    /**
     * Shows current notification settings to the player.
     */
    private void showNotificationStatus(Player player) {
        NotificationPreferencesDTO prefs = plugin.getNotificationManager().getPreferences(player.getUniqueId());

        player.sendMessage(ChatColor.GOLD + "=== Notification Settings ===");

        String masterStatus = prefs.masterEnabled()
                ? ChatColor.GREEN + "enabled" + ChatColor.GRAY + " (/shop notifications off to disable)"
                : ChatColor.RED + "disabled" + ChatColor.GRAY + " (/shop notifications on to enable)";
        player.sendMessage(ChatColor.YELLOW + "All notifications: " + masterStatus);

        if (prefs.masterEnabled()) {
            player.sendMessage(ChatColor.GOLD + "Types:");
            for (NotificationType type : PLAYER_CONFIGURABLE_TYPES) {
                boolean enabled = prefs.enabledTypes().getOrDefault(type, true);
                String status = enabled ? ChatColor.GREEN + "on" : ChatColor.RED + "off";
                player.sendMessage(ChatColor.GRAY + "  " + type.getDisplayName() + ": " + status);
            }
            player.sendMessage(ChatColor.GRAY + "Use /shop notifications toggle <type> to change.");
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
        return sender.hasPermission(getPermission()) || sender.isOp();
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
        return true;
    }
}
