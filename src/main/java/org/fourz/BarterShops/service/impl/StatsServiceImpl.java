package org.fourz.BarterShops.service.impl;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.dto.StatsDataDTO;
import org.fourz.BarterShops.data.dto.TradeRecordDTO;
import org.fourz.BarterShops.service.IShopService;
import org.fourz.BarterShops.service.IRatingService;
import org.fourz.BarterShops.service.IStatsService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Concrete implementation of IStatsService for RVNKCore ServiceRegistry.
 *
 * <p>Provides comprehensive statistics and analytics for shops and players
 * with caching support for performance optimization.</p>
 *
 * <p>Uses in-memory caching with configurable TTL (Time To Live).
 * Cache is automatically refreshed when data is modified.</p>
 */
public class StatsServiceImpl implements IStatsService {

    private static final String CLASS_NAME = "StatsServiceImpl";
    private static final long CACHE_TTL_MS = 300000; // 5 minutes

    private final BarterShops plugin;
    private final LogManager logger;
    private final IShopService shopService;
    private final IRatingService ratingService;

    // Cache storage
    private final Map<UUID, CachedStats> playerStatsCache;
    private CachedStats serverStatsCache;
    private final Map<String, CachedStats> shopStatsCache;

    // Cache configuration
    private volatile boolean cacheEnabled = true;

