package org.fourz.BarterShops.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.protection.IProtectionProvider;
import org.fourz.BarterShops.protection.ProtectionManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Subcommand for managing shop region protection.
 * Usage: /shop region <info|protect|unprotect> [args]
 * Console-friendly: Yes (with location arguments)
 */
public class ShopRegionSubCommand implements SubCommand {

    private final BarterShops plugin;
    private final ProtectionManager protectionManager;

    public ShopRegionSubCommand(BarterShops plugin, ProtectionManager protectionManager) {
        this.plugin = plugin;
        this.protectionManager = protectionManager;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: " + getUsage());
            sender.sendMessage(ChatColor.GRAY + "   Subcommands: info, status");
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "info":
                return handleInfo(sender, args);
            case "status":
                return handleStatus(sender, args);
            default:
                sender.sendMessage(ChatColor.RED + "✖ Unknown region action: " + action);
                sender.sendMessage(ChatColor.GRAY + "   Valid actions: info, status");
                return true;
        }
    }

    /**
     * Handles /shop region info [x] [y] [z] [world]
     * Shows protection info at player location or specified coordinates.
     */
    private boolean handleInfo(CommandSender sender, String[] args) {
        Location location;

        // Parse location
        if (args.length >= 4) {
            // Console-friendly: /shop region info <x> <y> <z> [world]
            try {
                int x = Integer.parseInt(args[1]);
                int y = Integer.parseInt(args[2]);
                int z = Integer.parseInt(args[3]);
                String worldName = args.length >= 5 ? args[4] : "world";

                org.bukkit.World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    sender.sendMessage(ChatColor.RED + "✖ World not found: " + worldName);
                    return true;
                }

                location = new Location(world, x, y, z);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "✖ Invalid coordinates");
                sender.sendMessage(ChatColor.GRAY + "   Usage: /shop region info <x> <y> <z> [world]");
                return true;
            }
        } else if (sender instanceof Player player) {
            // Player location
            location = player.getLocation();
        } else {
            sender.sendMessage(ChatColor.RED + "✖ Console must specify coordinates");
            sender.sendMessage(ChatColor.GRAY + "   Usage: /shop region info <x> <y> <z> [world]");
            return true;
        }

        // Get protection info
        sender.sendMessage(ChatColor.YELLOW + "⚙ Checking protection at location...");

        protectionManager.getProtectionInfo(location).thenAccept(info -> {
            if (info == null) {
                sender.sendMessage(ChatColor.GRAY + "No shop protection found at this location.");
                sender.sendMessage(ChatColor.GRAY + "Location: " + formatLocation(location));
            } else {
                displayProtectionInfo(sender, info, location);
            }
        }).exceptionally(ex -> {
            sender.sendMessage(ChatColor.RED + "✖ Error checking protection: " + ex.getMessage());
            return null;
        });

        return true;
    }

    /**
     * Handles /shop region status
     * Shows overall protection system status.
     */
    private boolean handleStatus(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.GOLD + "===== Shop Protection Status =====");

        boolean enabled = protectionManager.isEnabled();
        IProtectionProvider provider = protectionManager.getProvider();

        sender.sendMessage(ChatColor.YELLOW + "Enabled: " +
                (enabled ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));

        sender.sendMessage(ChatColor.YELLOW + "Provider: " + ChatColor.WHITE +
                provider.getProviderName());

        sender.sendMessage(ChatColor.YELLOW + "Available: " +
                (provider.isAvailable() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));

        sender.sendMessage(ChatColor.YELLOW + "Auto-protect radius: " + ChatColor.WHITE +
                protectionManager.getAutoProtectRadius() + " blocks");

        sender.sendMessage(ChatColor.YELLOW + "Max shops per player: " + ChatColor.WHITE +
                protectionManager.getMaxShopsPerPlayer());

        // Player-specific info
        if (sender instanceof Player player) {
            sender.sendMessage(ChatColor.GRAY + "Checking your shop count...");

            provider.getProtectedShopCount(player.getUniqueId()).thenAccept(count -> {
                sender.sendMessage(ChatColor.YELLOW + "Your protected shops: " +
                        ChatColor.WHITE + count + "/" + protectionManager.getMaxShopsPerPlayer());

                boolean canCreate = count < protectionManager.getMaxShopsPerPlayer() ||
                        player.hasPermission("bartershops.admin.unlimited") ||
                        player.isOp();

                sender.sendMessage(ChatColor.YELLOW + "Can create more: " +
                        (canCreate ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
            }).exceptionally(ex -> {
                sender.sendMessage(ChatColor.RED + "✖ Error checking shop count: " + ex.getMessage());
                return null;
            });
        }

        return true;
    }

    /**
     * Displays protection information.
     */
    private void displayProtectionInfo(CommandSender sender, IProtectionProvider.ProtectionInfo info, Location location) {
        sender.sendMessage(ChatColor.GOLD + "===== Shop Protection Info =====");
        sender.sendMessage(ChatColor.YELLOW + "Shop ID: " + ChatColor.WHITE + info.shopId());
        sender.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE +
                (info.owner() != null ? Bukkit.getOfflinePlayer(info.owner()).getName() : "Unknown"));
        sender.sendMessage(ChatColor.YELLOW + "Provider: " + ChatColor.WHITE + info.providerType());
        sender.sendMessage(ChatColor.YELLOW + "Radius: " + ChatColor.WHITE + info.radius() + " blocks");
        sender.sendMessage(ChatColor.YELLOW + "Center: " + ChatColor.WHITE + formatLocation(info.center()));
        sender.sendMessage(ChatColor.YELLOW + "Query location: " + ChatColor.GRAY + formatLocation(location));
    }

    /**
     * Formats a location for display.
     */
    private String formatLocation(Location loc) {
        return String.format("%s: %d, %d, %d",
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ());
    }

    @Override
    public String getDescription() {
        return "Manage shop region protection";
    }

    @Override
    public String getUsage() {
        return "/shop region <info|status>";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.isOp();
    }

    @Override
    public String getPermission() {
        return "bartershops.command.region";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> actions = Arrays.asList("info", "status");

            for (String action : actions) {
                if (action.startsWith(partial)) {
                    completions.add(action);
                }
            }
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("info")) {
            // Suggest world names for info command
            if (args.length == 5) {
                String partial = args[4].toLowerCase();
                for (org.bukkit.World world : Bukkit.getWorlds()) {
                    if (world.getName().toLowerCase().startsWith(partial)) {
                        completions.add(world.getName());
                    }
                }
            }
        }

        return completions;
    }

    @Override
    public boolean requiresPlayer() {
        return false; // Console-friendly with coordinate arguments
    }
}
