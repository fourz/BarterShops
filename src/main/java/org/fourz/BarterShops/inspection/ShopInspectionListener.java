package org.fourz.BarterShops.inspection;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.BarterShops.sign.SignManager;
import org.fourz.BarterShops.trade.TradeValidator;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.List;
import java.util.Map;

/**
 * Listener for inspection tool interactions.
 * When a player holds the inspection tool and right-clicks a sign, displays shop details.
 */
public class ShopInspectionListener implements Listener {

    private final BarterShops plugin;
    private final InspectionManager inspectionManager;
    private final SignManager signManager;
    private final LogManager logger;
    private final TradeValidator tradeValidator;

    public ShopInspectionListener(BarterShops plugin, InspectionManager inspectionManager, SignManager signManager) {
        this.plugin = plugin;
        this.inspectionManager = inspectionManager;
        this.signManager = signManager;
        this.logger = LogManager.getInstance(plugin, "ShopInspectionListener");
        this.tradeValidator = new TradeValidator(plugin);
    }

    /**
     * Handles right-clicks on signs when player holds inspection tool.
     * Displays detailed shop information or allows normal sign interaction.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteractSign(PlayerInteractEvent event) {
        // Only process right-clicks
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Only process clicks on signs
        if (!(event.getClickedBlock().getState() instanceof Sign)) {
            return;
        }

        Player player = event.getPlayer();
        Location signLocation = event.getClickedBlock().getLocation();

        // Check if player holds inspection tool
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (!inspectionManager.isInspectionTool(heldItem)) {
            // Normal sign interaction - let it proceed
            return;
        }

        // Cancel the event to prevent normal sign interaction
        event.setCancelled(true);

        // Lookup BarterSign at this location
        BarterSign shop = signManager.getBarterSigns().get(signLocation);
        if (shop == null) {
            player.sendMessage(ChatColor.RED + "No barter shop found at this location");
            return;
        }

        // Display inspection information
        displayInspection(player, shop, signLocation);
    }

    /**
     * Displays detailed shop inspection information to the player.
     *
     * @param player Player to show information to
     * @param shop BarterSign with shop data
     * @param location Sign location
     */
    private void displayInspection(Player player, BarterSign shop, Location location) {
        player.sendMessage("" + ChatColor.GOLD + ChatColor.BOLD + "===== Shop Inspection =====");

        // Basic information
        player.sendMessage(ChatColor.YELLOW + "ID: " + ChatColor.WHITE + shop.getShopId());
        if (shop.getShopId() <= 0) {
            player.sendMessage(ChatColor.DARK_GRAY + "  (Database ID not yet assigned)");
        }

        player.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE +
                plugin.getPlayerLookup().getPlayerName(shop.getOwner()));

        player.sendMessage(ChatColor.YELLOW + "Location: " + ChatColor.WHITE +
                String.format("%s: %d, %d, %d",
                        location.getWorld().getName(),
                        location.getBlockX(),
                        location.getBlockY(),
                        location.getBlockZ()));

        // Shop mode and type
        player.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + shop.getType().name());
        player.sendMessage(ChatColor.YELLOW + "Mode: " + ChatColor.WHITE + shop.getMode().name());
        player.sendMessage(ChatColor.YELLOW + "Stackable: " + ChatColor.WHITE +
                (shop.isStackable() ? "Yes" : "No"));

        // Offering item information
        if (shop.getItemOffering() != null) {
            ItemStack offering = shop.getItemOffering();
            player.sendMessage(ChatColor.YELLOW + "Offering: " + ChatColor.WHITE +
                    offering.getAmount() + "x " + offering.getType().name());
        }

        // Payment options
        List<ItemStack> payments = shop.getAcceptedPayments();
        if (!payments.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Payment Options:");
            for (int i = 0; i < payments.size(); i++) {
                ItemStack payment = payments.get(i);
                player.sendMessage(ChatColor.WHITE + "  " + (i + 1) + ". " +
                        payment.getAmount() + "x " + payment.getType().name());
            }
        }

        // Stock information
        if (shop.getItemOffering() != null) {
            int stock = calculateStock(shop, shop.getItemOffering());
            if (shop.isAdmin()) {
                player.sendMessage(ChatColor.YELLOW + "Stock: " + ChatColor.GREEN + "Unlimited (admin shop)");
            } else if (stock >= 0) {
                player.sendMessage(ChatColor.YELLOW + "Stock: " + ChatColor.WHITE + stock + " available");
            } else {
                player.sendMessage(ChatColor.YELLOW + "Stock: " + ChatColor.RED + "No container found");
            }
        }

        // Admin status
        player.sendMessage(ChatColor.YELLOW + "Active: " + ChatColor.WHITE +
                (shop.isAdmin() ? "Admin Shop" : "Active"));

        player.sendMessage("" + ChatColor.GOLD + ChatColor.BOLD + "==========================");
    }

    /**
     * Calculates the current stock of the offered item in the shop container.
     *
     * @param shop BarterSign with shop data
     * @param offeredItem The item being offered for sale
     * @return Number of items available, or -1 if no container
     */
    private int calculateStock(BarterSign shop, ItemStack offeredItem) {
        if (shop.isAdmin()) {
            return -1; // Admin shops don't have physical inventory
        }

        // Use wrapper if available (Phase 2), otherwise fallback to plain container
        Inventory inv = null;
        if (shop.getShopContainerWrapper() != null) {
            inv = shop.getShopContainerWrapper().getInventory();
        } else if (shop.getShopContainer() != null) {
            inv = shop.getShopContainer().getInventory();
        }

        if (inv == null) {
            return -1;
        }

        return tradeValidator.countItems(inv, offeredItem);
    }
}
