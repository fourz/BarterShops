package org.fourz.BarterShops.trade;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates trade transactions before execution.
 * Checks inventory space, item availability, and trade conditions.
 */
public class TradeValidator {

    private final BarterShops plugin;
    private final LogManager logger;

    public TradeValidator(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "TradeValidator");
    }

    /**
     * Validates a complete trade session.
     *
     * @param session The trade session to validate
     * @param buyer The buyer player
     * @return Validation result with any errors
     */
    public ValidationResult validate(TradeSession session, Player buyer) {
        List<String> errors = new ArrayList<>();

        // Check session validity
        if (session == null) {
            errors.add("Invalid trade session");
            return new ValidationResult(false, errors);
        }

        if (session.isExpired()) {
            errors.add("Trade session has expired");
            return new ValidationResult(false, errors);
        }

        if (!session.isActive()) {
            errors.add("Trade session is no longer active");
            return new ValidationResult(false, errors);
        }

        // Check buyer
        if (buyer == null || !buyer.isOnline()) {
            errors.add("Buyer is not online");
            return new ValidationResult(false, errors);
        }

        if (!buyer.getUniqueId().equals(session.getBuyerUuid())) {
            errors.add("Player does not match trade session");
            return new ValidationResult(false, errors);
        }

        // Check shop
        BarterSign shop = session.getShop();
        if (shop == null) {
            errors.add("Shop no longer exists");
            return new ValidationResult(false, errors);
        }

        // Check items are configured
        if (session.getOfferedItem() == null) {
            errors.add("No item configured for trade");
            return new ValidationResult(false, errors);
        }

        // Validate buyer has payment
        errors.addAll(validateBuyerInventory(buyer, session));

        // Validate shop has stock (if container-based)
        errors.addAll(validateShopStock(shop, session));

        // Validate buyer has space for received items
        errors.addAll(validateBuyerSpace(buyer, session));

        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Validates buyer has the required payment items.
     */
    private List<String> validateBuyerInventory(Player buyer, TradeSession session) {
        List<String> errors = new ArrayList<>();

        ItemStack required = session.getRequestedItem();
        int requiredAmount = session.getRequestedQuantity();

        if (required == null || requiredAmount <= 0) {
            // No payment required (free item or admin shop)
            return errors;
        }

        int available = countItems(buyer.getInventory(), required);
        if (available < requiredAmount) {
            errors.add(String.format("Insufficient payment: need %d %s, have %d",
                    requiredAmount, getItemName(required), available));
        }

        return errors;
    }

    /**
     * Validates shop has items in stock.
     */
    private List<String> validateShopStock(BarterSign shop, TradeSession session) {
        List<String> errors = new ArrayList<>();

        ItemStack offered = session.getOfferedItem();
        int offeredAmount = session.getOfferedQuantity();

        if (offered == null || offeredAmount <= 0) {
            errors.add("Invalid trade configuration");
            return errors;
        }

        // Check shop container for stock
        // Use wrapper if available (Phase 2), otherwise fallback to plain container
        org.fourz.BarterShops.container.ShopContainer wrapper = shop.getShopContainerWrapper();
        if (wrapper != null) {
            Inventory shopInv = wrapper.getInventory();
            int stock = countItems(shopInv, offered);

            if (stock < offeredAmount) {
                errors.add(String.format("Shop out of stock: need %d %s, have %d",
                        offeredAmount, getItemName(offered), stock));
            }
        } else if (shop.getShopContainer() != null) {
            // Fallback to plain container (backward compat)
            Inventory shopInv = shop.getShopContainer().getInventory();
            int stock = countItems(shopInv, offered);

            if (stock < offeredAmount) {
                errors.add(String.format("Shop out of stock: need %d %s, have %d",
                        offeredAmount, getItemName(offered), stock));
            }
        }
        // If no container, assume admin shop with unlimited stock

        return errors;
    }

    /**
     * Validates buyer has inventory space for received items.
     */
    private List<String> validateBuyerSpace(Player buyer, TradeSession session) {
        List<String> errors = new ArrayList<>();

        ItemStack offered = session.getOfferedItem();
        int offeredAmount = session.getOfferedQuantity();

        if (offered == null) return errors;

        int freeSpace = countFreeSpace(buyer.getInventory(), offered);
        if (freeSpace < offeredAmount) {
            errors.add(String.format("Not enough inventory space: need %d slots, have %d",
                    offeredAmount, freeSpace));
        }

        return errors;
    }

    /**
     * Counts how many of an item exist in an inventory.
     */
    public int countItems(Inventory inventory, ItemStack item) {
        if (inventory == null || item == null) return 0;

        int count = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && stack.isSimilar(item)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    /**
     * Counts available space for an item type.
     */
    public int countFreeSpace(Inventory inventory, ItemStack item) {
        if (inventory == null || item == null) return 0;

        int space = 0;
        int maxStack = item.getMaxStackSize();

        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                space += maxStack;
            } else if (stack.isSimilar(item)) {
                space += maxStack - stack.getAmount();
            }
        }
        return space;
    }

    /**
     * Gets a display name for an item.
     */
    private String getItemName(ItemStack item) {
        if (item == null) return "Unknown";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name().toLowerCase().replace('_', ' ');
    }

    /**
     * Result of a trade validation.
     */
    public record ValidationResult(boolean valid, List<String> errors) {
        public ValidationResult {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }

        public static ValidationResult success() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult failure(String error) {
            return new ValidationResult(false, List.of(error));
        }

        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors);
        }
    }
}
