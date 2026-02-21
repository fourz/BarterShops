package org.fourz.BarterShops.inspection;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.preferences.PlayerShopPreferences;
import org.fourz.BarterShops.preferences.ShopPreferenceManager;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.BarterShops.trade.TradeValidator;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.List;
import java.util.UUID;

/**
 * Displays shop information based on permission tier and player preferences.
 *
 * Permission Tiers:
 * - Owner: Full details (all fields)
 * - Admin (bartershops.admin): Full details
 * - Info-viewer (bartershops.command.info): Limited view (offering + payments only)
 * - Default: Only own shops if preference enabled
 */
public class ShopInfoDisplayHelper {
    private static final String CLASS_NAME = "ShopInfoDisplayHelper";
    private final BarterShops plugin;
    private final ShopPreferenceManager preferenceManager;
    private final LogManager logger;
    private final TradeValidator tradeValidator;

    public enum InfoDisplayContext {
        SHIFT_CLICK,    // Shift+right-click on sign
        COMMAND,        // /shop info command
    }

    public enum ShopInfoView {
        FULL_DETAILS,   // All fields (owner/admin)
        LIMITED_VIEW,   // Offering + payments only
        NOTHING         // No permission to view
    }

    public ShopInfoDisplayHelper(BarterShops plugin, ShopPreferenceManager preferenceManager) {
        this.plugin = plugin;
        this.preferenceManager = preferenceManager;
        this.logger = LogManager.getInstance(plugin, CLASS_NAME);
        this.tradeValidator = new TradeValidator(plugin);
    }

