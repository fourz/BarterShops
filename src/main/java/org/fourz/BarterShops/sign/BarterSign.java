package org.fourz.BarterShops.sign;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.block.sign.SignSide;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.shop.ShopMode;

import java.util.UUID;

public class BarterSign {
    private final String id;
    private final UUID owner;
    private final Location signLocation;
    private String group;
    private SignType type;
    private Container container;
    private Container shopContainer;
    private ShopMode mode;
    private SignSide signSideDisplayFront;
    private SignSide signSideDisplayBack;

    // NEW FIELDS for shop configuration
    private ItemStack itemOffering;  // The specific item to sell (stackable shops only)
    private ItemStack priceItem;     // The payment item required
    private int priceAmount;         // Quantity of payment required
    private boolean isStackable = true;  // Whether this shop is stackable or unstackable
    private boolean typeDetected = false;  // Whether shop type has been auto-detected (locked)
    private boolean isAdmin = false;  // Admin shops have infinite inventory, no chest needed
    private Material lockedItemType = null;  // For stackable shops: the item type that's locked in

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
     * Note: Shop type (stackable/unstackable) is now auto-detected based on chest contents.
     */
    public void configureStackableShop(ItemStack itemInHand, int quantity) {
        this.itemOffering = itemInHand.clone();
        this.itemOffering.setAmount(quantity);
        // Type is auto-detected at trade time based on chest contents
    }

    /**
     * Configures price for the shop.
     */
    public void configurePrice(ItemStack paymentItem, int amount) {
        this.priceItem = paymentItem.clone();
        this.priceItem.setAmount(1); // Store single item, amount separate
        this.priceAmount = amount;
    }

    /**
     * Checks if shop is fully configured and ready to activate.
     * A shop is configured when it has a price set.
     * The shop type (stackable/unstackable) is auto-detected based on chest contents.
     */
    public boolean isConfigured() {
        // Shop must have price configured
        return priceItem != null && priceAmount > 0;
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
