package org.fourz.BarterShops.sign;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.block.sign.SignSide;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.shop.ShopMode;
import org.fourz.BarterShops.container.ShopContainer;
import org.fourz.BarterShops.container.validation.MultiTypeItemRule;
import org.fourz.BarterShops.container.validation.UnstackableItemOnlyRule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BarterSign {
    private final String id;
    private final UUID owner;
    private final Location signLocation;
    private String group;
    private SignType type;
    private Container container;
    private Container shopContainer;
    private ShopContainer shopContainerWrapper;  // Phase 2 Integration: Wrapper for validation
    private ShopMode mode;
    private SignSide signSideDisplayFront;
    private SignSide signSideDisplayBack;

    // Database reference
    private int shopId = -1;  // Database ID for this shop (populated from ShopDataDTO)

    // NEW FIELDS for shop configuration
    private ItemStack itemOffering;  // The specific item to sell (stackable shops only)
    private ItemStack priceItem;     // The payment item required
    private int priceAmount;         // Quantity of payment required
    private boolean isStackable = true;  // Whether this shop is stackable or unstackable
    private boolean typeDetected = false;  // Whether shop type has been auto-detected (locked)
    private boolean isAdmin = false;  // Admin shops have infinite inventory, no chest needed
    private Material lockedItemType = null;  // For stackable shops: the item type that's locked in
    private List<ItemStack> acceptedPayments = new ArrayList<>();  // Multiple payment options for BARTER mode

    // SESSION-ONLY FIELDS (UI state, NOT persisted to database)
    private boolean ownerPreviewMode = false;  // Owner viewing customer preview
    private int currentPaymentPage = 0;        // Current payment page index (0-based)

    private BarterSign(Builder builder) {
        this.id = builder.id;
        this.owner = builder.owner;
        this.signLocation = builder.signLocation;
        this.group = builder.group;
        this.type = builder.type;
        this.container = builder.container;
        this.shopContainer = builder.shopContainer;
        this.mode = builder.mode;
        this.signSideDisplayFront = builder.signSideDisplayFront;
        this.signSideDisplayBack = builder.signSideDisplayBack;
    }

    // Getters/Setters
    public String getId() { return id; }
    public UUID getOwner() { return owner; }
    public Location getSignLocation() { return signLocation; }
    public String getGroup() { return group; }
    public SignType getType() { return type; }
    public Container getContainer() { return container; }
    public Container getShopContainer() { return shopContainer; }
    public ShopMode getMode() { return mode; }
    public void setMode(ShopMode mode) { this.mode = mode; }
    public void setType(SignType type) { this.type = type; }
    public SignSide getSignSideDisplayFront() { return signSideDisplayFront; }
    public SignSide getSignSideDisplayBack() { return signSideDisplayBack; }
    public void setSignSideDisplayFront(SignSide side) { this.signSideDisplayFront = side; }
    public void setSignSideDisplayBack(SignSide side) { this.signSideDisplayBack = side; }

    // Database ID accessors
    public int getShopId() { return shopId; }
    public void setShopId(int shopId) { this.shopId = shopId; }

    // Shop container wrapper (Phase 2 Integration)
    public ShopContainer getShopContainerWrapper() { return shopContainerWrapper; }
    public void setShopContainerWrapper(ShopContainer wrapper) { this.shopContainerWrapper = wrapper; }

    // Compatibility: Return wrapper if available, otherwise fallback to plain container
    public Container getShopContainerCompat() {
        return shopContainerWrapper != null ? shopContainerWrapper.getContainer() : shopContainer;
    }

    // NEW: Item offering configuration
    public ItemStack getItemOffering() { return itemOffering; }
    public ItemStack getPriceItem() { return priceItem; }
    public int getPriceAmount() { return priceAmount; }
    public boolean isStackable() { return isStackable; }
    public void setStackable(boolean stackable) { this.isStackable = stackable; }
    public boolean isAdmin() { return isAdmin; }
    public void setAdmin(boolean admin) { this.isAdmin = admin; }

    /**
     * Configures the offering item for stackable shops.
     * Locks the shop type based on the offering item.
     * NOTE: Does NOT lock to single item type. Instead, uses getAllowedChestTypes()
     * to dynamically allow all payment types + offering type.
     */
    public void configureStackableShop(ItemStack itemInHand, int quantity) {
        this.itemOffering = itemInHand.clone();
        this.itemOffering.setAmount(quantity);

        // Lock shop type based on offering item
        boolean itemIsStackable = isItemStackable(itemInHand);
        setStackable(itemIsStackable);
        setTypeDetected(true); // Lock the type
    }

    /**
     * Configures price for the shop.
     */
    public void configurePrice(ItemStack paymentItem, int amount) {
        if (paymentItem != null) {
            this.priceItem = paymentItem.clone();
            this.priceItem.setAmount(1); // Store single item, amount separate
        } else {
            this.priceItem = null;
        }
        this.priceAmount = amount;
    }

    /**
     * Adds a payment option for BARTER shops.
     */
    public void addPaymentOption(ItemStack paymentItem, int amount) {
        if (paymentItem == null) return;

        ItemStack payment = paymentItem.clone();
        payment.setAmount(amount);

        // Check if this material is already accepted
        Material paymentMaterial = paymentItem.getType();
        acceptedPayments.removeIf(p -> p.getType() == paymentMaterial);

        acceptedPayments.add(payment);
    }

    /**
     * Removes a payment option by material type.
     */
    public boolean removePaymentOption(Material material) {
        return acceptedPayments.removeIf(p -> p.getType() == material);
    }

    /**
     * Clears all payment options.
     */
    public void clearPaymentOptions() {
        acceptedPayments.clear();
    }

    /**
     * Gets the list of accepted payment options for BARTER mode.
     */
    public List<ItemStack> getAcceptedPayments() {
        return new ArrayList<>(acceptedPayments);
    }

    /**
     * Checks if a payment item is accepted.
     */
    public boolean isPaymentAccepted(ItemStack paymentItem) {
        if (paymentItem == null) return false;

        // For BUY/SELL modes, check single priceItem
        if (type != SignType.BARTER) {
            return priceItem != null && priceItem.isSimilar(paymentItem);
        }

        // For BARTER mode, check acceptedPayments list
        Material paymentMaterial = paymentItem.getType();
        return acceptedPayments.stream()
            .anyMatch(p -> p.getType() == paymentMaterial);
    }

    /**
     * Gets the payment amount for a given material.
     */
    public int getPaymentAmount(Material material) {
        return acceptedPayments.stream()
            .filter(p -> p.getType() == material)
            .map(ItemStack::getAmount)
            .findFirst()
            .orElse(0);
    }

    /**
     * Gets all item types that should be allowed in the shop chest.
     * Includes: offering item + all payment option items.
     * This allows owners to restock offerings AND customers to deposit payments.
     *
     * @return Set of allowed material types for chest contents
     */
    public Set<Material> getAllowedChestTypes() {
        Set<Material> allowedTypes = new HashSet<>();

        // Allow offering item (for owner restocking)
        if (itemOffering != null) {
            allowedTypes.add(itemOffering.getType());
        }

        // Allow all payment items (for customer payments)
        for (ItemStack payment : acceptedPayments) {
            if (payment != null) {
                allowedTypes.add(payment.getType());
            }
        }

        return allowedTypes;
    }

    /**
     * Updates the ShopContainerWrapper's validation rules based on current configuration.
     * Call this whenever shop configuration changes (offering, payments, etc).
     * CRITICAL: Must be called after payment options are added.
     */
    public void updateValidationRules() {
        if (shopContainerWrapper == null) {
            return; // Container not yet created
        }

        // Clear old rules
        shopContainerWrapper.clearValidationRules();

        // Re-add rules based on current configuration
        if (isStackable()) {
            Set<Material> allowedTypes = getAllowedChestTypes();
            if (!allowedTypes.isEmpty()) {
                shopContainerWrapper.addValidationRule(
                    new org.fourz.BarterShops.container.validation.MultiTypeItemRule(allowedTypes)
                );
            }
        } else if (!isStackable()) {
            shopContainerWrapper.addValidationRule(
                new org.fourz.BarterShops.container.validation.UnstackableItemOnlyRule()
            );
        }
    }

    /**
     * Checks if shop is fully configured and ready to activate.
     * Configuration depends on shop type:
     * - BARTER: Must have offering and at least one payment option
     * - BUY/SELL: Must have offering and price configured
     */
    public boolean isConfigured() {
        // Must have offering item set
        if (itemOffering == null) return false;

        // Payment configuration depends on type
        return switch(type) {
            case BARTER -> !acceptedPayments.isEmpty();
            case BUY, SELL -> priceItem != null && priceAmount > 0;
        };
    }

    /**
     * Detects if an ItemStack is stackable (can stack in inventory).
     * Stackable items have maxStackSize > 1
     */
    public static boolean isItemStackable(ItemStack item) {
        return item != null && item.getMaxStackSize() > 1;
    }

    /**
     * Detects if first item in chest is stackable and sets shop type accordingly.
     * Only sets type on first detection - type is locked after initial detection.
     * For stackable shops, locks the item type to prevent mixing.
     * Returns true if type was set, false if chest is empty, type already locked, or no detection needed.
     */
    public boolean detectAndSetTypeFromChest() {
        // Type is locked after first detection - only deletion resets it
        if (typeDetected) {
            return false;
        }

        Container container = getShopContainer();
        if (container == null) {
            container = getContainer();
        }
        if (container == null) {
            return false;
        }

        // Find first non-air item
        for (ItemStack item : container.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                // Set shop type based on item stackability
                boolean itemIsStackable = isItemStackable(item);
                setStackable(itemIsStackable);

                // For stackable shops, lock the item type
                if (itemIsStackable) {
                    lockedItemType = item.getType();
                }

                typeDetected = true;  // Lock the type
                return true;
            }
        }
        return false;
    }

    /**
     * Resets type detection flag and locked item type (called when shop is deleted/recreated).
     */
    public void resetTypeDetection() {
        typeDetected = false;
        lockedItemType = null;
    }

    /**
     * Returns true if shop type has been detected and locked.
     */
    public boolean isTypeDetected() {
        return typeDetected;
    }

    /**
     * Sets whether this shop's type (stackable/unstackable) has been detected and locked.
     */
    public void setTypeDetected(boolean detected) {
        this.typeDetected = detected;
    }

    /**
     * Returns the locked item type for stackable shops, or null if not locked.
     */
    public Material getLockedItemType() {
        return lockedItemType;
    }

    /**
     * Sets the locked item type for stackable shops.
     */
    public void setLockedItemType(Material itemType) {
        this.lockedItemType = itemType;
    }

    /**
     * Returns the stackable mode for this shop (set manually or from chest).
     * This is the stored flag, not dynamically detected.
     */
    public boolean getShopStackableMode() {
        return isStackable;
    }

    /**
     * Session-only fields for customer UI pagination
     */
    public boolean isOwnerPreviewMode() {
        return ownerPreviewMode;
    }

    public void setOwnerPreviewMode(boolean previewMode) {
        this.ownerPreviewMode = previewMode;
    }

    public int getCurrentPaymentPage() {
        return currentPaymentPage;
    }

    /**
     * Sets the current payment page with wraparound using Math.floorMod().
     * Negative indices wrap correctly (e.g., -1 becomes last page).
     *
     * For multi-payment shops, pages include:
     * - Page 0: Summary (shows "page 1 of N")
     * - Pages 1 to N: Individual payment options (shows "page 2-N of N")
     * Total pages = acceptedPayments.size() + 1
     */
    public void setCurrentPaymentPage(int page) {
        if (acceptedPayments.size() <= 1) {
            this.currentPaymentPage = 0; // Single payment or empty: no pagination
        } else {
            // Multi-payment: wrap at totalPages = payments + 1 (summary + N payments)
            int totalPages = acceptedPayments.size() + 1;
            this.currentPaymentPage = Math.floorMod(page, totalPages);
        }
    }

    /**
     * Advances to the next payment page with wraparound.
     */
    public void incrementPaymentPage() {
        setCurrentPaymentPage(currentPaymentPage + 1);
    }

    /**
     * Resets customer view state (called when shop configuration changes).
     * Resets both owner preview mode and pagination to defaults.
     */
    public void resetCustomerViewState() {
        ownerPreviewMode = false;
        currentPaymentPage = 0;
    }

    public static class Builder {
        private String id;
        private UUID owner;
        private Location signLocation;
        private String group;
        private SignType type;
        private Container container;
        private Container shopContainer;
        private ShopMode mode = ShopMode.SETUP;
        private SignSide signSideDisplayFront;
        private SignSide signSideDisplayBack;

        public Builder id(String id) { this.id = id; return this; }
        public Builder owner(UUID owner) { this.owner = owner; return this; }
        public Builder signLocation(Location signLocation) { this.signLocation = signLocation; return this; }
        public Builder group(String group) { this.group = group; return this; }
        public Builder type(SignType type) { this.type = type; return this; }
        public Builder container(Container container) {
            this.container = container;
            return this;
        }

        public Builder shopContainer(Container container) {
            this.shopContainer = container;
            return this;
        }

        public Builder mode(ShopMode mode) { this.mode = mode; return this; }
        public Builder signSideDisplayFront(SignSide side) {
            this.signSideDisplayFront = side;
            return this;
        }

        public Builder signSideDisplayBack(SignSide side) {
            this.signSideDisplayBack = side;
            return this;
        }

        public BarterSign build() {
            return new BarterSign(this);
        }
    }
}
