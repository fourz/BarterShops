package org.fourz.BarterShops.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
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
            // Cache miss — fall back to database (sign may be in unloaded chunk)
            try {
                if (plugin.getShopRepository() != null) {
                    Optional<ShopDataDTO> dbShop = plugin.getShopRepository().findById(shopId).join();
                    if (dbShop.isPresent()) {
                        ShopDataDTO dto = dbShop.get();
                        sender.sendMessage(ChatColor.RED + "Shop exists in DB but its sign chunk is not loaded.");
                        sender.sendMessage(ChatColor.YELLOW + "Shop ID: " + dto.shopId() + " | Owner: " +
                                plugin.getPlayerLookup().getPlayerName(dto.ownerUuid()));
                        sender.sendMessage(ChatColor.YELLOW + "Location: " +
                                String.format("%s: %.0f, %.0f, %.0f",
                                        dto.locationWorld(), dto.locationX(), dto.locationY(), dto.locationZ()));
                        sender.sendMessage(ChatColor.RED + "The chunk must be loaded before the shop can be removed.");
                        sender.sendMessage(ChatColor.GRAY + "Travel to the shop location to load the chunk, then re-run the command.");
                        return true;
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("ShopRemoveSubCommand: DB fallback failed for '" + shopId + "': " + e.getMessage());
            }
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
                    plugin.getPlayerLookup().getPlayerName(sign.getOwner()));
            sender.sendMessage(ChatColor.GRAY + "Location: " +
                    location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
            sender.sendMessage(ChatColor.RED + "Run: /shop remove " + shopId + " --confirm");
            return true;
        }

        // Remove from in-memory map and database
        plugin.getSignManager().getBarterSigns().remove(location);

        int dbShopId = sign.getShopId();
        if (dbShopId > 0 && plugin.getShopRepository() != null) {
            plugin.getShopRepository().deleteById(dbShopId).exceptionally(ex -> {
                plugin.getLogger().warning("ShopRemoveSubCommand: DB delete failed for shopId " + dbShopId + ": " + ex.getMessage());
                return false;
            });
        }

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

        // Try to match by database shop ID
        try {
            int targetId = Integer.parseInt(id);
            return shops.entrySet().stream()
                    .filter(entry -> entry.getValue().getShopId() == targetId)
                    .findFirst();
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

            // Suggest database shop IDs owned by the sender
            Map<Location, BarterSign> shops = plugin.getSignManager().getBarterSigns();
            for (Map.Entry<Location, BarterSign> entry : shops.entrySet()) {
                BarterSign sign = entry.getValue();
                if (sign.getShopId() <= 0) continue;

                // Only suggest shops the player owns (or all if admin)
                if (sender instanceof Player player) {
                    if (!sign.getOwner().equals(player.getUniqueId()) &&
                            !sender.hasPermission("bartershops.admin")) {
                        continue;
                    }
                }

                String num = String.valueOf(sign.getShopId());
                if (num.startsWith(partial)) {
                    completions.add(num);
                }

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

    /** Worked examples served by {@code /shop help <verb>} (#1981). */
    @Override
    public java.util.List<String> getExamples() {
        return java.util.List.of(
                "/shop remove 123",
                "/shop remove 123 --confirm",
                "Breaking the chest no longer deletes the shop — use DELETE mode on the sign,",
                "or this command.");
    }
}
