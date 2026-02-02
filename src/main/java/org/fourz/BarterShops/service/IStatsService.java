package org.fourz.BarterShops.service;

import org.fourz.BarterShops.data.dto.StatsDataDTO;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for shop and player statistics.
 * Exposes analytics and statistics for cross-plugin access via RVNKCore ServiceRegistry.
 *
 * <p>Register with RVNKCore ServiceRegistry for use by other plugins:</p>
 * <pre>
 * ServiceRegistry.registerService(IStatsService.class, statsService);
 * </pre>
 *
 * <p>All async methods return CompletableFuture for non-blocking operations.</p>
 */
public interface IStatsService {

    // ========================================================
    // Player Statistics
    // ========================================================

    /**
     * Gets comprehensive statistics for a player.
     *
     * @param playerUuid The UUID of the player
     * @return CompletableFuture containing player statistics
     */
    CompletableFuture<StatsDataDTO> getPlayerStats(UUID playerUuid);

    /**
     * Gets the number of shops owned by a player.
     *
     * @param playerUuid The UUID of the player
     * @return CompletableFuture containing the shop count
     */
    CompletableFuture<Integer> getPlayerShopCount(UUID playerUuid);

    /**
     * Gets the number of trades completed by a player.
     *
     * @param playerUuid The UUID of the player
     * @return CompletableFuture containing the trade count
     */
    CompletableFuture<Integer> getPlayerTradeCount(UUID playerUuid);

    /**
     * Gets the average rating of shops owned by a player.
     *
     * @param playerUuid The UUID of the player
     * @return CompletableFuture containing the average rating (0.0-5.0)
     */
    CompletableFuture<Double> getPlayerAverageRating(UUID playerUuid);

    // ========================================================
    // Server Statistics
    // ========================================================

    /**
     * Gets comprehensive server-wide statistics.
     *
     * @return CompletableFuture containing server statistics
     */
    CompletableFuture<StatsDataDTO> getServerStats();

    /**
     * Gets the total number of active shops on the server.
     *
     * @return CompletableFuture containing the active shop count
     */
    CompletableFuture<Integer> getActiveShopCount();

    /**
     * Gets the total number of trades completed on the server.
     *
     * @return CompletableFuture containing the total trade count
     */
    CompletableFuture<Integer> getTotalTradeCount();

    /**
     * Gets the total number of items traded on the server.
     *
     * @return CompletableFuture containing the total item count
     */
    CompletableFuture<Integer> getTotalItemsTraded();

    /**
     * Gets the average number of trades per shop.
     *
     * @return CompletableFuture containing the average trade count
     */
    CompletableFuture<Double> getAverageTradesPerShop();

    // ========================================================
    // Shop Statistics
    // ========================================================

    /**
     * Gets statistics for a specific shop.
     *
     * @param shopId The unique shop ID
     * @return CompletableFuture containing shop statistics
     */
    CompletableFuture<StatsDataDTO> getShopStats(String shopId);

    /**
     * Gets the number of trades completed at a specific shop.
     *
     * @param shopId The unique shop ID
     * @return CompletableFuture containing the trade count
     */
    CompletableFuture<Integer> getShopTradeCount(String shopId);

    // ========================================================
    // Cache Management
    // ========================================================

    /**
     * Refreshes the statistics cache.
     * Should be called after major data changes to ensure accuracy.
     *
     * @return CompletableFuture that completes when cache is refreshed
     */
    CompletableFuture<Void> refreshCache();

    /**
     * Clears the statistics cache.
     * Forces all subsequent requests to recalculate statistics.
     */
    void clearCache();

    /**
     * Checks if the cache is enabled.
     *
     * @return true if caching is enabled
     */
    boolean isCacheEnabled();

    /**
     * Sets whether caching is enabled.
     *
     * @param enabled true to enable caching
     */
    void setCacheEnabled(boolean enabled);

    // ========================================================
    // Leaderboards
    // ========================================================

    /**
     * Gets the top shops by trade volume.
     *
     * @param limit Maximum number of shops to return
     * @return CompletableFuture containing list of top shops
     */
    CompletableFuture<java.util.List<StatsDataDTO.TopShop>> getTopShopsByTrades(int limit);

    /**
     * Gets the top shops by rating.
     *
     * @param limit Maximum number of shops to return
     * @return CompletableFuture containing list of top shops
     */
    CompletableFuture<java.util.List<StatsDataDTO.TopShop>> getTopShopsByRating(int limit);

    /**
     * Gets the most traded items across all shops.
     *
     * @param limit Maximum number of items to return
     * @return CompletableFuture containing map of item names to trade counts
     */
    CompletableFuture<java.util.Map<String, Integer>> getMostTradedItems(int limit);
}
