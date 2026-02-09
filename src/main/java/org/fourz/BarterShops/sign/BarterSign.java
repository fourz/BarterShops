package org.fourz.BarterShops.sign;

import org.bukkit.Location;
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

    // NEW FIELDS for shop configuration (stackable shops)
    private ItemStack itemOffering;  // The specific item to sell (stackable shops only)
    private ItemStack priceItem;     // The payment item required
    private int priceAmount;         // Quantity of payment required

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

    /**
     * Configures a STACKABLE shop with the item in hand.
     * Called when owner left-clicks in SETUP mode.
     */
    public void configureStackableShop(ItemStack itemInHand, int quantity) {
        this.itemOffering = itemInHand.clone();
        this.itemOffering.setAmount(quantity);
        this.type = SignType.STACKABLE;
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
     */
    public boolean isConfigured() {
        if (type == SignType.STACKABLE) {
            // Stackable requires item offering and price
            return itemOffering != null && priceItem != null && priceAmount > 0;
        } else {
            // Non-stackable requires price only (items in chest)
            return priceItem != null && priceAmount > 0;
        }
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
