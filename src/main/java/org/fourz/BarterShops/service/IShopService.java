package org.fourz.BarterShops.service;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.fourz.BarterShops.data.dto.ShopDataDTO;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for shop operations.
 * Exposes shop management for cross-plugin access via RVNKCore ServiceRegistry.
 *
 * <p>Register with RVNKCore ServiceRegistry for use by other plugins:</p>
 * <pre>
 * ServiceRegistry.registerService(IShopService.class, shopManager);
 * </pre>
 *
 * <p>All async methods return CompletableFuture for non-blocking operations.</p>
 */
public interface IShopService {

    // ========================================================
    // Shop Queries
    // ========================================================

    /**
     * Gets a shop by its unique identifier.
     *
     * @param shopId The unique shop ID
     * @return CompletableFuture containing the shop data, or empty if not found
     */
    CompletableFuture<Optional<ShopDataDTO>> getShopById(String shopId);

    /**
     * Gets a shop at a specific location.
     *
     * @param location The world location to check
     * @return CompletableFuture containing the shop data, or empty if no shop exists
     */
    CompletableFuture<Optional<ShopDataDTO>> getShopAtLocation(Location location);

    /**
     * Gets a shop by its sign block.
     *
     * @param signBlock The sign block associated with the shop
     * @return CompletableFuture containing the shop data, or empty if not a shop sign
     */
    CompletableFuture<Optional<ShopDataDTO>> getShopBySign(Block signBlock);

    /**
     * Gets all shops owned by a player.
     *
     * @param ownerUuid The UUID of the shop owner
     * @return CompletableFuture containing list of shops owned by the player
     */
    CompletableFuture<List<ShopDataDTO>> getShopsByOwner(UUID ownerUuid);

    /**
     * Gets all active shops.
     *
     * @return CompletableFuture containing list of all active shops
     */
    CompletableFuture<List<ShopDataDTO>> getAllShops();

    /**
     * Gets shops within a radius of a location.
     *
     * @param center The center location
     * @param radius The search radius in blocks
     * @return CompletableFuture containing list of shops within the radius
     */
    CompletableFuture<List<ShopDataDTO>> getShopsNearby(Location center, double radius);

    // ========================================================
    // Shop Management
    // ========================================================

    /**
     * Creates a new shop.
     *
     * @param ownerUuid The UUID of the shop owner
     * @param location The location of the shop sign
     * @param shopName Optional name for the shop
     * @return CompletableFuture containing the created shop data
     */
    CompletableFuture<ShopDataDTO> createShop(UUID ownerUuid, Location location, String shopName);

    /**
     * Removes a shop.
     *
     * @param shopId The unique shop ID to remove
     * @return CompletableFuture with true if removed, false if not found
     */
    CompletableFuture<Boolean> removeShop(String shopId);

    /**
     * Updates a shop's configuration.
     *
     * @param shopId The shop ID to update
     * @param updates The updates to apply
     * @return CompletableFuture with true if updated successfully
     */
    CompletableFuture<Boolean> updateShop(String shopId, ShopUpdateRequest updates);

    // ========================================================
    // Shop Statistics
    // ========================================================

    /**
     * Gets the total number of shops.
     *
     * @return CompletableFuture containing the shop count
     */
    CompletableFuture<Integer> getShopCount();

    /**
     * Gets the number of shops owned by a player.
     *
     * @param ownerUuid The UUID of the shop owner
     * @return CompletableFuture containing the shop count for the player
     */
    CompletableFuture<Integer> getShopCountByOwner(UUID ownerUuid);

    // ========================================================
    // Fallback Mode
    // ========================================================

    /**
     * Checks if the service is operating in fallback mode.
     * In fallback mode, database operations are unavailable but in-memory
     * operations may still work.
     *
     * @return true if in fallback mode, false if normal operation
     */
    boolean isInFallbackMode();

    // ========================================================
    // Update Request Record
    // ========================================================

    /**
     * Represents a shop update request using Java Record.
     * All fields are optional - only non-null values are applied.
     */
    record ShopUpdateRequest(
        String newName,
        Boolean active,
        ShopDataDTO.ShopType shopType,
        Location chestLocation,
        Map<String, String> metadataUpdates
    ) {
        /**
         * Creates an update request with just a name change.
         */
        public static ShopUpdateRequest nameOnly(String newName) {
            return new ShopUpdateRequest(newName, null, null, null, null);
        }

        /**
         * Creates an update request with just an active state change.
         */
        public static ShopUpdateRequest activeOnly(boolean active) {
            return new ShopUpdateRequest(null, active, null, null, null);
        }

        /**
         * Builder for constructing ShopUpdateRequest.
         */
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String newName;
            private Boolean active;
            private ShopDataDTO.ShopType shopType;
            private Location chestLocation;
            private Map<String, String> metadataUpdates;

            public Builder newName(String newName) {
                this.newName = newName;
                return this;
            }

            public Builder active(Boolean active) {
                this.active = active;
                return this;
            }

            public Builder shopType(ShopDataDTO.ShopType shopType) {
                this.shopType = shopType;
                return this;
            }

            public Builder chestLocation(Location chestLocation) {
                this.chestLocation = chestLocation;
                return this;
            }

            public Builder metadataUpdates(Map<String, String> metadataUpdates) {
                this.metadataUpdates = metadataUpdates;
                return this;
            }

            public ShopUpdateRequest build() {
                return new ShopUpdateRequest(
                    newName, active, shopType, chestLocation, metadataUpdates
                );
            }
        }
    }
}
