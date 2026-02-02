package org.fourz.BarterShops.api;

import org.bukkit.Location;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.dto.TradeRecordDTO;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST API endpoint interface for BarterShops web integration.
 * Provides HTTP-based access to shop listings, trade activity, and server statistics.
 *
 * <p>API Design follows RVNKCore REST API Standards:</p>
 * <ul>
 *     <li>Resource-based URLs: /api/shops, /api/shops/{id}</li>
 *     <li>JSON response format with data/meta structure</li>
 *     <li>Async operations with CompletableFuture</li>
 *     <li>CORS support for web clients</li>
 * </ul>
 *
 * <p>All endpoints return CompletableFuture for non-blocking execution on Jetty threads.</p>
 *
 * @see org.fourz.BarterShops.api.ShopApiEndpointImpl
 */
public interface ShopApiEndpoint {

    // ========================================================
    // Shop Listing Endpoints
    // ========================================================

    /**
     * GET /api/shops - List all active shops with optional filters.
     *
     * <p>Query Parameters:</p>
     * <ul>
     *     <li>owner - Filter by owner UUID</li>
     *     <li>type - Filter by shop type (BARTER, SELL, BUY, ADMIN)</li>
     *     <li>world - Filter by world name</li>
     *     <li>page - Page number (default: 1)</li>
     *     <li>limit - Items per page (default: 20, max: 100)</li>
     *     <li>sort - Sort field (default: createdAt)</li>
     *     <li>order - Sort order (asc/desc, default: desc)</li>
     * </ul>
     *
     * @param filters Query parameter filters
     * @return CompletableFuture containing API response with shop list
     */
    CompletableFuture<ApiResponse<List<ShopDataDTO>>> getShops(Map<String, String> filters);

    /**
     * GET /api/shops/{id} - Get shop details by ID.
     *
     * @param shopId The shop ID
     * @return CompletableFuture containing API response with shop data
     */
    CompletableFuture<ApiResponse<ShopDataDTO>> getShopById(String shopId);

    /**
     * GET /api/shops/nearby - Get shops near coordinates.
     *
     * <p>Query Parameters:</p>
     * <ul>
     *     <li>world - World name (required)</li>
     *     <li>x - X coordinate (required)</li>
     *     <li>y - Y coordinate (required)</li>
     *     <li>z - Z coordinate (required)</li>
     *     <li>radius - Search radius in blocks (default: 50, max: 500)</li>
     * </ul>
     *
     * @param world World name
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param radius Search radius
     * @return CompletableFuture containing API response with nearby shops
     */
    CompletableFuture<ApiResponse<List<ShopDataDTO>>> getShopsNearby(
        String world, double x, double y, double z, double radius
    );

    // ========================================================
    // Trade Activity Endpoints
    // ========================================================

    /**
     * GET /api/trades/recent - Get recent trade activity.
     *
     * <p>Query Parameters:</p>
     * <ul>
     *     <li>limit - Number of trades to return (default: 20, max: 100)</li>
     *     <li>shop - Filter by shop ID</li>
     *     <li>player - Filter by player UUID</li>
     * </ul>
     *
     * @param limit Maximum number of trades
     * @param shopId Optional shop ID filter
     * @param playerUuid Optional player UUID filter
     * @return CompletableFuture containing API response with trade records
     */
    CompletableFuture<ApiResponse<List<TradeRecordDTO>>> getRecentTrades(
        int limit, String shopId, UUID playerUuid
    );

    /**
     * GET /api/trades/{id} - Get trade details by transaction ID.
     *
     * @param transactionId The transaction ID
     * @return CompletableFuture containing API response with trade details
     */
    CompletableFuture<ApiResponse<TradeRecordDTO>> getTradeById(String transactionId);

    // ========================================================
    // Statistics Endpoints
    // ========================================================

    /**
     * GET /api/stats - Get server-wide shop and trade statistics.
     *
     * <p>Response includes:</p>
     * <ul>
     *     <li>totalShops - Total number of active shops</li>
     *     <li>totalTrades - Total completed trades</li>
     *     <li>shopsByType - Breakdown by shop type</li>
     *     <li>tradesByDay - Recent trade activity by day</li>
     *     <li>topShops - Most active shops by trade count</li>
     * </ul>
     *
     * @return CompletableFuture containing API response with statistics
     */
    CompletableFuture<ApiResponse<Map<String, Object>>> getServerStats();

    /**
     * GET /api/stats/shops - Get shop-specific statistics.
     *
     * @param shopId Optional shop ID filter
     * @return CompletableFuture containing API response with shop statistics
     */
    CompletableFuture<ApiResponse<Map<String, Object>>> getShopStats(String shopId);

    // ========================================================
    // Health Check Endpoint
    // ========================================================

    /**
     * GET /api/health - Health check endpoint for monitoring.
     *
     * <p>Response includes:</p>
     * <ul>
     *     <li>status - "healthy" or "degraded"</li>
     *     <li>database - Database connection status</li>
     *     <li>fallbackMode - Whether database fallback is active</li>
     *     <li>uptime - API server uptime in milliseconds</li>
     * </ul>
     *
     * @return CompletableFuture containing API response with health status
     */
    CompletableFuture<ApiResponse<Map<String, Object>>> getHealthStatus();

    // ========================================================
    // API Response Record
    // ========================================================

    /**
     * Standard API response format following RVNKCore REST API Standards.
     *
     * @param <T> The data type
     */
    record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error,
        ApiMeta meta
    ) {
        /**
         * Creates a successful response.
         */
        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(
                true,
                data,
                null,
                ApiMeta.create()
            );
        }

        /**
         * Creates a successful paginated response.
         */
        public static <T> ApiResponse<T> success(T data, int page, int limit, int totalItems) {
            return new ApiResponse<>(
                true,
                data,
                null,
                ApiMeta.createPaginated(page, limit, totalItems)
            );
        }

        /**
         * Creates an error response.
         */
        public static <T> ApiResponse<T> error(String code, String message) {
            return new ApiResponse<>(
                false,
                null,
                new ApiError(code, message, null),
                ApiMeta.create()
            );
        }

        /**
         * Creates an error response with details.
         */
        public static <T> ApiResponse<T> error(String code, String message, List<String> details) {
            return new ApiResponse<>(
                false,
                null,
                new ApiError(code, message, details),
                ApiMeta.create()
            );
        }
    }

    /**
     * API error details.
     */
    record ApiError(
        String code,
        String message,
        List<String> details
    ) {
        public ApiError {
            details = details == null ? List.of() : List.copyOf(details);
        }
    }

    /**
     * API response metadata.
     */
    record ApiMeta(
        String timestamp,
        String version,
        Integer page,
        Integer limit,
        Integer totalItems,
        Integer totalPages
    ) {
        /**
         * Creates basic metadata.
         */
        public static ApiMeta create() {
            return new ApiMeta(
                java.time.Instant.now().toString(),
                "1.0",
                null,
                null,
                null,
                null
            );
        }

        /**
         * Creates paginated metadata.
         */
        public static ApiMeta createPaginated(int page, int limit, int totalItems) {
            int totalPages = (int) Math.ceil((double) totalItems / limit);
            return new ApiMeta(
                java.time.Instant.now().toString(),
                "1.0",
                page,
                limit,
                totalItems,
                totalPages
            );
        }
    }
}
