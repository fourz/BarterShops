package org.fourz.BarterShops.api;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.dto.ShopGroupDTO;
import org.fourz.BarterShops.data.dto.TradeRecordDTO;
import org.fourz.BarterShops.service.IShopDatabaseService;
import org.fourz.BarterShops.service.IShopGroupService;
import org.fourz.BarterShops.service.IShopService;
import org.fourz.BarterShops.service.ITradeService;
import org.fourz.rvnkcore.api.model.response.ApiResponse;
import org.fourz.rvnkcore.api.service.IBarterShopsApiService;
import org.fourz.rvnkcore.api.util.ApiUtils;
import org.fourz.rvnkcore.util.PlayerLookup;

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
    private final IShopGroupService shopGroupService;
    private final PlayerLookup playerLookup;
    private final long startTime;

    public ShopApiEndpointImpl(
        IShopService shopService,
        ITradeService tradeService,
        IShopDatabaseService databaseService,
        IShopGroupService shopGroupService,
        PlayerLookup playerLookup
    ) {
        this.shopService = shopService;
        this.tradeService = tradeService;
        this.databaseService = databaseService;
        this.shopGroupService = shopGroupService;
        this.playerLookup = playerLookup;
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
                List<ShopDataDTO> clean = paginated.stream().map(this::sanitizeMetadata).toList();
                return ApiResponse.success(clean, page, limit, sorted.size());
            });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getShopById(String shopId) {
        try {
            Integer.parseInt(shopId);
        } catch (NumberFormatException e) {
            return CompletableFuture.completedFuture(
                ApiResponse.error("INVALID_REQUEST", "Invalid shop ID: must be numeric"));
        }
        return shopService.getShopById(shopId)
            .<ApiResponse<?>>handle((optionalShop, ex) -> {
                if (ex != null) return ApiResponse.error("INTERNAL_ERROR",
                    "Failed to retrieve shop: " + ex.getMessage());
                return optionalShop
                    .map(shop -> ApiResponse.success(this.sanitizeMetadata(shop)))
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
                List<ShopDataDTO> clean = shops.stream().map(this::sanitizeMetadata).toList();
                return ApiResponse.success(clean);
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
                return ApiResponse.success(enrichTrades(trades));
            });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getTradeById(String transactionId) {
        if (transactionId == null || transactionId.isEmpty()) {
            return CompletableFuture.completedFuture(
                ApiResponse.error("INVALID_REQUEST", "Transaction ID is required"));
        }
        try {
            UUID.fromString(transactionId);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(
                ApiResponse.error("INVALID_REQUEST", "Invalid transaction ID: must be a valid UUID"));
        }

        return tradeService.getTradeByTransactionId(transactionId)
            .<ApiResponse<?>>handle((optionalTrade, ex) -> {
                if (ex != null) return ApiResponse.error("INTERNAL_ERROR",
                    "Failed to retrieve trade: " + ex.getMessage());
                return optionalTrade
                    .map(trade -> ApiResponse.success(enrichTrades(List.of(trade)).get(0)))
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
                        stats.put("createdAt", shop.createdAt() != null
                            ? shop.createdAt().toInstant().toString() : null);
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

    public CompletableFuture<ApiResponse<?>> getGroups(Map<String, String> filters) {
        if (shopGroupService == null) {
            return CompletableFuture.completedFuture(
                ApiResponse.error("SERVICE_UNAVAILABLE", "Shop group service is not available"));
        }

        String ownerFilter = filters.get("owner");
        if (ownerFilter == null || ownerFilter.isEmpty()) {
            return CompletableFuture.completedFuture(
                ApiResponse.error("INVALID_REQUEST", "owner parameter is required"));
        }

        UUID ownerUuid;
        try {
            ownerUuid = UUID.fromString(ownerFilter);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(
                ApiResponse.error("INVALID_REQUEST", "Invalid owner UUID format"));
        }

        String worldFilter = filters.get("world");

        return shopGroupService.getPlayerGroups(ownerUuid)
            .<ApiResponse<?>>handle((groups, ex) -> {
                if (ex != null) return ApiResponse.error("INTERNAL_ERROR",
                    "Failed to retrieve groups: " + ex.getMessage());

                List<ShopGroupDTO> filtered = groups;
                if (worldFilter != null && !worldFilter.isEmpty()) {
                    filtered = groups.stream()
                        .filter(g -> worldFilter.equalsIgnoreCase(g.world()))
                        .collect(Collectors.toList());
                }

                int page = ApiUtils.parseIntOrDefault(filters.get("page"), 1);
                int limit = Math.min(ApiUtils.parseIntOrDefault(filters.get("limit"), 20), 100);
                int total = filtered.size();
                int startIndex = (page - 1) * limit;
                int endIndex = Math.min(startIndex + limit, total);
                List<ShopGroupDTO> paginated = startIndex >= total
                    ? List.of() : filtered.subList(startIndex, endIndex);

                List<Map<String, Object>> enriched = paginated.stream().map(group -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("groupId", group.groupId());
                    m.put("groupName", group.groupName());
                    m.put("ownerUuid", group.ownerUuid().toString());
                    m.put("ownerName", resolvePlayerName(group.ownerUuid()));
                    m.put("world", group.world());
                    m.put("isActive", group.isActive());
                    m.put("createdAt", group.createdAt() != null
                        ? group.createdAt().toInstant().toString() : null);
                    m.put("coOwners", group.coOwners().stream().map(uuid -> {
                        Map<String, String> co = new LinkedHashMap<>();
                        co.put("uuid", uuid.toString());
                        co.put("name", resolvePlayerName(uuid));
                        return co;
                    }).toList());
                    // Shop count fetched synchronously from the future (already completed by service layer)
                    try {
                        List<ShopDataDTO> shops = shopGroupService.getGroupShops(group.groupId()).join();
                        m.put("shopCount", shops.size());
                    } catch (Exception e) {
                        m.put("shopCount", 0);
                    }
                    return m;
                }).toList();

                return ApiResponse.success(enriched, page, limit, total);
            });
    }

    public CompletableFuture<ApiResponse<?>> getGroupById(String groupIdStr) {
        if (shopGroupService == null) {
            return CompletableFuture.completedFuture(
                ApiResponse.error("SERVICE_UNAVAILABLE", "Shop group service is not available"));
        }

        int groupId;
        try {
            groupId = Integer.parseInt(groupIdStr);
        } catch (NumberFormatException e) {
            return CompletableFuture.completedFuture(
                ApiResponse.error("INVALID_REQUEST", "Invalid group ID: must be numeric"));
        }

        return shopGroupService.getGroup(groupId)
            .thenCompose(optionalGroup -> {
                if (optionalGroup.isEmpty()) {
                    return CompletableFuture.completedFuture(
                        (ApiResponse<?>) ApiResponse.error("NOT_FOUND",
                            "Group with ID " + groupId + " not found"));
                }

                ShopGroupDTO group = optionalGroup.get();

                return shopGroupService.getGroupShops(groupId)
                    .<ApiResponse<?>>handle((shops, ex) -> {
                        if (ex != null) return ApiResponse.error("INTERNAL_ERROR",
                            "Failed to retrieve group shops: " + ex.getMessage());

                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("groupId", group.groupId());
                        result.put("groupName", group.groupName());
                        result.put("ownerUuid", group.ownerUuid().toString());
                        result.put("ownerName", resolvePlayerName(group.ownerUuid()));
                        result.put("world", group.world());
                        result.put("isActive", group.isActive());
                        result.put("createdAt", group.createdAt() != null
                            ? group.createdAt().toInstant().toString() : null);
                        result.put("lastModified", group.lastModified() != null
                            ? group.lastModified().toInstant().toString() : null);
                        result.put("coOwners", group.coOwners().stream().map(uuid -> {
                            Map<String, String> co = new LinkedHashMap<>();
                            co.put("uuid", uuid.toString());
                            co.put("name", resolvePlayerName(uuid));
                            return co;
                        }).toList());
                        result.put("shops", shops.stream()
                            .map(this::sanitizeMetadata).toList());
                        result.put("shopCount", shops.size());

                        return ApiResponse.success(result);
                    });
            })
            .<ApiResponse<?>>handle((result, ex) -> {
                if (ex != null) return ApiResponse.error("INTERNAL_ERROR",
                    "Failed to retrieve group: " + ex.getMessage());
                return result;
            });
    }

    // ========================================================
    // Helper Methods
    // ========================================================

    /**
     * Resolves a player UUID to a name via PlayerLookup.
     */
    private String resolvePlayerName(UUID uuid) {
        if (playerLookup != null) {
            return playerLookup.getPlayerName(uuid);
        }
        return uuid.toString().substring(0, 8);
    }

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

    /**
     * Sanitize metadata JSON strings by removing trailing commas before } and ].
     * Fixes invalid JSON produced by older ShopConfigSerializer versions.
     */
    private static String sanitizeJson(String json) {
        if (json == null) return null;
        return json.replaceAll(",\\s*}", "}").replaceAll(",\\s*]", "]");
    }

    /**
     * Return a copy of the shop DTO with sanitized metadata values and resolved ownerName.
     */
    private ShopDataDTO sanitizeMetadata(ShopDataDTO shop) {
        Map<String, String> meta = shop.metadata();
        Map<String, String> sanitized = new HashMap<>();
        if (meta != null) {
            for (Map.Entry<String, String> entry : meta.entrySet()) {
                sanitized.put(entry.getKey(), sanitizeJson(entry.getValue()));
            }
        }

        // Inject resolved owner name into metadata
        if (playerLookup != null) {
            sanitized.put("ownerName", playerLookup.getPlayerName(shop.ownerUuid()));
        } else {
            sanitized.putIfAbsent("ownerName", shop.ownerUuid().toString().substring(0, 8));
        }

        return new ShopDataDTO(
            shop.shopId(), shop.ownerUuid(), shop.shopName(), shop.shopType(),
            shop.locationWorld(), shop.locationX(), shop.locationY(), shop.locationZ(),
            shop.chestLocationWorld(), shop.chestLocationX(), shop.chestLocationY(), shop.chestLocationZ(),
            shop.isActive(), shop.createdAt(), shop.lastModified(), sanitized, shop.groupId()
        );
    }

    /**
     * Enrich trade records with resolved buyer/seller player names.
     */
    private List<Map<String, Object>> enrichTrades(List<TradeRecordDTO> trades) {
        Set<UUID> uuids = new HashSet<>();
        trades.forEach(t -> { uuids.add(t.buyerUuid()); uuids.add(t.sellerUuid()); });

        Map<UUID, String> names = new HashMap<>();
        uuids.forEach(u -> {
            if (playerLookup != null) {
                names.put(u, playerLookup.getPlayerName(u));
            } else {
                names.put(u, u.toString().substring(0, 8));
            }
        });

        return trades.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("transactionId", t.transactionId());
            m.put("shopId", t.shopId());
            m.put("buyerUuid", t.buyerUuid().toString());
            m.put("sellerUuid", t.sellerUuid().toString());
            m.put("buyerName", names.getOrDefault(t.buyerUuid(), t.buyerUuid().toString().substring(0, 8)));
            m.put("sellerName", names.getOrDefault(t.sellerUuid(), t.sellerUuid().toString().substring(0, 8)));
            m.put("itemStackData", t.itemStackData());
            m.put("quantity", t.quantity());
            m.put("pricePaid", t.pricePaid());
            m.put("status", t.status().name());
            m.put("tradeSource", t.tradeSource());
            m.put("completedAt", t.completedAt() != null ? t.completedAt().toInstant().toString() : null);
            return m;
        }).toList();
    }
}
