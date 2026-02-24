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
 * Usage: /shop notifications [toggle [type]|quiet <start> <end>]
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

        // Handle "list" as alias for showing notifications
        if (action.equals("list")) {
            showNotificationStatus(player);
            return true;
        }

        switch (action) {
            case "toggle" -> {
                // Require a notification type to toggle
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /shop notifications toggle <type>");
                    player.sendMessage(ChatColor.GRAY + "Available types: " +
                            Arrays.stream(NotificationType.values())
                                    .map(t -> t.name().toLowerCase())
                                    .collect(Collectors.joining(", ")));
                    return true;
                }

                // Toggle specific notification type
                String typeName = args[1].toUpperCase();
                try {
                    NotificationType type = NotificationType.valueOf(typeName);

                    // Get current state first, calculate new state
                    NotificationPreferencesDTO currentPrefs = plugin.getNotificationManager().getPreferences(player.getUniqueId());
                    boolean currentState = currentPrefs.enabledTypes().getOrDefault(type, false);
                    boolean newState = !currentState;

                    // Perform toggle
                    plugin.getNotificationManager().toggleNotificationType(player.getUniqueId(), type);

                    // Display new state
                    String status = newState ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled";
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
            default -> {
                player.sendMessage(ChatColor.RED + "Unknown action: " + action);
                player.sendMessage(ChatColor.GRAY + "Available: toggle");
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

        // Notification types
        player.sendMessage(ChatColor.GOLD + "Notification Types:");
        for (NotificationType type : NotificationType.values()) {
            boolean enabled = prefs.enabledTypes().getOrDefault(type, false);
            String status = enabled ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗";
            player.sendMessage(ChatColor.GRAY + "  " + status + " " + ChatColor.WHITE + type.getDisplayName());
        }

        player.sendMessage(ChatColor.GRAY + "Use /shop notifications toggle <type> to manage");
    }

    @Override
    public String getDescription() {
        return "Manage notification preferences";
    }

    @Override
    public String getUsage() {
        return "/shop notifications [list|toggle <type>]";
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
            List<String> actions = Arrays.asList("list", "toggle");
            for (String action : actions) {
                if (action.startsWith(partial)) {
                    completions.add(action);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
            // Tab completion for toggle subcommand - show available notification types
            String partial = args[1].toLowerCase();
            for (NotificationType type : NotificationType.values()) {
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
        return true; // Requires player for UUID-based preferences
    }
}
