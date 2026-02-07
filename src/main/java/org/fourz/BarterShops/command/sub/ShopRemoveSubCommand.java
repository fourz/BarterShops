package org.fourz.BarterShops.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.sign.BarterSign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Subcommand for removing a shop.
 * Usage: /shop remove <id>
 * Console-friendly: Yes (with admin permission)
 */
public class ShopRemoveSubCommand implements SubCommand {

    private final BarterShops plugin;

    public ShopRemoveSubCommand(BarterShops plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: " + getUsage());
            return true;
        }

        String shopId = args[0];

        // Find shop by ID
        Optional<Map.Entry<Location, BarterSign>> shopEntry = findShopById(shopId);

        if (shopEntry.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Shop not found: " + shopId);
            sender.sendMessage(ChatColor.GRAY + "Use /shop list to see available shops.");
            return true;
        }

        Location location = shopEntry.get().getKey();
        BarterSign sign = shopEntry.get().getValue();

        // Check ownership (unless admin)
        if (sender instanceof Player player) {
            if (!sign.getOwner().equals(player.getUniqueId()) &&
                    !sender.hasPermission("bartershops.admin")) {
                sender.sendMessage(ChatColor.RED + "You don't own this shop.");
                return true;
            }
        }

        // Confirm removal (if no --confirm flag)
        boolean confirmed = args.length > 1 && args[1].equalsIgnoreCase("--confirm");
        if (!confirmed) {
            sender.sendMessage(ChatColor.YELLOW + "Are you sure you want to remove this shop?");
            sender.sendMessage(ChatColor.GRAY + "Owner: " +
                    Bukkit.getOfflinePlayer(sign.getOwner()).getName());
            sender.sendMessage(ChatColor.GRAY + "Location: " +
                    location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
            sender.sendMessage(ChatColor.RED + "Run: /shop remove " + shopId + " --confirm");
            return true;
        }

        // Remove the shop
        plugin.getSignManager().getBarterSigns().remove(location);

        sender.sendMessage(ChatColor.GREEN + "Shop removed successfully.");
        sender.sendMessage(ChatColor.GRAY + "Location: " +
                location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());

        return true;
    }

    private Optional<Map.Entry<Location, BarterSign>> findShopById(String id) {
        Map<Location, BarterSign> shops = plugin.getSignManager().getBarterSigns();

        // Try to match by location coordinates (x,y,z format)
        if (id.contains(",")) {
            String[] parts = id.split(",");
            if (parts.length >= 3) {
                try {
                    int x = Integer.parseInt(parts[0].trim());
                    int y = Integer.parseInt(parts[1].trim());
                    int z = Integer.parseInt(parts[2].trim());

                    return shops.entrySet().stream()
                            .filter(entry -> {
                                Location loc = entry.getKey();
                                return loc.getBlockX() == x && loc.getBlockY() == y && loc.getBlockZ() == z;
                            })
                            .findFirst();
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // Try to match by index number
        try {
            int index = Integer.parseInt(id) - 1;
            List<Map.Entry<Location, BarterSign>> shopList = new ArrayList<>(shops.entrySet());
            if (index >= 0 && index < shopList.size()) {
                return Optional.of(shopList.get(index));
            }
        } catch (NumberFormatException ignored) {
        }

        return Optional.empty();
    }

    @Override
    public String getDescription() {
        return "Remove a shop you own";
    }

    @Override
    public String getUsage() {
        return "/shop remove <id> [--confirm]";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.isOp();
    }

    @Override
    public String getPermission() {
        return "bartershops.command.remove";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();

            // Suggest shop numbers owned by the sender
            Map<Location, BarterSign> shops = plugin.getSignManager().getBarterSigns();
            int index = 1;
            for (Map.Entry<Location, BarterSign> entry : shops.entrySet()) {
                // Only suggest shops the player owns (or all if admin)
                if (sender instanceof Player player) {
                    if (!entry.getValue().getOwner().equals(player.getUniqueId()) &&
                            !sender.hasPermission("bartershops.admin")) {
                        index++;
                        continue;
                    }
                }

                String num = String.valueOf(index);
                if (num.startsWith(partial)) {
                    completions.add(num);
                }
                index++;

                if (completions.size() >= 10) break;
            }
        } else if (args.length == 2) {
            if ("--confirm".startsWith(args[1].toLowerCase())) {
                completions.add("--confirm");
            }
        }

        return completions;
    }

    @Override
    public boolean requiresPlayer() {
        return false; // Console can remove with admin permission
    }
}
