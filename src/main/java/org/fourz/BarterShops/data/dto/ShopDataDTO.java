package org.fourz.BarterShops.data.dto;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.sql.Timestamp;
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
            this.metadata = metadata;
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