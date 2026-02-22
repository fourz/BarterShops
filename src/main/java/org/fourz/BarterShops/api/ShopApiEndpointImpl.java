package org.fourz.BarterShops.api;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.dto.TradeRecordDTO;
import org.fourz.BarterShops.service.IShopDatabaseService;
import org.fourz.BarterShops.service.IShopService;
import org.fourz.BarterShops.service.ITradeService;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Implementation of REST API endpoints for BarterShops.
 * Delegates to service layer for business logic and data access.
 *
 * <p>Thread-safe implementation for async Jetty servlet handling.</p>
 */
public class ShopApiEndpointImpl implements ShopApiEndpoint {

    private final IShopService shopService;
    private final ITradeService tradeService;
    private final IShopDatabaseService databaseService;
    private final long startTime;

    /**
     * Creates a new API endpoint implementation.
     *
     * @param shopService Shop service for shop operations
     * @param tradeService Trade service for trade operations
     * @param databaseService Database service for direct queries
     */
    public ShopApiEndpointImpl(
        IShopService shopService,
        ITradeService tradeService,
        IShopDatabaseService databaseService
    ) {
        this.shopService = shopService;
        this.tradeService = tradeService;
        this.databaseService = databaseService;
        this.startTime = System.currentTimeMillis();
    }

    // ========================================================
    // Shop Listing Endpoints
    // ========================================================

    @Override
    public CompletableFuture<ApiResponse<List<ShopDataDTO>>> getShops(Map<String, String> filters) {
        return shopService.getAllShops()
            .thenApply(shops -> {
                // Apply filters
                List<ShopDataDTO> filtered = applyFilters(shops, filters);

                // Apply sorting
                String sortField = filters.getOrDefault("sort", "createdAt");
                String sortOrder = filters.getOrDefault("order", "desc");
                List<ShopDataDTO> sorted = applySorting(filtered, sortField, sortOrder);

                // Apply pagination
                int page = parseIntOrDefault(filters.get("page"), 1);
                int limit = Math.min(parseIntOrDefault(filters.get("limit"), 20), 100);
                List<ShopDataDTO> paginated = applyPagination(sorted, page, limit);

                return ApiResponse.success(paginated, page, limit, sorted.size());
            })
            .exceptionally(ex -> ApiResponse.error(
                "INTERNAL_ERROR",
                "Failed to retrieve shops: " + ex.getMessage()
            ));
    }

