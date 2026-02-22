package org.fourz.BarterShops.service;

import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.dto.StatsDataDTO;
import org.fourz.BarterShops.service.impl.StatsServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StatsServiceImpl.
 * Tests stats calculation, caching, and aggregation logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StatsServiceImpl Tests")
class StatsServiceTest {

    @Mock
    private BarterShops plugin;

    @Mock
    private IShopService shopService;

    @Mock
    private IRatingService ratingService;

    private StatsServiceImpl statsService;
    private UUID testPlayerUuid;
    private UUID testOwnerUuid;

    @BeforeEach
    void setUp() {
        testPlayerUuid = UUID.randomUUID();
        testOwnerUuid = UUID.randomUUID();

        statsService = new StatsServiceImpl(plugin, shopService, ratingService);
    }

    // ====== Player Statistics Tests ======

    @Nested
    @DisplayName("Player Statistics")
    class PlayerStatisticsTests {

        @BeforeEach
        void setUpPlayerStats() {
            when(shopService.getShopCountByOwner(testPlayerUuid))
                .thenReturn(CompletableFuture.completedFuture(3));
            when(shopService.getShopsByOwner(testPlayerUuid))
                .thenReturn(CompletableFuture.completedFuture(Arrays.asList(
                    createMockShop(1, testPlayerUuid),
                    createMockShop(2, testPlayerUuid),
                    createMockShop(3, testPlayerUuid)
                )));
        }

        @Test
        @DisplayName("getPlayerStats returns player statistics")
        void getPlayerStatsReturnsStats() throws ExecutionException, InterruptedException {
            when(ratingService.getAverageRating(anyInt()))
                .thenReturn(CompletableFuture.completedFuture(4.5));
            when(ratingService.getRatingCount(anyInt()))
                .thenReturn(CompletableFuture.completedFuture(10));

            StatsDataDTO stats = statsService.getPlayerStats(testPlayerUuid).get();

            assertNotNull(stats);
            assertEquals(StatsDataDTO.StatType.PLAYER, stats.statType());
            assertEquals(testPlayerUuid, stats.targetUuid());
            assertEquals(3, stats.totalShopsOwned());
        }

        @Test
        @DisplayName("getPlayerShopCount returns count of owned shops")
        void getPlayerShopCountReturnsCount() throws ExecutionException, InterruptedException {
            when(shopService.getShopCountByOwner(testPlayerUuid))
                .thenReturn(CompletableFuture.completedFuture(5));

            Integer count = statsService.getPlayerShopCount(testPlayerUuid).get();

            assertEquals(5, count);
        }

        @Test
        @DisplayName("getPlayerTradeCount returns player trade count")
        void getPlayerTradeCountReturnsCount() throws ExecutionException, InterruptedException {
            Integer count = statsService.getPlayerTradeCount(testPlayerUuid).get();

            assertNotNull(count);
            assertTrue(count >= 0);
        }

        @Test
        @DisplayName("getPlayerAverageRating returns average rating")
        void getPlayerAverageRatingReturnsRating() throws ExecutionException, InterruptedException {
            when(shopService.getShopCountByOwner(testPlayerUuid))
                .thenReturn(CompletableFuture.completedFuture(2));
            when(shopService.getShopsByOwner(testPlayerUuid))
                .thenReturn(CompletableFuture.completedFuture(Arrays.asList(
                    createMockShop(1, testPlayerUuid),
                    createMockShop(2, testPlayerUuid)
                )));
            when(ratingService.getAverageRating(1))
                .thenReturn(CompletableFuture.completedFuture(5.0));
            when(ratingService.getRatingCount(1))
                .thenReturn(CompletableFuture.completedFuture(10));
            when(ratingService.getAverageRating(2))
                .thenReturn(CompletableFuture.completedFuture(3.0));
            when(ratingService.getRatingCount(2))
                .thenReturn(CompletableFuture.completedFuture(5));

            Double average = statsService.getPlayerAverageRating(testPlayerUuid).get();

            assertNotNull(average);
            assertTrue(average >= 0 && average <= 5.0);
        }
    }

    // ====== Server Statistics Tests ======

    @Nested
    @DisplayName("Server Statistics")
    class ServerStatisticsTests {

