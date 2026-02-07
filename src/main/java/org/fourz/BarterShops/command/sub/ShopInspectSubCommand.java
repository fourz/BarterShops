package org.fourz.BarterShops.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.sign.BarterSign;

import java.util.*;

/**
 * Admin subcommand for inspecting any shop with trade history.
 * Usage: /shop inspect <id>
 * Console-friendly: Yes
 */
public class ShopInspectSubCommand implements SubCommand {
    private final BarterShops plugin;

    public ShopInspectSubCommand(BarterShops plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: " + getUsage());
            return true;
        }

        String shopId = args[0];
        Optional<Map.Entry<Location, BarterSign>> shopEntry = findShopById(shopId);

        if (shopEntry.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Shop not found: " + shopId);
            return true;
        }

        Location location = shopEntry.get().getKey();
        BarterSign sign = shopEntry.get().getValue();

        // Display detailed admin inspection
        sender.sendMessage(ChatColor.GOLD + "===== Shop Inspection =====");
        sender.sendMessage(ChatColor.YELLOW + "ID: " + ChatColor.WHITE + shopId);
        sender.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE +
                plugin.getPlayerLookup().getPlayerName(sign.getOwner()));
        sender.sendMessage(ChatColor.YELLOW + "Owner UUID: " + ChatColor.GRAY + sign.getOwner());
        sender.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + sign.getType());
        sender.sendMessage(ChatColor.YELLOW + "Mode: " + ChatColor.WHITE + sign.getMode());
        sender.sendMessage(ChatColor.YELLOW + "Location: " + ChatColor.WHITE +
                String.format("%s: %d, %d, %d",
                        location.getWorld().getName(),
                        location.getBlockX(), location.getBlockY(), location.getBlockZ()));
        sender.sendMessage(ChatColor.YELLOW + "Active: " + ChatColor.WHITE + "Yes");

        // Trade history placeholder (will use ITradeRepository when available)
        sender.sendMessage(ChatColor.GOLD + "--- Recent Trades (last 10) ---");
        sender.sendMessage(ChatColor.GRAY + "Trade history requires ITradeRepository implementation");

        return true;
    }

    private Optional<Map.Entry<Location, BarterSign>> findShopById(String id) {
        Map<Location, BarterSign> shops = plugin.getSignManager().getBarterSigns();

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
                } catch (NumberFormatException ignored) {}
            }
        }

        try {
            int index = Integer.parseInt(id) - 1;
            List<Map.Entry<Location, BarterSign>> shopList = new ArrayList<>(shops.entrySet());
            if (index >= 0 && index < shopList.size()) {
                return Optional.of(shopList.get(index));
            }
        } catch (NumberFormatException ignored) {}

        return Optional.empty();
    }

    @Override
    public String getDescription() {
        return "Inspect detailed shop information (admin)";
    }

    @Override
    public String getUsage() {
        return "/shop inspect <id|x,y,z>";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.isOp();
    }

    @Override
    public String getPermission() {
        return "bartershops.admin.inspect";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            Map<Location, BarterSign> shops = plugin.getSignManager().getBarterSigns();
            for (int i = 1; i <= Math.min(shops.size(), 10); i++) {
                if (String.valueOf(i).startsWith(args[0])) {
                    completions.add(String.valueOf(i));
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
