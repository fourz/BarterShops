package org.fourz.BarterShops.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.sign.BarterSign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Subcommand for showing shop details.
 * Usage: /shop info <id>
 * Console-friendly: Yes
 */
public class ShopInfoSubCommand implements SubCommand {

    private final BarterShops plugin;

    public ShopInfoSubCommand(BarterShops plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: " + getUsage());
            return true;
        }

        String shopId = args[0];

        // Find shop by ID (using location hash for now)
        Optional<Map.Entry<Location, BarterSign>> shopEntry = findShopById(shopId);

        if (shopEntry.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Shop not found: " + shopId);
            sender.sendMessage(ChatColor.GRAY + "Use /shop list to see available shops.");
            return true;
        }

        Location location = shopEntry.get().getKey();
        BarterSign sign = shopEntry.get().getValue();

        // Display shop info
        sender.sendMessage(ChatColor.GOLD + "===== Shop Info =====");
        sender.sendMessage(ChatColor.YELLOW + "ID: " + ChatColor.WHITE + shopId);
        sender.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE +
                plugin.getPlayerLookup().getPlayerName(sign.getOwner()));
        sender.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + sign.getType());
        sender.sendMessage(ChatColor.YELLOW + "Mode: " + ChatColor.WHITE + sign.getMode());
        sender.sendMessage(ChatColor.YELLOW + "Location: " + ChatColor.WHITE +
                String.format("%s: %d, %d, %d",
                        location.getWorld().getName(),
                        location.getBlockX(), location.getBlockY(), location.getBlockZ()));

        // Trade info (placeholder - will use DTOs when service is implemented)
        sender.sendMessage(ChatColor.YELLOW + "Trades: " + ChatColor.GRAY + "(Use /shop info <id> trades)");

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
        return "Show detailed information about a shop";
    }

    @Override
    public String getUsage() {
        return "/shop info <id|x,y,z>";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.isOp();
    }

    @Override
    public String getPermission() {
        return "bartershops.command.info";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();

            // Suggest shop numbers
            Map<Location, BarterSign> shops = plugin.getSignManager().getBarterSigns();
            for (int i = 1; i <= Math.min(shops.size(), 10); i++) {
                String num = String.valueOf(i);
                if (num.startsWith(partial)) {
                    completions.add(num);
                }
            }
        }

        return completions;
    }

    @Override
    public boolean requiresPlayer() {
        return false; // Console-friendly
    }
}
