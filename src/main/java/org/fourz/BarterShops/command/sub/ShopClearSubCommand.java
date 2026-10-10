package org.fourz.BarterShops.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
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
        Optional<Map.Entry<Location, BarterSign>> shopEntry = plugin.getSignManager().findShop(shopId, sender);

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
                        sender.sendMessage(ChatColor.RED + "The chunk must be loaded before the shop inventory can be cleared.");
                        sender.sendMessage(ChatColor.GRAY + "Travel to the shop location to load the chunk, then re-run the command.");
                        return true;
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("ShopClearSubCommand: DB fallback failed for '" + shopId + "': " + e.getMessage());
            }
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
        String ownerName = plugin.getPlayerLookup().getPlayerName(sign.getOwner());
        logger.info("Admin " + sender.getName() + " cleared shop " + shopId +
                " (owner: " + ownerName + ") - " + clearedItems + " item stacks removed");

        sender.sendMessage(ChatColor.GREEN + "Shop inventory cleared!");
        sender.sendMessage(ChatColor.GRAY + "Cleared " + clearedItems + " item stacks from shop " + shopId);
        sender.sendMessage(ChatColor.GRAY + "Owner: " + ownerName);

        return true;
    }

    @Override
    public String getDescription() {
        return "Clear shop inventory (admin)";
    }

    @Override
    public String getUsage() {
        return "/shop clear <id|x,y,z|world,x,y,z>";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("bartershops.admin");
    }

    @Override
    public String getPermission() {
        return "bartershops.admin";
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

    /** Worked examples served by {@code /shop help <verb>} (#1981). */
    @Override
    public java.util.List<String> getExamples() {
        return java.util.List.of(
                "/shop clear 123",
                "/shop clear 100,64,-200",
                "  accepts the shop id from /shop list, x,y,z in your world, or world,x,y,z");
    }
}
