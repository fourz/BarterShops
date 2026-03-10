package org.fourz.BarterShops.api;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.dto.TradeRecordDTO;
import org.fourz.BarterShops.service.IShopDatabaseService;
import org.fourz.BarterShops.service.IShopService;
import org.fourz.BarterShops.service.ITradeService;
import org.fourz.rvnkcore.api.model.response.ApiResponse;
import org.fourz.rvnkcore.api.service.IBarterShopsApiService;
import org.fourz.rvnkcore.api.util.ApiUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Implementation of REST API endpoints for BarterShops.
 * Implements {@link IBarterShopsApiService} for registration with RVNKCore ServiceRegistry,
 * allowing the BarterShopsController in RVNKCore to route requests here.
 */
public class ShopApiEndpointImpl implements IBarterShopsApiService {

    private final IShopService shopService;
    private final ITradeService tradeService;
    private final IShopDatabaseService databaseService;
    private final long startTime;

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

    @Override
    public CompletableFuture<ApiResponse<?>> getShops(Map<String, String> filters) {
        return shopService.getAllShops()
            .<ApiResponse<?>>handle((shops, ex) -> {
                if (ex != null) return ApiResponse.error("INTERNAL_ERROR",
                    "Failed to retrieve shops: " + ex.getMessage());
                List<ShopDataDTO> filtered = applyFilters(shops, filters);
                String sortField = filters.getOrDefault("sort", "createdAt");
                String sortOrder = filters.getOrDefault("order", "desc");
                List<ShopDataDTO> sorted = applySorting(filtered, sortField, sortOrder);
                int page = ApiUtils.parseIntOrDefault(filters.get("page"), 1);
                int limit = Math.min(ApiUtils.parseIntOrDefault(filters.get("limit"), 20), 100);
                List<ShopDataDTO> paginated = applyPagination(sorted, page, limit);
                return ApiResponse.success(paginated, page, limit, sorted.size());
            });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getShopById(String shopId) {
        return shopService.getShopById(shopId)
            .<ApiResponse<?>>handle((optionalShop, ex) -> {
                if (ex != null) return ApiResponse.error("INTERNAL_ERROR",
                    "Failed to retrieve shop: " + ex.getMessage());
                return optionalShop
                    .map(ApiResponse::success)
                    .orElse(ApiResponse.error("NOT_FOUND",
                        "Shop with ID " + shopId + " not found"));
            });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getShopsNearby(
        String world, double x, double y, double z, double radius
    ) {
        if (world == null || world.isEmpty()) {
            return CompletableFuture.completedFuture(
                ApiResponse.error("INVALID_REQUEST", "World parameter is required"));
        }

        double effectiveRadius = Math.min(radius, 500.0);

        return shopService.getShopsNearby(world, x, y, z, effectiveRadius)
            .<ApiResponse<?>>handle((shops, ex) -> {
                if (ex != null) return ApiResponse.error("INTERNAL_ERROR",
                    "Failed to find nearby shops: " + ex.getMessage());
                return ApiResponse.success(shops);
            });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getRecentTrades(
        int limit, String shopId, String playerUuidStr
    ) {
        int effectiveLimit = Math.min(limit, 100);

        CompletableFuture<List<TradeRecordDTO>> futureTrades;

        if (shopId != null && !shopId.isEmpty()) {
            futureTrades = tradeService.getShopTradeHistory(shopId, effectiveLimit);
        } else if (playerUuidStr != null && !playerUuidStr.isEmpty()) {
            try {
                UUID playerUuid = UUID.fromString(playerUuidStr);
                futureTrades = tradeService.getTradeHistory(playerUuid, effectiveLimit);
            } catch (IllegalArgumentException e) {
                return CompletableFuture.completedFuture(
                    ApiResponse.error("INVALID_REQUEST", "Invalid player UUID format"));
            }
        } else {
            futureTrades = tradeService.getRecentTrades(effectiveLimit);
        }

        return futureTrades
            .<ApiResponse<?>>handle((trades, ex) -> {
                if (ex != null) return ApiResponse.error("INTERNAL_ERROR",
                    "Failed to retrieve trade history: " + ex.getMessage());
                return ApiResponse.success(trades);
            });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getTradeById(String transactionId) {
        if (transactionId == null || transactionId.isEmpty()) {
            return CompletableFuture.completedFuture(
                ApiResponse.error("INVALID_REQUEST", "Transaction ID is required"));
        }

        return tradeService.getTradeByTransactionId(transactionId)
            .<ApiResponse<?>>handle((optionalTrade, ex) -> {
                if (ex != null) return ApiResponse.error("INTERNAL_ERROR",
                    "Failed to retrieve trade: " + ex.getMessage());
                return optionalTrade
                    .map(ApiResponse::success)
                    .orElse(ApiResponse.error("NOT_FOUND",
                        "Trade with ID " + transactionId + " not found"));
            });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getServerStats() {
        CompletableFuture<Integer> shopCountFuture = shopService.getShopCount();
        CompletableFuture<Long> tradeCountFuture = tradeService.getTotalTradeCount();
        CompletableFuture<List<ShopDataDTO>> allShopsFuture = shopService.getAllShops();

        return CompletableFuture.allOf(shopCountFuture, tradeCountFuture, allShopsFuture)
            .<ApiResponse<?>>handle((v, ex) -> {
                if (ex != null) return ApiResponse.error("INTERNAL_ERROR",
                    "Failed to retrieve statistics: " + ex.getMessage());

                int totalShops = shopCountFuture.join();
                long totalTrades = tradeCountFuture.join();
                List<ShopDataDTO> allShops = allShopsFuture.join();

                Map<String, Long> shopsByType = allShops.stream()
                    .collect(Collectors.groupingBy(
                        shop -> shop.shopType().name(),
                        Collectors.counting()));

                Map<String, Object> stats = new HashMap<>();
                stats.put("totalShops", totalShops);
                stats.put("totalTrades", totalTrades);
                stats.put("shopsByType", shopsByType);
                stats.put("activeShops", allShops.stream().filter(ShopDataDTO::isActive).count());

                return ApiResponse.success(stats);
            });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getShopStats(String shopId) {
        if (shopId == null || shopId.isEmpty()) {
            return getServerStats();
        }

        return shopService.getShopById(shopId)
            .<ApiResponse<?>>thenCompose(optionalShop -> {
                if (optionalShop.isEmpty()) {
                    return CompletableFuture.completedFuture(
                        ApiResponse.error("NOT_FOUND",
                            "Shop with ID " + shopId + " not found"));
                }

                ShopDataDTO shop = optionalShop.get();

                return tradeService.getShopTradeHistory(shopId, 100)
                    .<ApiResponse<?>>handle((trades, innerEx) -> {
                        if (innerEx != null) return ApiResponse.error("INTERNAL_ERROR",
                            "Failed to retrieve shop statistics: " + innerEx.getMessage());
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
            .<ApiResponse<?>>handle((result, ex) -> {
                if (ex != null) return ApiResponse.error("INTERNAL_ERROR",
                    "Failed to retrieve shop statistics: " + ex.getMessage());
                return result;
            });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getHealthStatus() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> health = new HashMap<>();

            boolean fallbackMode = shopService.isInFallbackMode() || tradeService.isInFallbackMode();
            String status = fallbackMode ? "degraded" : "healthy";

            health.put("status", status);
            health.put("fallbackMode", fallbackMode);
            health.put("database", !fallbackMode ? "connected" : "fallback");
            health.put("uptime", System.currentTimeMillis() - startTime);
            health.put("timestamp", java.time.Instant.now().toString());

            return (ApiResponse<?>) ApiResponse.success(health);
        });
    }

    // ========================================================
    // Helper Methods
    // ========================================================

    private List<ShopDataDTO> applyFilters(List<ShopDataDTO> shops, Map<String, String> filters) {
        List<ShopDataDTO> result = new ArrayList<>(shops);

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

        String worldFilter = filters.get("world");
        if (worldFilter != null && !worldFilter.isEmpty()) {
            result = result.stream()
                .filter(shop -> worldFilter.equalsIgnoreCase(shop.locationWorld()))
                .collect(Collectors.toList());
        }

        return result;
    }

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

    private List<ShopDataDTO> applyPagination(List<ShopDataDTO> shops, int page, int limit) {
        int startIndex = (page - 1) * limit;
        int endIndex = Math.min(startIndex + limit, shops.size());

        if (startIndex >= shops.size()) {
            return List.of();
        }

        return shops.subList(startIndex, endIndex);
    }
}