    /**
     * Main method - displays shop info based on permission tier and preferences.
     */
    public void displayShopInfo(Player player, BarterSign shop, Location location, InfoDisplayContext context) {
        UUID playerId = player.getUniqueId();
        PlayerShopPreferences prefs = preferenceManager.getPreferences(playerId);

        // Check if info display is enabled
        if (!prefs.infoDisplayEnabled()) {
            logger.debug("Info display disabled for player: " + player.getName());
            return;
        }

        // Get permission-aware view
        ShopInfoView view = getPermissionAwareView(player, shop);

        // Check if player has permission to view this shop
        if (view == ShopInfoView.NOTHING) {
            logger.debug("Player " + player.getName() + " lacks permission to view shop");
            return;
        }

        // Check "own shops only" preference
        if (prefs.ownShopsOnly() && !shop.getOwner().equals(playerId)) {
            logger.debug("Player " + player.getName() + " restricted to own shops only");
            return;
        }

        boolean useChatFormat = prefs.chatFormat();
        logger.debug("Info displayed - Player: " + player.getName() + ", View: " + view + ", Format: " +
                (useChatFormat ? "chat" : "actionbar"));

        // Resolve owner name async (hits Mojang API if not in local cache), then send on main thread
        plugin.getPlayerLookup().getPlayerNameAsync(shop.getOwner())
                .thenAcceptAsync(ownerName ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (useChatFormat) {
                                sendChatInfo(player, view, shop, location, ownerName);
                            } else {
                                sendActionBarInfo(player, view, shop, location, ownerName);
                            }
                        })
                );
    }

    /**
     * Displays shop info for a customer's explicit shift+click request.
     * Bypasses preference gates — customer is actively asking for this info.
     * Always uses chat format. Non-owners get LIMITED_VIEW (offering + payments).
     */
    public void displayShopInfoForCustomer(Player player, BarterSign shop, Location location) {
        ShopInfoView view = getPermissionAwareView(player, shop);
        plugin.getPlayerLookup().getPlayerNameAsync(shop.getOwner())
                .thenAcceptAsync(ownerName ->
                        Bukkit.getScheduler().runTask(plugin, () ->
                                sendChatInfo(player, view, shop, location, ownerName)
                        )
                );
    }

    /**
     * Determines permission-aware info view tier.
     */
    private ShopInfoView getPermissionAwareView(Player player, BarterSign shop) {
        UUID playerId = player.getUniqueId();

        // Owner - always full details
        if (shop.getOwner().equals(playerId)) {
            return ShopInfoView.FULL_DETAILS;
        }

        // Admin - full details
        if (player.hasPermission("bartershops.admin")) {
            return ShopInfoView.FULL_DETAILS;
        }

        // Info-viewer - limited view
        if (player.hasPermission("bartershops.command.info")) {
            return ShopInfoView.LIMITED_VIEW;
        }

        // No explicit permission - default is limited to own shops (checked by caller)
        return ShopInfoView.LIMITED_VIEW;
    }

    /**
     * Sends shop info in chat format.
     */
    private void sendChatInfo(Player player, ShopInfoView view, BarterSign shop, Location location, String ownerName) {
        player.sendMessage(ChatColor.GOLD + "========== Shop Inspection ==========");

        if (view == ShopInfoView.FULL_DETAILS) {
            // Full details view
            player.sendMessage(ChatColor.YELLOW + "ID: " + ChatColor.WHITE + shop.getShopId());
            player.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE + ownerName);
            player.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + shop.getType().name());
            player.sendMessage(ChatColor.YELLOW + "Mode: " + ChatColor.WHITE + shop.getMode().name());

            if (location.getWorld() != null) {
                player.sendMessage(ChatColor.YELLOW + "Location: " + ChatColor.WHITE +
                        String.format("%s: %d, %d, %d",
                                location.getWorld().getName(),
                                location.getBlockX(), location.getBlockY(), location.getBlockZ()));
            }
        }

        // Owner name (shown in limited view; full details shows it in the block above)
        if (view == ShopInfoView.LIMITED_VIEW) {
            player.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE + ownerName);
        }

        // Offering (shown in both views)
        if (shop.getItemOffering() != null) {
            ItemStack offering = shop.getItemOffering();
            player.sendMessage(ChatColor.YELLOW + "Offering: " + ChatColor.WHITE +
                    offering.getAmount() + "x " + offering.getType().name());
        }

        // Payment options (shown in both views)
        List<ItemStack> payments = shop.getAcceptedPayments();
        if (!payments.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Payment Options:");
            for (int i = 0; i < payments.size(); i++) {
                ItemStack payment = payments.get(i);
                player.sendMessage(ChatColor.WHITE + "  " + (i + 1) + ". " +
                        payment.getAmount() + "x " + payment.getType().name());
            }
        }

        // Stock (full details only)
        if (view == ShopInfoView.FULL_DETAILS && shop.getItemOffering() != null) {
            int stock = calculateStock(shop, shop.getItemOffering());
            if (shop.isAdmin()) {
                player.sendMessage(ChatColor.YELLOW + "Stock: " + ChatColor.GREEN + "Unlimited (admin shop)");
            } else if (stock >= 0) {
                player.sendMessage(ChatColor.YELLOW + "Stock: " + ChatColor.WHITE + stock + " available");
            } else {
                player.sendMessage(ChatColor.YELLOW + "Stock: " + ChatColor.RED + "No container found");
            }
        }

        // Active status (full details only)
        if (view == ShopInfoView.FULL_DETAILS) {
            player.sendMessage(ChatColor.YELLOW + "Active: " + ChatColor.WHITE + "Yes");
        }

        player.sendMessage(ChatColor.GOLD + "======================================");
    }

    /**
     * Sends shop info in actionbar format.
     */
    private void sendActionBarInfo(Player player, ShopInfoView view, BarterSign shop, Location location, String ownerName) {
        StringBuilder message = new StringBuilder();

        if (view == ShopInfoView.FULL_DETAILS) {
            // Full details: ID | Owner | Type | Location
            message.append(ChatColor.YELLOW).append("ID: ").append(ChatColor.WHITE).append(shop.getShopId())
                    .append(" | ")
                    .append(ChatColor.YELLOW).append("Owner: ").append(ChatColor.WHITE)
                    .append(ownerName)
                    .append(" | ")
                    .append(ChatColor.YELLOW).append("Type: ").append(ChatColor.WHITE).append(shop.getType().name());
        } else {
            // Limited view: Offering | Payment(s)
            if (shop.getItemOffering() != null) {
                ItemStack offering = shop.getItemOffering();
                message.append(ChatColor.YELLOW).append("Offering: ").append(ChatColor.WHITE)
                        .append(offering.getAmount()).append("x ").append(offering.getType().name());
            }

            List<ItemStack> payments = shop.getAcceptedPayments();
            if (!payments.isEmpty()) {
                message.append(" | ").append(ChatColor.YELLOW).append("Payments: ").append(ChatColor.WHITE);
                for (int i = 0; i < payments.size(); i++) {
                    if (i > 0) message.append(", ");
                    ItemStack payment = payments.get(i);
                    message.append(payment.getAmount()).append("x ").append(payment.getType().name());
                }
            }
        }

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message.toString()));
    }

    /**
     * Calculates current stock in shop container.
     */
    private int calculateStock(BarterSign shop, ItemStack offeredItem) {
        if (shop.isAdmin()) {
            return -1; // Admin shops have unlimited stock
        }

        // Use wrapper if available (Phase 2), otherwise fallback
        var inv = shop.getShopContainerWrapper() != null ?
                shop.getShopContainerWrapper().getInventory() :
                (shop.getShopContainer() != null ? shop.getShopContainer().getInventory() : null);

        if (inv == null) {
            return -1;
        }

        return tradeValidator.countItems(inv, offeredItem);
    }
}
