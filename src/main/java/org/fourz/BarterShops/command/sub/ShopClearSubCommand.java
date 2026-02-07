package org.fourz.BarterShops.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;

/**
 * Admin subcommand for clearing shop chest inventory.
 * Usage: /shop clear <id>
 * Console-friendly: Yes
 */
public class ShopClearSubCommand implements SubCommand {
    private final BarterShops plugin;
    private final LogManager logger;

    public ShopClearSubCommand(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ShopClear");
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

        // Get linked container directly from BarterSign
        Container container = sign.getContainer();
        if (container == null) {
            container = sign.getShopContainer();
        }
        if (container == null) {
            sender.sendMessage(ChatColor.RED + "No container linked to this shop.");
            return true;
        }

        // Clear the container inventory
        Container chestState = container;
        int clearedItems = 0;
        for (int i = 0; i < chestState.getInventory().getSize(); i++) {
            if (chestState.getInventory().getItem(i) != null) {
                clearedItems++;
            }
        }
        chestState.getInventory().clear();

        // Log the action
        String ownerName = Bukkit.getOfflinePlayer(sign.getOwner()).getName();
        logger.info("Admin " + sender.getName() + " cleared shop " + shopId +
                " (owner: " + ownerName + ") - " + clearedItems + " item stacks removed");

        sender.sendMessage(ChatColor.GREEN + "Shop inventory cleared!");
        sender.sendMessage(ChatColor.GRAY + "Cleared " + clearedItems + " item stacks from shop " + shopId);
        sender.sendMessage(ChatColor.GRAY + "Owner: " + ownerName);

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
        return "Clear shop inventory (admin)";
    }

    @Override
    public String getUsage() {
        return "/shop clear <id|x,y,z>";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.isOp();
    }

    @Override
    public String getPermission() {
        return "bartershops.admin.clear";
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
