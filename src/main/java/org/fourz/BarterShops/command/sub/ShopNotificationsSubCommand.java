package org.fourz.BarterShops.command.sub;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.notification.NotificationPreferencesDTO;
import org.fourz.BarterShops.notification.NotificationType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Subcommand for managing notification preferences.
 * Usage: /shop notifications [toggle|type|quiet|status]
 * Player-only command (requires notification preferences).
 */
public class ShopNotificationsSubCommand implements SubCommand {

    private final BarterShops plugin;

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

        switch (action) {
            case "toggle" -> {
                plugin.getNotificationManager().toggleMasterEnabled(player.getUniqueId());
                NotificationPreferencesDTO prefs = plugin.getNotificationManager().getPreferences(player.getUniqueId());
                String status = prefs.masterEnabled() ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled";
                player.sendMessage(ChatColor.GOLD + "Notifications " + status);
                return true;
            }
            case "type" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /shop notifications type <type>");
                    player.sendMessage(ChatColor.GRAY + "Available types: " +
                            Arrays.stream(NotificationType.values())
                                    .map(t -> t.name().toLowerCase())
                                    .collect(Collectors.joining(", ")));
                    return true;
                }

                String typeName = args[1].toUpperCase();
                try {
                    NotificationType type = NotificationType.valueOf(typeName);
                    plugin.getNotificationManager().toggleNotificationType(player.getUniqueId(), type);

                    NotificationPreferencesDTO prefs = plugin.getNotificationManager().getPreferences(player.getUniqueId());
                    boolean enabled = prefs.enabledTypes().getOrDefault(type, false);
                    String status = enabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled";

                    player.sendMessage(ChatColor.GOLD + type.getDisplayName() + " notifications " + status);
                } catch (IllegalArgumentException e) {
                    player.sendMessage(ChatColor.RED + "Invalid notification type: " + args[1]);
                    player.sendMessage(ChatColor.GRAY + "Available types: " +
                            Arrays.stream(NotificationType.values())
                                    .map(t -> t.name().toLowerCase())
                                    .collect(Collectors.joining(", ")));
                }
                return true;
            }
            case "quiet" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /shop notifications quiet <start> <end>");
                    player.sendMessage(ChatColor.GRAY + "Hours: 0-23, or -1 to disable");
                    player.sendMessage(ChatColor.GRAY + "Example: /shop notifications quiet 22 8 (10 PM to 8 AM)");
                    return true;
                }

                try {
                    int start = Integer.parseInt(args[1]);
                    int end = Integer.parseInt(args[2]);
                    plugin.getNotificationManager().setQuietHours(player.getUniqueId(), start, end);

                    if (start == -1 || end == -1) {
                        player.sendMessage(ChatColor.GOLD + "Quiet hours disabled");
                    } else {
                        player.sendMessage(ChatColor.GOLD + "Quiet hours set: " +
                                ChatColor.WHITE + start + ":00 - " + end + ":00");
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Invalid hour format. Use numbers 0-23 or -1.");
                } catch (IllegalArgumentException e) {
                    player.sendMessage(ChatColor.RED + e.getMessage());
                }
                return true;
            }
            case "status" -> {
                showNotificationStatus(player);
                return true;
            }
            default -> {
                player.sendMessage(ChatColor.RED + "Unknown action: " + action);
                player.sendMessage(ChatColor.GRAY + "Available: toggle, type, quiet, status");
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

        // Master toggle
        String masterStatus = prefs.masterEnabled() ?
                ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled";
        player.sendMessage(ChatColor.YELLOW + "Master: " + masterStatus);

        // Quiet hours
        if (prefs.quietHoursStart() != -1 && prefs.quietHoursEnd() != -1) {
            player.sendMessage(ChatColor.YELLOW + "Quiet Hours: " + ChatColor.WHITE +
                    prefs.quietHoursStart() + ":00 - " + prefs.quietHoursEnd() + ":00");
        } else {
            player.sendMessage(ChatColor.YELLOW + "Quiet Hours: " + ChatColor.GRAY + "Disabled");
        }

        // Notification types
        player.sendMessage(ChatColor.GOLD + "Notification Types:");
        for (NotificationType type : NotificationType.values()) {
            boolean enabled = prefs.enabledTypes().getOrDefault(type, false);
            String status = enabled ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗";
            player.sendMessage(ChatColor.GRAY + "  " + status + " " + ChatColor.WHITE + type.getDisplayName());
        }

        player.sendMessage(ChatColor.GRAY + "Use /shop notifications <action> to manage");
        player.sendMessage(ChatColor.GRAY + "Actions: toggle, type <name>, quiet <start> <end>, status");
    }

    @Override
    public String getDescription() {
        return "Manage notification preferences";
    }

    @Override
    public String getUsage() {
        return "/shop notifications [toggle|type|quiet|status]";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.isOp();
    }

    @Override
    public String getPermission() {
        return "bartershops.notifications";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> actions = Arrays.asList("toggle", "type", "quiet", "status");
            for (String action : actions) {
                if (action.startsWith(partial)) {
                    completions.add(action);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("type")) {
            String partial = args[1].toLowerCase();
            for (NotificationType type : NotificationType.values()) {
                String typeName = type.name().toLowerCase();
                if (typeName.startsWith(partial)) {
                    completions.add(typeName);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("quiet")) {
            // Suggest common quiet hour starts
            completions.addAll(Arrays.asList("-1", "0", "22", "23"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("quiet")) {
            // Suggest common quiet hour ends
            completions.addAll(Arrays.asList("-1", "6", "7", "8"));
        }

        return completions;
    }

    @Override
    public boolean requiresPlayer() {
        return true; // Requires player for UUID-based preferences
    }
}
