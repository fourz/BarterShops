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
        // Check cache first
        if (cacheEnabled) {
            CachedStats cached = playerStatsCache.get(playerUuid);
            if (cached != null && !cached.isExpired()) {
                logger.debug("Returning cached player stats for " + playerUuid);
                return CompletableFuture.completedFuture((StatsDataDTO) cached.data);
            }
        }

        return CompletableFuture.supplyAsync(() -> {
            logger.debug("Calculating player stats for " + playerUuid);

            // Get player name
            String playerName = plugin.getPlayerLookup().getPlayerName(playerUuid);

            // Gather statistics
            int shopsOwned = shopService.getShopCountByOwner(playerUuid).join();

            // Calculate trades and items (mock data for now - will use ITradeService when implemented)
            int tradesCompleted = calculatePlayerTrades(playerUuid);
            int itemsTraded = calculatePlayerItems(playerUuid);

            // Get average rating - calculate from shops owned
            double avgRating = 0.0;
            int ratingCount = 0;
            if (ratingService != null && shopsOwned > 0) {
                // Calculate average across all owned shops
                List<ShopDataDTO> ownedShops = shopService.getShopsByOwner(playerUuid).join();
                double totalRating = 0.0;
                int totalCount = 0;

                for (ShopDataDTO shop : ownedShops) {
                    double shopRating = ratingService.getAverageRating(shop.shopId()).join();
                    int shopRatingCount = ratingService.getRatingCount(shop.shopId()).join();

                    if (shopRatingCount > 0) {
                        totalRating += shopRating * shopRatingCount; // Weighted by count
                        totalCount += shopRatingCount;
                    }
                }

                if (totalCount > 0) {
                    avgRating = totalRating / totalCount;
                    ratingCount = totalCount;
                }
            }

            // Get most traded items
            Map<String, Integer> tradedItems = getMostTradedItemsForPlayer(playerUuid);

            StatsDataDTO stats = StatsDataDTO.playerStats(
                playerUuid, playerName, shopsOwned, tradesCompleted,
                itemsTraded, avgRating, ratingCount, tradedItems
            );

            // Update cache
            if (cacheEnabled) {
                playerStatsCache.put(playerUuid, new CachedStats(stats, System.currentTimeMillis()));
                logger.debug("Cached player stats for " + playerUuid);
            }

            return stats;
        });
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

        return CompletableFuture.supplyAsync(() -> {
            List<ShopDataDTO> ownedShops = shopService.getShopsByOwner(playerUuid).join();
            if (ownedShops.isEmpty()) {
                return 0.0;
            }

            double totalRating = 0.0;
            int totalCount = 0;

            for (ShopDataDTO shop : ownedShops) {
                double shopRating = ratingService.getAverageRating(shop.shopId()).join();
                int shopRatingCount = ratingService.getRatingCount(shop.shopId()).join();

                if (shopRatingCount > 0) {
                    totalRating += shopRating * shopRatingCount;
                    totalCount += shopRatingCount;
                }
            }

            return totalCount > 0 ? totalRating / totalCount : 0.0;
        });
    }

    // ========================================================
    // Server Statistics
    // ========================================================

    @Override
    public CompletableFuture<StatsDataDTO> getServerStats() {
        // Check cache first
        if (cacheEnabled && serverStatsCache != null && !serverStatsCache.isExpired()) {
            logger.debug("Returning cached server stats");
            return CompletableFuture.completedFuture((StatsDataDTO) serverStatsCache.data);
        }

        return CompletableFuture.supplyAsync(() -> {
            logger.debug("Calculating server stats");

            // Get all shops
            List<ShopDataDTO> allShops = shopService.getAllShops().join();
            int totalShops = allShops.size();
            int activeShops = (int) allShops.stream().filter(ShopDataDTO::isActive).count();

            // Calculate unique players
            Set<UUID> uniquePlayers = allShops.stream()
                .map(ShopDataDTO::ownerUuid)
                .collect(Collectors.toSet());
            int totalPlayers = uniquePlayers.size();

            // Calculate trades (mock data - will use ITradeService when implemented)
            int totalTrades = calculateTotalTrades();
            int totalItemsTraded = calculateTotalItems();

            // Calculate average trades per shop
            double avgTradesPerShop = totalShops > 0 ? (double) totalTrades / totalShops : 0.0;

            // Get most traded items
            Map<String, Integer> mostTradedItems = getMostTradedItemsGlobal();

            // Get top shops
            List<StatsDataDTO.TopShop> topShops = getTopShopsByTrades(10).join();

            StatsDataDTO.ServerStats serverStats = StatsDataDTO.ServerStats.create(
                totalShops, activeShops, totalPlayers, totalTrades,
                totalItemsTraded, avgTradesPerShop, mostTradedItems, topShops
            );

            StatsDataDTO stats = StatsDataDTO.serverStats(serverStats);

            // Update cache
            if (cacheEnabled) {
                serverStatsCache = new CachedStats(stats, System.currentTimeMillis());
                logger.debug("Cached server stats");
            }

            return stats;
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
        return CompletableFuture.supplyAsync(() -> {
            int totalShops = shopService.getShopCount().join();
            int totalTrades = calculateTotalTrades();
            return totalShops > 0 ? (double) totalTrades / totalShops : 0.0;
        });
    }

    // ========================================================
    // Shop Statistics
    // ========================================================

    @Override
    public CompletableFuture<StatsDataDTO> getShopStats(String shopId) {
        // Check cache first
        if (cacheEnabled) {
            CachedStats cached = shopStatsCache.get(shopId);
            if (cached != null && !cached.isExpired()) {
                logger.debug("Returning cached shop stats for " + shopId);
                return CompletableFuture.completedFuture((StatsDataDTO) cached.data);
            }
        }

        return CompletableFuture.supplyAsync(() -> {
            logger.debug("Calculating shop stats for " + shopId);

            // Get shop data
            Optional<ShopDataDTO> shopOpt = shopService.getShopById(shopId).join();
            if (shopOpt.isEmpty()) {
                logger.debug("Shop not found: " + shopId);
                return StatsDataDTO.builder().statType(StatsDataDTO.StatType.SHOP).build();
            }

            ShopDataDTO shop = shopOpt.get();

            // Calculate shop-specific stats (mock data for now)
            int tradeCount = calculateShopTrades(shopId);
            double rating = 0.0;
            int ratingCount = 0;

            if (ratingService != null) {
                rating = ratingService.getAverageRating(shop.shopId()).join();
                ratingCount = ratingService.getRatingCount(shop.shopId()).join();
            }

            StatsDataDTO stats = StatsDataDTO.builder()
                .statType(StatsDataDTO.StatType.SHOP)
                .targetUuid(shop.ownerUuid())
                .targetName(shop.shopName())
                .totalTradesCompleted(tradeCount)
                .averageRating(rating)
                .totalRatings(ratingCount)
                .build();

            // Update cache
            if (cacheEnabled) {
                shopStatsCache.put(shopId, new CachedStats(stats, System.currentTimeMillis()));
                logger.debug("Cached shop stats for " + shopId);
            }

            return stats;
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

            // Pre-warm cache with server stats
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
    // Leaderboards
    // ========================================================

    @Override
    public CompletableFuture<List<StatsDataDTO.TopShop>> getTopShopsByTrades(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debug("Getting top " + limit + " shops by trades");

            List<ShopDataDTO> allShops = shopService.getAllShops().join();

            return allShops.stream()
                .map(shop -> {
                    int tradeCount = calculateShopTrades(String.valueOf(shop.shopId()));
                    double rating = 0.0;
                    int ratingCount = 0;

                    if (ratingService != null) {
                        rating = ratingService.getAverageRating(shop.shopId()).join();
                        ratingCount = ratingService.getRatingCount(shop.shopId()).join();
                    }

                    String ownerName = plugin.getPlayerLookup().getPlayerName(shop.ownerUuid());

                    return new StatsDataDTO.TopShop(
                        shop.shopId(), shop.shopName(), shop.ownerUuid(),
                        ownerName, tradeCount, rating, ratingCount
                    );
                })
                .sorted(Comparator.comparingInt(StatsDataDTO.TopShop::tradeCount).reversed())
                .limit(limit)
                .toList();
        });
    }

    @Override
    public CompletableFuture<List<StatsDataDTO.TopShop>> getTopShopsByRating(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debug("Getting top " + limit + " shops by rating");

            List<ShopDataDTO> allShops = shopService.getAllShops().join();

            return allShops.stream()
                .map(shop -> {
                    int tradeCount = calculateShopTrades(String.valueOf(shop.shopId()));
                    double rating = 0.0;
                    int ratingCount = 0;

                    if (ratingService != null) {
                        rating = ratingService.getAverageRating(shop.shopId()).join();
                        ratingCount = ratingService.getRatingCount(shop.shopId()).join();
                    }

                    String ownerName = plugin.getPlayerLookup().getPlayerName(shop.ownerUuid());

                    return new StatsDataDTO.TopShop(
                        shop.shopId(), shop.shopName(), shop.ownerUuid(),
                        ownerName, tradeCount, rating, ratingCount
                    );
                })
                .filter(shop -> shop.ratingCount() >= 3) // Require at least 3 ratings
                .sorted(Comparator.comparingDouble(StatsDataDTO.TopShop::rating).reversed())
                .limit(limit)
                .toList();
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

    /**
     * Calculates player trade count from repository.
     */
    private int calculatePlayerTrades(UUID playerUuid) {
        TradeServiceImpl ts = getTradeService();
        if (ts == null) return 0;
        return ts.getPlayerTradeCount(playerUuid).join().intValue();
    }

    /**
     * Calculates player items traded from repository.
     */
    private int calculatePlayerItems(UUID playerUuid) {
        TradeServiceImpl ts = getTradeService();
        if (ts == null) return 0;
        List<TradeRecordDTO> trades = ts.getTradeHistory(playerUuid, 1000).join();
        return trades.stream().mapToInt(TradeRecordDTO::quantity).sum();
    }

    /**
     * Calculates total server trade count from repository.
     */
    private int calculateTotalTrades() {
        TradeServiceImpl ts = getTradeService();
        if (ts == null) return 0;
        return ts.getTotalTradeCount().join().intValue();
    }

    /**
     * Calculates total server items traded from repository.
     */
    private int calculateTotalItems() {
        TradeServiceImpl ts = getTradeService();
        if (ts == null) return 0;
        List<TradeRecordDTO> trades = ts.getRecentTrades(10000).join();
        return trades.stream().mapToInt(TradeRecordDTO::quantity).sum();
    }

    /**
     * Calculates shop-specific trade count from repository.
     */
    private int calculateShopTrades(String shopId) {
        TradeServiceImpl ts = getTradeService();
        if (ts == null) return 0;
        return ts.getShopTradeCount(shopId).join().intValue();
    }

    /**
     * Gets most traded items for a player from trade history.
     */
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

    /**
     * Gets most traded items globally from trade history.
     */
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

    /**
     * Represents a cached statistics entry with expiration.
     */
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