    @Override
    public CompletableFuture<ApiResponse<ShopDataDTO>> getShopById(String shopId) {
        return shopService.getShopById(shopId)
            .thenApply(optionalShop -> optionalShop
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(
                    "NOT_FOUND",
                    "Shop with ID " + shopId + " not found"
                ))
            )
            .exceptionally(ex -> ApiResponse.error(
                "INTERNAL_ERROR",
                "Failed to retrieve shop: " + ex.getMessage()
            ));
    }

    @Override
    public CompletableFuture<ApiResponse<List<ShopDataDTO>>> getShopsNearby(
        String world, double x, double y, double z, double radius
    ) {
        // Validate parameters
        if (world == null || world.isEmpty()) {
            return CompletableFuture.completedFuture(
                ApiResponse.error("INVALID_REQUEST", "World parameter is required")
            );
        }

        if (Bukkit.getWorld(world) == null) {
            return CompletableFuture.completedFuture(
                ApiResponse.error("NOT_FOUND", "World '" + world + "' not found")
            );
        }

        // Clamp radius to maximum
        double effectiveRadius = Math.min(radius, 500.0);

        Location center = new Location(Bukkit.getWorld(world), x, y, z);

        return shopService.getShopsNearby(center, effectiveRadius)
            .thenApply(ApiResponse::success)
            .exceptionally(ex -> ApiResponse.error(
                "INTERNAL_ERROR",
                "Failed to find nearby shops: " + ex.getMessage()
            ));
    }

    // ========================================================
    // Trade Activity Endpoints
    // ========================================================

    @Override
    public CompletableFuture<ApiResponse<List<TradeRecordDTO>>> getRecentTrades(
        int limit, String shopId, UUID playerUuid
    ) {
        // Clamp limit to maximum
        int effectiveLimit = Math.min(limit, 100);

        CompletableFuture<List<TradeRecordDTO>> futureTrades;

        if (shopId != null && !shopId.isEmpty()) {
            // Filter by shop
            futureTrades = tradeService.getShopTradeHistory(shopId, effectiveLimit);
        } else if (playerUuid != null) {
            // Filter by player
            futureTrades = tradeService.getTradeHistory(playerUuid, effectiveLimit);
        } else {
            // Get all recent trades (requires database service)
            futureTrades = databaseService.getRecentTrades(effectiveLimit);
        }

        return futureTrades
            .thenApply(ApiResponse::success)
            .exceptionally(ex -> ApiResponse.error(
                "INTERNAL_ERROR",
                "Failed to retrieve trade history: " + ex.getMessage()
            ));
    }

    @Override
    public CompletableFuture<ApiResponse<TradeRecordDTO>> getTradeById(String transactionId) {
        if (transactionId == null || transactionId.isEmpty()) {
            return CompletableFuture.completedFuture(
                ApiResponse.error("INVALID_REQUEST", "Transaction ID is required")
            );
        }

        return databaseService.getTradeByTransactionId(transactionId)
            .thenApply(optionalTrade -> optionalTrade
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(
                    "NOT_FOUND",
                    "Trade with ID " + transactionId + " not found"
                ))
            )
            .exceptionally(ex -> ApiResponse.error(
                "INTERNAL_ERROR",
                "Failed to retrieve trade: " + ex.getMessage()
            ));
    }

    // ========================================================
    // Statistics Endpoints
    // ========================================================

    @Override
    public CompletableFuture<ApiResponse<Map<String, Object>>> getServerStats() {
        CompletableFuture<Integer> shopCountFuture = shopService.getShopCount();
        CompletableFuture<Long> tradeCountFuture = tradeService.getTotalTradeCount();
        CompletableFuture<List<ShopDataDTO>> allShopsFuture = shopService.getAllShops();

        return CompletableFuture.allOf(shopCountFuture, tradeCountFuture, allShopsFuture)
            .thenApply(v -> {
                int totalShops = shopCountFuture.join();
                long totalTrades = tradeCountFuture.join();
                List<ShopDataDTO> allShops = allShopsFuture.join();

                // Calculate shop type breakdown
                Map<String, Long> shopsByType = allShops.stream()
                    .collect(Collectors.groupingBy(
                        shop -> shop.shopType().name(),
                        Collectors.counting()
                    ));

                Map<String, Object> stats = new HashMap<>();
                stats.put("totalShops", totalShops);
                stats.put("totalTrades", totalTrades);
                stats.put("shopsByType", shopsByType);
                stats.put("activeShops", allShops.stream().filter(ShopDataDTO::isActive).count());

                return ApiResponse.success(stats);
            })
            .exceptionally(ex -> ApiResponse.error(
                "INTERNAL_ERROR",
                "Failed to retrieve statistics: " + ex.getMessage()
            ));
    }

    @Override
    public CompletableFuture<ApiResponse<Map<String, Object>>> getShopStats(String shopId) {
        if (shopId == null || shopId.isEmpty()) {
            // Return global shop statistics
            return getServerStats();
        }

        // Get specific shop statistics
        return shopService.getShopById(shopId)
            .thenCompose(optionalShop -> {
                if (optionalShop.isEmpty()) {
                    return CompletableFuture.completedFuture(
                        ApiResponse.<Map<String, Object>>error(
                            "NOT_FOUND",
                            "Shop with ID " + shopId + " not found"
                        )
                    );
                }

                ShopDataDTO shop = optionalShop.get();

                return tradeService.getShopTradeHistory(shopId, 100)
                    .thenApply(trades -> {
                        Map<String, Object> stats = new HashMap<>();
                        stats.put("shopId", shopId);
                        stats.put("shopName", shop.shopName());
                        stats.put("ownerUuid", shop.ownerUuid().toString());
                        stats.put("totalTrades", trades.size());
                        stats.put("isActive", shop.isActive());
                        stats.put("createdAt", shop.createdAt().toString());

                        return ApiResponse.success(stats);
                    });
            })
            .exceptionally(ex -> ApiResponse.error(
                "INTERNAL_ERROR",
                "Failed to retrieve shop statistics: " + ex.getMessage()
            ));
    }

    // ========================================================
    // Health Check Endpoint
    // ========================================================

    @Override
    public CompletableFuture<ApiResponse<Map<String, Object>>> getHealthStatus() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> health = new HashMap<>();

            boolean fallbackMode = shopService.isInFallbackMode() || tradeService.isInFallbackMode();
            String status = fallbackMode ? "degraded" : "healthy";

            health.put("status", status);
            health.put("fallbackMode", fallbackMode);
            health.put("database", !fallbackMode ? "connected" : "fallback");
            health.put("uptime", System.currentTimeMillis() - startTime);
            health.put("timestamp", java.time.Instant.now().toString());

            return ApiResponse.success(health);
        });
    }

    // ========================================================
    // Helper Methods
    // ========================================================

    /**
     * Applies filters to shop list.
     */
    private List<ShopDataDTO> applyFilters(List<ShopDataDTO> shops, Map<String, String> filters) {
        List<ShopDataDTO> result = new ArrayList<>(shops);

        // Filter by owner
        String ownerFilter = filters.get("owner");
        if (ownerFilter != null && !ownerFilter.isEmpty()) {
            try {
                UUID ownerUuid = UUID.fromString(ownerFilter);
                result = result.stream()
                    .filter(shop -> shop.ownerUuid().equals(ownerUuid))
                    .collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                // Invalid UUID, skip filter
            }
        }

        // Filter by type
        String typeFilter = filters.get("type");
        if (typeFilter != null && !typeFilter.isEmpty()) {
            try {
                ShopDataDTO.ShopType shopType = ShopDataDTO.ShopType.valueOf(typeFilter.toUpperCase());
                result = result.stream()
                    .filter(shop -> shop.shopType() == shopType)
                    .collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                // Invalid shop type, skip filter
            }
        }

        // Filter by world
        String worldFilter = filters.get("world");
        if (worldFilter != null && !worldFilter.isEmpty()) {
            result = result.stream()
                .filter(shop -> worldFilter.equalsIgnoreCase(shop.locationWorld()))
                .collect(Collectors.toList());
        }

        return result;
    }

    /**
     * Applies sorting to shop list.
     */
    private List<ShopDataDTO> applySorting(List<ShopDataDTO> shops, String sortField, String sortOrder) {
        List<ShopDataDTO> sorted = new ArrayList<>(shops);

        Comparator<ShopDataDTO> comparator = switch (sortField) {
            case "name" -> Comparator.comparing(ShopDataDTO::shopName, Comparator.nullsLast(String::compareTo));
            case "owner" -> Comparator.comparing(shop -> shop.ownerUuid().toString());
            case "type" -> Comparator.comparing(shop -> shop.shopType().name());
            default -> Comparator.comparing(ShopDataDTO::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };

        if ("asc".equalsIgnoreCase(sortOrder)) {
            sorted.sort(comparator);
        } else {
            sorted.sort(comparator.reversed());
        }

        return sorted;
    }

    /**
     * Applies pagination to shop list.
     */
    private List<ShopDataDTO> applyPagination(List<ShopDataDTO> shops, int page, int limit) {
        int startIndex = (page - 1) * limit;
        int endIndex = Math.min(startIndex + limit, shops.size());

        if (startIndex >= shops.size()) {
            return List.of();
        }

        return shops.subList(startIndex, endIndex);
    }

    /**
     * Parses an integer from a string, returning default value on error.
     */
    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
