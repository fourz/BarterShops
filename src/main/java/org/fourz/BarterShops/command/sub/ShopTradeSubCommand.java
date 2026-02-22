package org.fourz.BarterShops.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.BarterShops.trade.TradeSource;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Admin command to force-trade items from a shop to an online player.
 *
 * <pre>Usage: /shop trade &lt;player&gt; &lt;shopId&gt; [qty]</pre>
 *
 * <ul>
 *   <li>Permission: {@code bartershops.admin.trade}</li>
 *   <li>Console: YES — {@code requiresPlayer() == false}</li>
 *   <li>Payment: bypassed (source = {@link TradeSource#ADMIN_OVERRIDE})</li>
 * </ul>
 */
public class ShopTradeSubCommand implements SubCommand {

    private final BarterShops plugin;
    private final LogManager logger;

    public ShopTradeSubCommand(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ShopTradeSubCommand");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "> Usage: " + getUsage());
            return true;
        }

        // Arg 0: player name
        String playerName = args[0];
        Player target = Bukkit.getPlayer(playerName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "x Player '" + playerName + "' is not online.");
            return true;
        }

        // Arg 1: shopId
        int shopId;
        try {
            shopId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "x Shop ID must be an integer. Got: " + args[1]);
            return true;
        }

        // Locate shop in sign cache
        BarterSign shop = findShopById(shopId);
        if (shop == null) {
            sender.sendMessage(ChatColor.RED + "x Shop #" + shopId + " not found or not loaded.");
            return true;
        }

        // Resolve the offering item and base qty
        ItemStack offering = shop.getItemOffering();
        if (offering == null) {
            sender.sendMessage(ChatColor.RED + "x Shop #" + shopId + " has no configured offering item.");
            return true;
        }
        int baseQty = offering.getAmount() > 0 ? offering.getAmount() : 1;

        // Arg 2 (optional): total qty — must be a positive multiple of baseQty
        int totalQty;
        if (args.length >= 3) {
            try {
                totalQty = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "x Quantity must be an integer. Got: " + args[2]);
                return true;
            }
            if (totalQty <= 0) {
                sender.sendMessage(ChatColor.RED + "x Quantity must be positive.");
                return true;
            }
            if (totalQty % baseQty != 0) {
                sender.sendMessage(ChatColor.RED + "x Quantity must be a multiple of " + baseQty
                        + " (the shop's base offering amount). Got: " + totalQty);
                return true;
            }
        } else {
            totalQty = baseQty;
        }

        // Check shop stock (skip for admin shops with no container)
        Inventory shopInv = resolveShopInventory(shop);
        if (shopInv != null) {
            int available = countItems(shopInv, offering);
            if (available < totalQty) {
                sender.sendMessage(ChatColor.RED + "x Shop #" + shopId + " has insufficient stock. "
                        + "Need " + totalQty + ", available " + available + ".");
                return true;
            }
        }

        // Capture final copies for lambda
        final int finalQty = totalQty;
        final ItemStack finalOffering = offering.clone();

        // Execute trade async — ADMIN_OVERRIDE source, payment bypassed (null, 0)
        sender.sendMessage(ChatColor.YELLOW + "* Executing admin trade for " + target.getName() + "...");

        plugin.getTradeEngine()
                .executeDirectTrade(target, shop, finalOffering, finalQty, null, 0, TradeSource.ADMIN_OVERRIDE)
                .thenAccept(result -> {
                    // Schedule message delivery to main thread
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (result.success()) {
                            sender.sendMessage(ChatColor.GREEN + "+ Traded " + finalQty + "x "
                                    + finalOffering.getType().name()
                                    + " from shop #" + shopId
                                    + " to " + target.getName()
                                    + " (txId: " + result.transactionId() + ")");
                            logger.info("Admin trade executed: " + finalQty + "x " + finalOffering.getType().name()
                                    + " → " + target.getName() + " from shop #" + shopId
                                    + " by " + sender.getName());
                        } else {
                            sender.sendMessage(ChatColor.RED + "x Trade failed: " + result.message());
                        }
                    });
                })
                .exceptionally(ex -> {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            sender.sendMessage(ChatColor.RED + "x Internal error: " + ex.getMessage()));
                    logger.error("Admin trade exception for shop #" + shopId + ": " + ex.getMessage());
                    return null;
                });

        return true;
    }

    /**
     * Finds a BarterSign by its database shop ID, scanning the loaded sign cache.
     * Returns null if no matching sign is found.
     */
    private BarterSign findShopById(int shopId) {
        Map<org.bukkit.Location, BarterSign> signs = plugin.getSignManager().getBarterSigns();
        for (BarterSign sign : signs.values()) {
            if (sign.getShopId() == shopId) {
                return sign;
            }
        }
        return null;
    }

    /**
     * Resolves the backing inventory for stock checking.
     * Returns null for admin shops (no container = unlimited stock).
     */
    private Inventory resolveShopInventory(BarterSign shop) {
        if (shop.isAdmin()) {
            return null; // Admin shops have infinite stock
        }
        if (shop.getShopContainerWrapper() != null) {
            return shop.getShopContainerWrapper().getInventory();
        }
        if (shop.getShopContainer() != null) {
            return shop.getShopContainer().getInventory();
        }
        return null;
    }

    /**
     * Counts items in an inventory that match the given template (type only).
     */
    private int countItems(Inventory inventory, ItemStack template) {
        int count = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && stack.getType() == template.getType()) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    @Override
    public String getDescription() {
        return "Admin: force-trade items from a shop to a player (no payment)";
    }

    @Override
    public String getUsage() {
        return "/shop trade <player> <shopId> [qty]";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.isOp();
    }

    @Override
    public String getPermission() {
        return "bartershops.admin.trade";
    }

    @Override
    public boolean requiresPlayer() {
        return false; // Console-friendly
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Complete online player names
            String partial = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partial)) {
                    completions.add(p.getName());
                }
            }
        }
        // shopId and qty are hard to tab-complete meaningfully

        return completions;
    }
}