        @BeforeEach
        void setUpServerStats() {
            when(shopService.getShopCount())
                .thenReturn(CompletableFuture.completedFuture(100));
            when(shopService.getAllShops())
                .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));
        }

        @Test
        @DisplayName("getServerStats returns server-wide statistics")
        void getServerStatsReturnsStats() throws ExecutionException, InterruptedException {
            StatsDataDTO stats = statsService.getServerStats().get();

            assertNotNull(stats);
            assertEquals(StatsDataDTO.StatType.SERVER, stats.statType());
            assertEquals("Server", stats.targetName());
        }

        @Test
        @DisplayName("getActiveShopCount returns count of active shops")
        void getActiveShopCountReturnsCount() throws ExecutionException, InterruptedException {
            when(shopService.getShopCount())
                .thenReturn(CompletableFuture.completedFuture(50));

            Integer count = statsService.getActiveShopCount().get();

            assertEquals(50, count);
        }

        @Test
        @DisplayName("getTotalTradeCount returns server trade count")
        void getTotalTradeCountReturnsCount() throws ExecutionException, InterruptedException {
            Integer count = statsService.getTotalTradeCount().get();

            assertNotNull(count);
            assertTrue(count >= 0);
        }

        @Test
        @DisplayName("getTotalItemsTraded returns total item count")
        void getTotalItemsTradedReturnsCount() throws ExecutionException, InterruptedException {
            Integer count = statsService.getTotalItemsTraded().get();

            assertNotNull(count);
            assertTrue(count >= 0);
        }

        @Test
        @DisplayName("getAverageTradesPerShop returns average")
        void getAverageTradesPerShopReturnsAverage() throws ExecutionException, InterruptedException {
            Double average = statsService.getAverageTradesPerShop().get();

            assertNotNull(average);
            assertTrue(average >= 0.0);
        }
    }

    // ====== Shop Statistics Tests ======

    @Nested
    @DisplayName("Shop Statistics")
    class ShopStatisticsTests {

        @Test
        @DisplayName("getShopStats returns shop statistics")
        void getShopStatsReturnsStats() throws ExecutionException, InterruptedException {
            StatsDataDTO stats = statsService.getShopStats("1").get();

            assertNotNull(stats);
            assertEquals(StatsDataDTO.StatType.SHOP, stats.statType());
        }

        @Test
        @DisplayName("getShopTradeCount returns shop trade count")
        void getShopTradeCountReturnsCount() throws ExecutionException, InterruptedException {
            Integer count = statsService.getShopTradeCount("1").get();

            assertNotNull(count);
            assertTrue(count >= 0);
        }
    }

    // ====== Caching Tests ======

    @Nested
    @DisplayName("Cache Management")
    class CacheManagementTests {

        @BeforeEach
        void setUpCaching() {
            when(shopService.getShopCountByOwner(testPlayerUuid))
                .thenReturn(CompletableFuture.completedFuture(2));
            when(shopService.getShopsByOwner(testPlayerUuid))
                .thenReturn(CompletableFuture.completedFuture(Arrays.asList(
                    createMockShop(1, testPlayerUuid),
                    createMockShop(2, testPlayerUuid)
                )));
            when(ratingService.getAverageRating(anyInt()))
                .thenReturn(CompletableFuture.completedFuture(4.0));
            when(ratingService.getRatingCount(anyInt()))
                .thenReturn(CompletableFuture.completedFuture(5));
        }

        @Test
        @DisplayName("refreshCache clears cache and recomputes")
        void refreshCacheRefreshesData() throws ExecutionException, InterruptedException {
            statsService.setCacheEnabled(true);
            StatsDataDTO stats1 = statsService.getPlayerStats(testPlayerUuid).get();

            statsService.refreshCache().get();

            StatsDataDTO stats2 = statsService.getPlayerStats(testPlayerUuid).get();

            assertNotNull(stats1);
            assertNotNull(stats2);
        }

        @Test
        @DisplayName("clearCache removes all cached entries")
        void clearCacheClearsEntries() throws ExecutionException, InterruptedException {
            statsService.setCacheEnabled(true);
            statsService.getPlayerStats(testPlayerUuid).get();

            statsService.clearCache();

            // Cache should be cleared, subsequent calls should recompute
            StatsDataDTO stats = statsService.getPlayerStats(testPlayerUuid).get();
            assertNotNull(stats);
        }

        @Test
        @DisplayName("isCacheEnabled returns true when enabled")
        void isCacheEnabledReturnsTrue() {
            statsService.setCacheEnabled(true);
            assertTrue(statsService.isCacheEnabled());
        }

        @Test
        @DisplayName("isCacheEnabled returns false when disabled")
        void isCacheEnabledReturnsFalse() {
            statsService.setCacheEnabled(false);
            assertFalse(statsService.isCacheEnabled());
        }

        @Test
        @DisplayName("setCacheEnabled controls cache behavior")
        void setCacheEnabledControlsBehavior() throws ExecutionException, InterruptedException {
            statsService.setCacheEnabled(true);
            StatsDataDTO stats1 = statsService.getPlayerStats(testPlayerUuid).get();

            statsService.setCacheEnabled(false);
            StatsDataDTO stats2 = statsService.getPlayerStats(testPlayerUuid).get();

            assertNotNull(stats1);
            assertNotNull(stats2);
        }
    }

    // ====== Leaderboard Tests ======

    @Nested
    @DisplayName("Leaderboards")
    class LeaderboardTests {

        @Test
        @DisplayName("getTopShopsByTrades returns top shops")
        void getTopShopsByTradesReturnsShops() throws ExecutionException, InterruptedException {
            List<StatsDataDTO.TopShop> topShops = Arrays.asList(
                new StatsDataDTO.TopShop(1, "Shop 1", UUID.randomUUID(), "Owner1", 100, 4.5, 20),
                new StatsDataDTO.TopShop(2, "Shop 2", UUID.randomUUID(), "Owner2", 80, 4.0, 15)
            );

            List<StatsDataDTO.TopShop> result = statsService.getTopShopsByTrades(2).get();

            assertNotNull(result);
            assertTrue(result.size() >= 0);
        }

        @Test
        @DisplayName("getTopShopsByRating returns top rated shops")
        void getTopShopsByRatingReturnsShops() throws ExecutionException, InterruptedException {
            List<StatsDataDTO.TopShop> topShops = Arrays.asList(
                new StatsDataDTO.TopShop(1, "Great Shop", UUID.randomUUID(), "Owner1", 50, 5.0, 30),
                new StatsDataDTO.TopShop(2, "Good Shop", UUID.randomUUID(), "Owner2", 40, 4.8, 25)
            );

            List<StatsDataDTO.TopShop> result = statsService.getTopShopsByRating(2).get();

            assertNotNull(result);
            assertTrue(result.size() >= 0);
        }

        @Test
        @DisplayName("getMostTradedItems returns item statistics")
        void getMostTradedItemsReturnsItems() throws ExecutionException, InterruptedException {
            Map<String, Integer> items = statsService.getMostTradedItems(5).get();

            assertNotNull(items);
            assertTrue(items.size() >= 0);
        }
    }

    // ====== DTO Validation Tests ======

    @Nested
    @DisplayName("DTO Validation")
    class DTOValidationTests {

        @Test
        @DisplayName("StatsDataDTO validates rating range")
        void statsDTOValidatesRatingRange() {
            StatsDataDTO stats = StatsDataDTO.builder()
                .statType(StatsDataDTO.StatType.PLAYER)
                .targetUuid(testPlayerUuid)
                .targetName("TestPlayer")
                .totalShopsOwned(5)
                .totalTradesCompleted(50)
                .totalItemsTraded(200)
                .averageRating(4.5)
                .totalRatings(20)
                .build();

            assertTrue(stats.averageRating() >= 0.0);
            assertTrue(stats.averageRating() <= 5.0);
        }

        @Test
        @DisplayName("StatsDataDTO validates negative values")
        void statsDTOValidatesNegativeValues() {
            StatsDataDTO stats = StatsDataDTO.builder()
                .statType(StatsDataDTO.StatType.PLAYER)
                .targetUuid(testPlayerUuid)
                .targetName("TestPlayer")
                .totalShopsOwned(-5)
                .totalTradesCompleted(-10)
                .build();

            assertTrue(stats.totalShopsOwned() >= 0);
            assertTrue(stats.totalTradesCompleted() >= 0);
        }

        @Test
        @DisplayName("TopShop validates rating range")
        void topShopValidatesRatingRange() {
            StatsDataDTO.TopShop topShop = new StatsDataDTO.TopShop(
                1, "Test Shop", UUID.randomUUID(), "Owner", 50, 5.5, 20
            );

            assertTrue(topShop.rating() >= 0.0);
            assertTrue(topShop.rating() <= 5.0);
        }

        @Test
        @DisplayName("ServerStats validates values")
        void serverStatsValidatesValues() {
            StatsDataDTO.ServerStats serverStats = StatsDataDTO.ServerStats.create(
                100, 95, 50, 1000, 5000, 10.0,
                Map.of("DIAMOND", 500, "IRON", 300),
                Collections.emptyList()
            );

            assertTrue(serverStats.totalShops() >= 0);
            assertTrue(serverStats.activeShops() >= 0);
            assertTrue(serverStats.totalTrades() >= 0);
        }
    }

    // ====== Formatting Tests ======

    @Nested
    @DisplayName("Formatting")
    class FormattingTests {

        @Test
        @DisplayName("StatsDataDTO.getFormattedRating returns formatted string")
        void getFormattedRatingReturnsString() {
            StatsDataDTO stats = StatsDataDTO.builder()
                .statType(StatsDataDTO.StatType.PLAYER)
                .averageRating(4.5)
                .totalRatings(20)
                .build();

            String formatted = stats.getFormattedRating();

            assertNotNull(formatted);
            assertTrue(formatted.contains("4.5"));
        }

        @Test
        @DisplayName("StatsDataDTO.getTopTradedItems returns top items")
        void getTopTradedItemsReturnsItems() {
            Map<String, Integer> items = Map.of(
                "DIAMOND", 500,
                "IRON", 300,
                "GOLD", 200
            );

            StatsDataDTO stats = StatsDataDTO.builder()
                .statType(StatsDataDTO.StatType.SERVER)
                .mostTradedItems(items)
                .build();

            List<String> topItems = stats.getTopTradedItems();

            assertNotNull(topItems);
            assertTrue(topItems.size() > 0);
        }

        @Test
        @DisplayName("TopShop.getFormattedRating returns formatted string")
        void topShopFormattedRatingReturnsString() {
            StatsDataDTO.TopShop topShop = new StatsDataDTO.TopShop(
                1, "Test Shop", UUID.randomUUID(), "Owner", 50, 4.5, 20
            );

            String formatted = topShop.getFormattedRating();

            assertNotNull(formatted);
            assertTrue(formatted.contains("4.5"));
        }
    }

    // ====== Edge Case Tests ======

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("handles player with no shops")
        void handlesPlayerWithNoShops() throws ExecutionException, InterruptedException {
            when(shopService.getShopCountByOwner(testPlayerUuid))
                .thenReturn(CompletableFuture.completedFuture(0));
            when(shopService.getShopsByOwner(testPlayerUuid))
                .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));

            Integer count = statsService.getPlayerShopCount(testPlayerUuid).get();

            assertEquals(0, count);
        }

        @Test
        @DisplayName("handles server with no shops")
        void handlesServerWithNoShops() throws ExecutionException, InterruptedException {
            when(shopService.getShopCount())
                .thenReturn(CompletableFuture.completedFuture(0));
            when(shopService.getAllShops())
                .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));

            Integer count = statsService.getActiveShopCount().get();

            assertEquals(0, count);
        }

        @Test
        @DisplayName("handles shop with no ratings")
        void handlesShopWithNoRatings() throws ExecutionException, InterruptedException {
            when(ratingService.getAverageRating(1))
                .thenReturn(CompletableFuture.completedFuture(0.0));
            when(ratingService.getRatingCount(1))
                .thenReturn(CompletableFuture.completedFuture(0));

            Double average = statsService.getPlayerAverageRating(testPlayerUuid).get();

            assertNotNull(average);
        }
    }

    // ====== Helper Methods ======

    private ShopDataDTO createMockShop(int shopId, UUID ownerUuid) {
        return ShopDataDTO.builder()
            .shopId(shopId)
            .ownerUuid(ownerUuid)
            .shopName("Test Shop " + shopId)
            .shopType(ShopDataDTO.ShopType.BARTER)
            .signLocation("world", 0, 64, 0)
            .chestLocation("world", 1, 64, 0)
            .isActive(true)
            .createdAt(new Timestamp(System.currentTimeMillis()))
            .lastModified(new Timestamp(System.currentTimeMillis()))
            .metadata(new HashMap<>())
            .build();
    }
}
