package org.fourz.BarterShops.data.dto;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.sql.Timestamp;
import java.util.*;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Data Transfer Object for Shop data using Java Record.
 * Immutable and thread-safe for cross-plugin data transfer via RVNKCore.
 *
 * <p>Represents a shop in the BarterShops system with full location
 * and ownership information for ServiceRegistry communication.</p>
 */
public record ShopDataDTO(
    int shopId,
    UUID ownerUuid,
    String shopName,
    ShopType shopType,
    String locationWorld,
    double locationX,
    double locationY,
    double locationZ,
    String chestLocationWorld,
    double chestLocationX,
    double chestLocationY,
    double chestLocationZ,
    boolean isActive,
    Timestamp createdAt,
    Timestamp lastModified,
    Map<String, String> metadata
) {
    /**
     * Compact constructor with validation and defensive copies.
     */
    public ShopDataDTO {
        Objects.requireNonNull(ownerUuid, "ownerUuid cannot be null");

        // Default shopType if null
        if (shopType == null) {
            shopType = ShopType.BARTER;
        }

        // Defensive copy for mutable collection
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * Gets the sign location as a Bukkit Location.
     *
     * @return The shop sign location, or null if world not loaded
     */
    public Location getSignLocation() {
        if (locationWorld == null) return null;
        var world = Bukkit.getWorld(locationWorld);
        if (world == null) return null;
        return new Location(world, locationX, locationY, locationZ);
    }

    /**
     * Gets the chest location as a Bukkit Location.
     *
     * @return The shop chest location, or null if world not loaded
     */
    public Location getChestLocation() {
        if (chestLocationWorld == null) return null;
        var world = Bukkit.getWorld(chestLocationWorld);
        if (world == null) return null;
        return new Location(world, chestLocationX, chestLocationY, chestLocationZ);
    }

    /**
     * Gets the shop ID as a string identifier.
     *
     * @return The shop ID as a string
     */
    public String getShopIdString() {
        return String.valueOf(shopId);
    }

    /**
     * Get configured offering item from metadata.
     *
     * @return ItemStack if configured, null otherwise
     */
    public ItemStack getConfiguredOffering() {
        String json = metadata.get("shop_config_offering");
        if (json == null) return null;
        return org.fourz.BarterShops.data.ShopConfigSerializer.deserializeItemStack(json);
    }

    /**
     * Get configured price item from metadata.
     *
     * @return ItemStack if configured, null otherwise
     */
    public ItemStack getConfiguredPriceItem() {
        String json = metadata.get("shop_config_price_item");
        if (json == null) return null;
        return org.fourz.BarterShops.data.ShopConfigSerializer.deserializeItemStack(json);
    }

    /**
     * Get configured price amount from metadata.
     *
     * @return price amount, 0 if not set
     */
    public int getConfiguredPriceAmount() {
        String amount = metadata.get("shop_config_price_amount");
        if (amount == null) return 0;
        try {
            return Integer.parseInt(amount);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Get locked item type from metadata (for stackable shops).
     *
     * @return Material if set, null otherwise
     */
    public Material getLockedItemType() {
        String typeStr = metadata.get("shop_config_locked_item_type");
        if (typeStr == null || typeStr.isEmpty()) return null;
        try {
            return Material.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Get accepted payment options from metadata (BARTER mode).
     *
     * @return List of ItemStacks, empty if not set
     */
    public List<ItemStack> getAcceptedPayments() {
        String json = metadata.get("shop_config_accepted_payments");
        if (json == null || json.isEmpty()) return List.of();
        return org.fourz.BarterShops.data.ShopConfigSerializer.deserializeItemStackList(json);
    }

    /**
     * Check if shop is stackable type.
     *
     * @return true if stackable, false if unstackable, default true
     */
    public boolean isStackable() {
        String value = metadata.get("shop_config_is_stackable");
        if (value == null) return true;
        return "true".equalsIgnoreCase(value);
    }

    /**
     * Check if shop type has been detected/locked.
     *
     * @return true if type detected and locked
     */
    public boolean isTypeDetected() {
        String value = metadata.get("shop_config_type_detected");
        if (value == null) return false;
        return "true".equalsIgnoreCase(value);
    }

    /**
     * Check if this is an admin shop.
     *
     * @return true if admin shop
     */
    public boolean isAdminShop() {
        String value = metadata.get("shop_config_is_admin");
        if (value == null) return false;
        return "true".equalsIgnoreCase(value);
    }

    /**
     * Builder for constructing ShopDataDTO with optional fields.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Shop type enumeration.
     */
    public enum ShopType {
        /** Standard barter shop - item for item exchange */
        BARTER,
        /** Sell shop - players sell items for currency */
        SELL,
        /** Buy shop - players buy items with currency */
        BUY,
        /** Admin shop - unlimited stock, server-managed */
        ADMIN
    }

    /**
     * Builder class for ShopDataDTO.
     */
    public static class Builder {
        private int shopId;
        private UUID ownerUuid;
        private String shopName;
        private ShopType shopType = ShopType.BARTER;
        private String locationWorld;
        private double locationX;
        private double locationY;
        private double locationZ;
        private String chestLocationWorld;
        private double chestLocationX;
        private double chestLocationY;
        private double chestLocationZ;
        private boolean isActive = true;
        private Timestamp createdAt = new Timestamp(System.currentTimeMillis());
        private Timestamp lastModified = new Timestamp(System.currentTimeMillis());
        private Map<String, String> metadata = Map.of();

        public Builder shopId(int shopId) {
            this.shopId = shopId;
            return this;
        }

        public Builder ownerUuid(UUID ownerUuid) {
            this.ownerUuid = ownerUuid;
            return this;
        }

        public Builder shopName(String shopName) {
            this.shopName = shopName;
            return this;
        }

        public Builder shopType(ShopType shopType) {
            this.shopType = shopType;
            return this;
        }

        public Builder signLocation(Location location) {
            if (location != null && location.getWorld() != null) {
                this.locationWorld = location.getWorld().getName();
                this.locationX = location.getX();
                this.locationY = location.getY();
                this.locationZ = location.getZ();
            }
            return this;
        }

        public Builder signLocation(String world, double x, double y, double z) {
            this.locationWorld = world;
            this.locationX = x;
            this.locationY = y;
            this.locationZ = z;
            return this;
        }

        public Builder chestLocation(Location location) {
            if (location != null && location.getWorld() != null) {
                this.chestLocationWorld = location.getWorld().getName();
                this.chestLocationX = location.getX();
                this.chestLocationY = location.getY();
                this.chestLocationZ = location.getZ();
            }
            return this;
        }

        public Builder chestLocation(String world, double x, double y, double z) {
            this.chestLocationWorld = world;
            this.chestLocationX = x;
            this.chestLocationY = y;
            this.chestLocationZ = z;
            return this;
        }

        public Builder isActive(boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public Builder createdAt(Timestamp createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder lastModified(Timestamp lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = new HashMap<>(metadata != null ? metadata : Map.of());
            return this;
        }

        /**
         * Set configured offering item.
         */
        public Builder configuredOffering(ItemStack item) {
            if (item != null) {
                String json = org.fourz.BarterShops.data.ShopConfigSerializer.serializeItemStack(item);
                if (json != null) {
                    metadata.put("shop_config_offering", json);
                }
            } else {
                metadata.remove("shop_config_offering");
            }
            return this;
        }

        /**
         * Set configured price item and amount.
         */
        public Builder configuredPrice(ItemStack item, int amount) {
            if (item != null) {
                String json = org.fourz.BarterShops.data.ShopConfigSerializer.serializeItemStack(item);
                if (json != null) {
                    metadata.put("shop_config_price_item", json);
                    metadata.put("shop_config_price_amount", String.valueOf(Math.max(0, amount)));
                }
            } else {
                metadata.remove("shop_config_price_item");
                metadata.remove("shop_config_price_amount");
            }
            return this;
        }

        /**
         * Set accepted payment options (BARTER mode).
         */
        public Builder acceptedPayments(List<ItemStack> payments) {
            if (payments != null && !payments.isEmpty()) {
                String json = org.fourz.BarterShops.data.ShopConfigSerializer.serializeItemStackList(payments);
                metadata.put("shop_config_accepted_payments", json);
            } else {
                metadata.remove("shop_config_accepted_payments");
            }
            return this;
        }

        /**
         * Set locked item type for stackable shops.
         */
        public Builder lockedItemType(Material material) {
            if (material != null) {
                metadata.put("shop_config_locked_item_type", material.name());
            } else {
                metadata.remove("shop_config_locked_item_type");
            }
            return this;
        }

        /**
         * Set whether shop is stackable.
         */
        public Builder isStackable(boolean stackable) {
            metadata.put("shop_config_is_stackable", String.valueOf(stackable));
            return this;
        }

        /**
         * Set whether shop type has been detected/locked.
         */
        public Builder typeDetected(boolean detected) {
            metadata.put("shop_config_type_detected", String.valueOf(detected));
            return this;
        }

        /**
         * Set whether this is an admin shop.
         */
        public Builder isAdminShop(boolean admin) {
            metadata.put("shop_config_is_admin", String.valueOf(admin));
            return this;
        }

        public ShopDataDTO build() {
            return new ShopDataDTO(
                shopId, ownerUuid, shopName, shopType,
                locationWorld, locationX, locationY, locationZ,
                chestLocationWorld, chestLocationX, chestLocationY, chestLocationZ,
                isActive, createdAt, lastModified, metadata
            );
        }
    }
}