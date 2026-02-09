package org.fourz.BarterShops.command.sub;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.data.dto.ShopDataDTO;

import java.util.ArrayList;
import java.util.List;
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

        if (plugin.getShopRepository() == null) {
            sender.sendMessage(ChatColor.RED + "Shop database not available.");
            return true;
        }

        String shopArg = args[0];

        // Find shop by ID or name from database
        Optional<ShopDataDTO> shopOpt = findShop(shopArg);

        if (shopOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Shop not found: " + shopArg);
            sender.sendMessage(ChatColor.GRAY + "Use /shop list to see available shops.");
            return true;
        }

        ShopDataDTO shop = shopOpt.get();

        // Display shop info
        sender.sendMessage(ChatColor.GOLD + "===== Shop Info =====");
        sender.sendMessage(ChatColor.YELLOW + "ID: " + ChatColor.WHITE + shop.shopId());
        if (shop.shopName() != null && !shop.shopName().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Name: " + ChatColor.WHITE + shop.shopName());
        }
        sender.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE +
                plugin.getPlayerLookup().getPlayerName(shop.ownerUuid()));
        sender.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE +
                (shop.shopType() != null ? shop.shopType().name() : "BARTER"));
        sender.sendMessage(ChatColor.YELLOW + "Active: " + ChatColor.WHITE + shop.isActive());

        if (shop.locationWorld() != null) {
            sender.sendMessage(ChatColor.YELLOW + "Location: " + ChatColor.WHITE +
                    String.format("%s: %d, %d, %d",
                            shop.locationWorld(),
                            (int) shop.locationX(), (int) shop.locationY(), (int) shop.locationZ()));
        }

        // Trade info placeholder
        sender.sendMessage(ChatColor.YELLOW + "Trades: " + ChatColor.GRAY + "(Use /shop inspect <id> for details)");

        return true;
    }

    private Optional<ShopDataDTO> findShop(String arg) {
        // Try numeric shop ID first
        try {
            int shopId = Integer.parseInt(arg);
            return plugin.getShopRepository().findById(shopId).join();
        } catch (NumberFormatException ignored) {
        }

        // Try name match against all active shops
        try {
            List<ShopDataDTO> shops = plugin.getShopRepository().findAllActive().join();
            String lowerArg = arg.toLowerCase();
            return shops.stream()
                    .filter(s -> s.shopName() != null && s.shopName().toLowerCase().equals(lowerArg))
                    .findFirst();
        } catch (Exception ignored) {
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

        if (args.length == 1 && plugin.getShopRepository() != null) {
            String partial = args[0].toLowerCase();

            try {
                List<ShopDataDTO> shops = plugin.getShopRepository().findAllActive().join();
                for (ShopDataDTO shop : shops) {
                    // Suggest shop IDs
                    String idStr = String.valueOf(shop.shopId());
                    if (idStr.startsWith(partial)) {
                        completions.add(idStr);
                    }
                    // Suggest shop names
                    if (shop.shopName() != null && shop.shopName().toLowerCase().startsWith(partial)) {
                        completions.add(shop.shopName());
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return completions;
    }

    @Override
    public boolean requiresPlayer() {
        return false; // Console-friendly
    }
}
