package org.fourz.BarterShops.data.repository;

import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.dto.TradeItemDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for shop data access.
 * Follows the Repository pattern for data access abstraction.
 *
 * <p>All methods return CompletableFuture for async database operations.</p>
 * <p>Implementations should use FallbackTracker for graceful degradation.</p>
 */
public interface IShopRepository {

    // ========================================================
    // Shop CRUD Operations
    // ========================================================

    /**
     * Saves a new shop or updates an existing one.
     *
     * @param shop The shop data to save
     * @return CompletableFuture containing the saved shop with generated ID
     */
    CompletableFuture<ShopDataDTO> save(ShopDataDTO shop);

    /**
     * Finds a shop by its unique ID.
     *
     * @param shopId The shop ID to find
     * @return CompletableFuture containing the shop, or empty if not found
     */
    CompletableFuture<Optional<ShopDataDTO>> findById(int shopId);

    /**
     * Finds a shop by its string identifier.
     *
     * @param shopIdString The shop ID as string
     * @return CompletableFuture containing the shop, or empty if not found
     */
    default CompletableFuture<Optional<ShopDataDTO>> findById(String shopIdString) {
        try {
            return findById(Integer.parseInt(shopIdString));
        } catch (NumberFormatException e) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    /**
     * Deletes a shop by its ID.
     *
     * @param shopId The shop ID to delete
     * @return CompletableFuture with true if deleted, false if not found
     */
    CompletableFuture<Boolean> deleteById(int shopId);

    /**
     * Checks if a shop exists.
     *
     * @param shopId The shop ID to check
     * @return CompletableFuture with true if exists
     */
    CompletableFuture<Boolean> existsById(int shopId);

    // ========================================================
    // Shop Queries
    // ========================================================

    /**
     * Finds all shops owned by a player.
     *
     * @param ownerUuid The owner's UUID
     * @return CompletableFuture containing list of owned shops
     */
    CompletableFuture<List<ShopDataDTO>> findByOwner(UUID ownerUuid);

    /**
     * Finds a shop at a specific sign location.
     *
     * @param world The world name
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return CompletableFuture containing the shop, or empty if not found
     */
    CompletableFuture<Optional<ShopDataDTO>> findBySignLocation(
            String world, double x, double y, double z);

    /**
     * Finds shops within a radius of a location.
     *
     * @param world The world name
     * @param x Center X coordinate
     * @param y Center Y coordinate
     * @param z Center Z coordinate
     * @param radius The search radius in blocks
     * @return CompletableFuture containing list of nearby shops
     */
    CompletableFuture<List<ShopDataDTO>> findNearby(
            String world, double x, double y, double z, double radius);

    /**
     * Finds all active shops.
     *
     * @return CompletableFuture containing list of all active shops
     */
    CompletableFuture<List<ShopDataDTO>> findAllActive();

    /**
     * Finds all shops (including inactive).
     *
     * @return CompletableFuture containing list of all shops
     */
    CompletableFuture<List<ShopDataDTO>> findAll();

    // ========================================================
    // Trade Items (Shop Inventory)
    // ========================================================

    /**
     * Gets all trade items for a shop.
     *
     * @param shopId The shop ID
     * @return CompletableFuture containing list of trade items
     */
    CompletableFuture<List<TradeItemDTO>> findTradeItems(int shopId);

    /**
     * Saves a trade item configuration.
     *
     * @param item The trade item to save
     * @return CompletableFuture containing the saved item with generated ID
     */
    CompletableFuture<TradeItemDTO> saveTradeItem(TradeItemDTO item);

    /**
     * Deletes a trade item by ID.
     *
     * @param tradeItemId The trade item ID to delete
     * @return CompletableFuture with true if deleted
     */
    CompletableFuture<Boolean> deleteTradeItem(int tradeItemId);

    /**
     * Updates stock quantity for a trade item.
     *
     * @param tradeItemId The trade item ID
     * @param newQuantity The new stock quantity
     * @return CompletableFuture with true if updated
     */
    CompletableFuture<Boolean> updateStock(int tradeItemId, int newQuantity);

    // ========================================================
    // Statistics
    // ========================================================

    /**
     * Gets the total count of shops.
     *
     * @return CompletableFuture containing the shop count
     */
    CompletableFuture<Integer> count();

    /**
     * Gets the count of shops owned by a player.
     *
     * @param ownerUuid The owner's UUID
     * @return CompletableFuture containing the owner's shop count
     */
    CompletableFuture<Integer> countByOwner(UUID ownerUuid);

    // ========================================================
    // Metadata Operations
    // ========================================================

    /**
     * Gets metadata value for a shop.
     *
     * @param shopId The shop ID
     * @param key The metadata key
     * @return CompletableFuture containing the value, or empty if not found
     */
    CompletableFuture<Optional<String>> getMetadata(int shopId, String key);

    /**
     * Sets metadata value for a shop.
     *
     * @param shopId The shop ID
     * @param key The metadata key
     * @param value The metadata value
     * @return CompletableFuture with true if saved
     */
    CompletableFuture<Boolean> setMetadata(int shopId, String key, String value);

    /**
     * Removes metadata from a shop.
     *
     * @param shopId The shop ID
     * @param key The metadata key to remove
     * @return CompletableFuture with true if removed
     */
    CompletableFuture<Boolean> removeMetadata(int shopId, String key);
}