    public StatsServiceImpl(BarterShops plugin, IShopService shopService, IRatingService ratingService) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, CLASS_NAME);
        this.shopService = shopService;
        this.ratingService = ratingService;

        this.playerStatsCache = new ConcurrentHashMap<>();
        this.shopStatsCache = new ConcurrentHashMap<>();

        logger.info("StatsServiceImpl initialized with caching enabled");
    }

    // ========================================================
    // Player Statistics
    // ========================================================

    @Override
    public CompletableFuture<StatsDataDTO> getPlayerStats(UUID playerUuid) {
        if (cacheEnabled) {
            CachedStats cached = playerStatsCache.get(playerUuid);
            if (cached != null && !cached.isExpired()) {
                logger.debug("Returning cached player stats for " + playerUuid);
                return CompletableFuture.completedFuture((StatsDataDTO) cached.data);
            }
        }

        String playerName = plugin.getPlayerLookup().getPlayerName(playerUuid);
        int tradesCompleted = calculatePlayerTrades(playerUuid);
        int itemsTraded = calculatePlayerItems(playerUuid);
        Map<String, Integer> tradedItems = getMostTradedItemsForPlayer(playerUuid);

        CompletableFuture<Integer> shopCountFuture = shopService.getShopCountByOwner(playerUuid);
        CompletableFuture<List<ShopDataDTO>> ownedShopsFuture = shopService.getShopsByOwner(playerUuid);

        return shopCountFuture.thenCombine(ownedShopsFuture, (shopsOwned, ownedShops) -> {
            if (ratingService == null || ownedShops.isEmpty()) {
                StatsDataDTO stats = StatsDataDTO.playerStats(
                    playerUuid, playerName, shopsOwned, tradesCompleted, itemsTraded, 0.0, 0, tradedItems
                );
                if (cacheEnabled) {
                    playerStatsCache.put(playerUuid, new CachedStats(stats, System.currentTimeMillis()));
                }
                return CompletableFuture.completedFuture(stats);
            }

            // Fetch all shop ratings concurrently
            List<CompletableFuture<double[]>> ratingFutures = ownedShops.stream()
                .map(s -> ratingService.getAverageRating(s.shopId())
                    .thenCombine(ratingService.getRatingCount(s.shopId()), (r, c) -> new double[]{r, c}))
                .toList();

            return CompletableFuture.allOf(ratingFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<double[]> pairs = ratingFutures.stream().map(CompletableFuture::join).toList();
                    double avg = RatingCalculator.weightedAverage(pairs);
                    int ratingCount = pairs.stream().mapToInt(pair -> (int) pair[1]).sum();
                    StatsDataDTO stats = StatsDataDTO.playerStats(
                        playerUuid, playerName, shopsOwned, tradesCompleted, itemsTraded, avg, ratingCount, tradedItems
                    );
                    if (cacheEnabled) {
                        playerStatsCache.put(playerUuid, new CachedStats(stats, System.currentTimeMillis()));
                    }
                    return stats;
                });
        }).thenCompose(f -> f);
    }

    @Override
    public CompletableFuture<Integer> getPlayerShopCount(UUID playerUuid) {
        return shopService.getShopCountByOwner(playerUuid);
    }

    @Override
    public CompletableFuture<Integer> getPlayerTradeCount(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> calculatePlayerTrades(playerUuid));
    }

    @Override
    public CompletableFuture<Double> getPlayerAverageRating(UUID playerUuid) {
        if (ratingService == null) {
            return CompletableFuture.completedFuture(0.0);
        }

        return shopService.getShopsByOwner(playerUuid).thenCompose(ownedShops -> {
            if (ownedShops.isEmpty()) {
                return CompletableFuture.completedFuture(0.0);
            }

            List<CompletableFuture<double[]>> ratingFutures = ownedShops.stream()
                .map(s -> ratingService.getAverageRating(s.shopId())
                    .thenCombine(ratingService.getRatingCount(s.shopId()), (r, c) -> new double[]{r, c}))
                .toList();

            return CompletableFuture.allOf(ratingFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<double[]> pairs = ratingFutures.stream().map(CompletableFuture::join).toList();
                    return RatingCalculator.weightedAverage(pairs);
                });
        });
    }

    // ========================================================
    // Server Statistics
    // ========================================================

    @Override
    public CompletableFuture<StatsDataDTO> getServerStats() {
        if (cacheEnabled && serverStatsCache != null && !serverStatsCache.isExpired()) {
            logger.debug("Returning cached server stats");
            return CompletableFuture.completedFuture((StatsDataDTO) serverStatsCache.data);
        }

        return shopService.getAllShops().thenCompose(allShops -> {
            logger.debug("Calculating server stats");

            int totalShops = allShops.size();
            int activeShops = (int) allShops.stream().filter(ShopDataDTO::isActive).count();

            Set<UUID> uniquePlayers = allShops.stream()
                .map(ShopDataDTO::ownerUuid)
                .collect(Collectors.toSet());
            int totalPlayers = uniquePlayers.size();

            int totalTrades = calculateTotalTrades();
            int totalItemsTraded = calculateTotalItems();
            double avgTradesPerShop = totalShops > 0 ? (double) totalTrades / totalShops : 0.0;
            Map<String, Integer> mostTradedItems = getMostTradedItemsGlobal();

            return getTopShopsByTrades(5).thenApply(topShops -> {
                StatsDataDTO.ServerStats serverStats = StatsDataDTO.ServerStats.create(
                    totalShops, activeShops, totalPlayers, totalTrades,
                    totalItemsTraded, avgTradesPerShop, mostTradedItems, topShops
                );

                StatsDataDTO stats = StatsDataDTO.serverStats(serverStats);

                if (cacheEnabled) {
                    serverStatsCache = new CachedStats(stats, System.currentTimeMillis());
                    logger.debug("Cached server stats");
                }

                return stats;
            });
        });
    }

    @Override
    public CompletableFuture<Integer> getActiveShopCount() {
        return shopService.getAllShops().thenApply(shops ->
            (int) shops.stream().filter(ShopDataDTO::isActive).count()
        );
    }

    @Override
    public CompletableFuture<Integer> getTotalTradeCount() {
        return CompletableFuture.supplyAsync(this::calculateTotalTrades);
    }

    @Override
    public CompletableFuture<Integer> getTotalItemsTraded() {
        return CompletableFuture.supplyAsync(this::calculateTotalItems);
    }

    @Override
    public CompletableFuture<Double> getAverageTradesPerShop() {
        return shopService.getShopCount().thenApply(totalShops -> {
            int totalTrades = calculateTotalTrades();
            return totalShops > 0 ? (double) totalTrades / totalShops : 0.0;
        });
    }

    // ========================================================
    // Shop Statistics
    // ========================================================

    @Override
    public CompletableFuture<StatsDataDTO> getShopStats(String shopId) {
        if (cacheEnabled) {
            CachedStats cached = shopStatsCache.get(shopId);
            if (cached != null && !cached.isExpired()) {
                logger.debug("Returning cached shop stats for " + shopId);
                return CompletableFuture.completedFuture((StatsDataDTO) cached.data);
            }
        }

        return shopService.getShopById(shopId).thenCompose(shopOpt -> {
            logger.debug("Calculating shop stats for " + shopId);

            if (shopOpt.isEmpty()) {
                logger.debug("Shop not found: " + shopId);
                return CompletableFuture.completedFuture(
                    StatsDataDTO.builder().statType(StatsDataDTO.StatType.SHOP).build()
                );
            }

            ShopDataDTO shop = shopOpt.get();
            int tradeCount = calculateShopTrades(shopId);

            if (ratingService == null) {
                StatsDataDTO stats = StatsDataDTO.builder()
                    .statType(StatsDataDTO.StatType.SHOP)
                    .targetUuid(shop.ownerUuid())
                    .targetName(shop.shopName())
                    .totalTradesCompleted(tradeCount)
                    .build();
                if (cacheEnabled) {
                    shopStatsCache.put(shopId, new CachedStats(stats, System.currentTimeMillis()));
                }
                return CompletableFuture.completedFuture(stats);
            }

            CompletableFuture<Double> ratingFuture = ratingService.getAverageRating(shop.shopId());
            CompletableFuture<Integer> countFuture = ratingService.getRatingCount(shop.shopId());

            return CompletableFuture.allOf(ratingFuture, countFuture).thenApply(v -> {
                double rating = ratingFuture.join();
                int ratingCount = countFuture.join();

                StatsDataDTO stats = StatsDataDTO.builder()
                    .statType(StatsDataDTO.StatType.SHOP)
                    .targetUuid(shop.ownerUuid())
                    .targetName(shop.shopName())
                    .totalTradesCompleted(tradeCount)
                    .averageRating(rating)
                    .totalRatings(ratingCount)
                    .build();

                if (cacheEnabled) {
                    shopStatsCache.put(shopId, new CachedStats(stats, System.currentTimeMillis()));
                    logger.debug("Cached shop stats for " + shopId);
                }

                return stats;
            });
        });
    }

    @Override
    public CompletableFuture<Integer> getShopTradeCount(String shopId) {
        return CompletableFuture.supplyAsync(() -> calculateShopTrades(shopId));
    }

    // ========================================================
    // Cache Management
    // ========================================================

    @Override
    public CompletableFuture<Void> refreshCache() {
        return CompletableFuture.runAsync(() -> {
            logger.info("Refreshing statistics cache...");
            clearCache();
            getServerStats().join();
            logger.info("Statistics cache refreshed");
        });
    }

    @Override
    public void clearCache() {
        playerStatsCache.clear();
        shopStatsCache.clear();
        serverStatsCache = null;
        logger.debug("Statistics cache cleared");
    }

    @Override
    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    @Override
    public void setCacheEnabled(boolean enabled) {
        this.cacheEnabled = enabled;
        logger.info("Statistics caching " + (enabled ? "enabled" : "disabled"));
        if (!enabled) {
            clearCache();
        }
    }

    // ========================================================
    // Leaderboards (stubbed — pending SQL-level aggregation)
    // ========================================================

    @Override
    public CompletableFuture<List<StatsDataDTO.TopShop>> getTopShopsByTrades(int limit) {
        TradeServiceImpl ts = getTradeService();
        if (ts == null) return CompletableFuture.completedFuture(List.of());

        return ts.getTradeRepository().findTopShopsByTradeCount(limit)
            .thenCompose(rows -> {
                if (rows.isEmpty()) return CompletableFuture.completedFuture(List.of());

                List<CompletableFuture<Optional<StatsDataDTO.TopShop>>> futures = rows.stream()
                    .map(row -> {
                        int shopId = row[0];
                        int tradeCount = row[1];
                        return shopService.getShopById(String.valueOf(shopId)).thenCompose(shopOpt -> {
                            if (shopOpt.isEmpty()) return CompletableFuture.completedFuture(Optional.<StatsDataDTO.TopShop>empty());
                            ShopDataDTO shop = shopOpt.get();
                            String ownerName = plugin.getPlayerLookup().getPlayerName(shop.ownerUuid());
                            if (ratingService == null) {
                                return CompletableFuture.completedFuture(Optional.of(new StatsDataDTO.TopShop(
                                    shopId, shop.shopName(), shop.ownerUuid(), ownerName, tradeCount, 0.0, 0)));
                            }
                            return ratingService.getAverageRating(shopId)
                                .thenCombine(ratingService.getRatingCount(shopId), (avg, cnt) ->
                                    Optional.of(new StatsDataDTO.TopShop(
                                        shopId, shop.shopName(), shop.ownerUuid(), ownerName, tradeCount, avg, cnt)));
                        });
                    }).toList();

                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList()));
            });
    }

    @Override
    public CompletableFuture<List<StatsDataDTO.TopShop>> getTopShopsByRating(int limit) {
        if (ratingService == null) return CompletableFuture.completedFuture(List.of());

        return ratingService.getTopRatedShops(limit)
            .thenCompose(shopIds -> {
                if (shopIds.isEmpty()) return CompletableFuture.completedFuture(List.of());

                TradeServiceImpl ts = getTradeService();
                List<CompletableFuture<Optional<StatsDataDTO.TopShop>>> futures = shopIds.stream()
                    .map(shopId -> shopService.getShopById(String.valueOf(shopId)).thenCompose(shopOpt -> {
                        if (shopOpt.isEmpty()) return CompletableFuture.completedFuture(Optional.<StatsDataDTO.TopShop>empty());
                        ShopDataDTO shop = shopOpt.get();
                        String ownerName = plugin.getPlayerLookup().getPlayerName(shop.ownerUuid());
                        int tradeCount = (ts != null) ? ts.getShopTradeCount(String.valueOf(shopId)).join().intValue() : 0;
                        return ratingService.getAverageRating(shopId)
                            .thenCombine(ratingService.getRatingCount(shopId), (avg, cnt) ->
                                Optional.of(new StatsDataDTO.TopShop(
                                    shopId, shop.shopName(), shop.ownerUuid(), ownerName, tradeCount, avg, cnt)));
                    })).toList();

                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList()));
            });
    }

    @Override
    public CompletableFuture<Map<String, Integer>> getMostTradedItems(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Integer> allItems = getMostTradedItemsGlobal();
            return allItems.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (e1, e2) -> e1,
                    LinkedHashMap::new
                ));
        });
    }

    // ========================================================
    // Helper Methods (delegates to TradeServiceImpl)
    // ========================================================

    private TradeServiceImpl getTradeService() {
        return plugin.getTradeService();
    }

    private int calculatePlayerTrades(UUID playerUuid) {
        TradeServiceImpl ts = getTradeService();
        if (ts == null) return 0;
        return ts.getPlayerTradeCount(playerUuid).join().intValue();
    }

    private int calculatePlayerItems(UUID playerUuid) {
        TradeServiceImpl ts = getTradeService();
        if (ts == null) return 0;
        List<TradeRecordDTO> trades = ts.getTradeHistory(playerUuid, 1000).join();
        return trades.stream().mapToInt(TradeRecordDTO::quantity).sum();
    }

    private int calculateTotalTrades() {
        TradeServiceImpl ts = getTradeService();
        if (ts == null) return 0;
        return ts.getTotalTradeCount().join().intValue();
    }

    private int calculateTotalItems() {
        TradeServiceImpl ts = getTradeService();
        if (ts == null) return 0;
        List<TradeRecordDTO> trades = ts.getRecentTrades(10000).join();
        return trades.stream().mapToInt(TradeRecordDTO::quantity).sum();
    }

    private int calculateShopTrades(String shopId) {
        TradeServiceImpl ts = getTradeService();
        if (ts == null) return 0;
        return ts.getShopTradeCount(shopId).join().intValue();
    }

    private Map<String, Integer> getMostTradedItemsForPlayer(UUID playerUuid) {
        TradeServiceImpl ts = getTradeService();
        if (ts == null) return Map.of();
        List<TradeRecordDTO> trades = ts.getTradeHistory(playerUuid, 1000).join();
        return trades.stream()
            .filter(t -> t.itemStackData() != null)
            .collect(Collectors.groupingBy(
                t -> t.itemStackData().contains(":") ? t.itemStackData().split(":")[0] : t.itemStackData(),
                Collectors.summingInt(TradeRecordDTO::quantity)
            ));
    }

    private Map<String, Integer> getMostTradedItemsGlobal() {
        TradeServiceImpl ts = getTradeService();
        if (ts == null) return Map.of();
        List<TradeRecordDTO> trades = ts.getRecentTrades(10000).join();
        return trades.stream()
            .filter(t -> t.itemStackData() != null)
            .collect(Collectors.groupingBy(
                t -> t.itemStackData().contains(":") ? t.itemStackData().split(":")[0] : t.itemStackData(),
                Collectors.summingInt(TradeRecordDTO::quantity)
            ));
    }

    // ========================================================
    // Cache Helper Classes
    // ========================================================

    private static class CachedStats {
        final Object data;
        final long timestamp;

        CachedStats(Object data, long timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > CACHE_TTL_MS;
        }
    }
}
