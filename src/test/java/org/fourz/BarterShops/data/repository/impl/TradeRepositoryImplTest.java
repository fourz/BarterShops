package org.fourz.BarterShops.data.repository.impl;

import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.data.FallbackTracker;
import org.fourz.BarterShops.data.IConnectionProvider;
import org.fourz.BarterShops.data.dto.TradeRecordDTO;
import org.fourz.rvnkcore.util.log.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.*;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TradeRepositoryImpl.
 * Tests async operations, fallback mode, and database interactions.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TradeRepositoryImpl Tests")
class TradeRepositoryImplTest {

    @Mock
    private BarterShops plugin;

    @Mock
    private IConnectionProvider connectionProvider;

    @Mock
    private FallbackTracker fallbackTracker;

    @Mock
    private LogManager logger;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    private TradeRepositoryImpl repository;
    private UUID testBuyer;
    private UUID testSeller;
    private Timestamp testTimestamp;

    @BeforeEach
    void setUp() {
        // Use the new constructor with injected LogManager
        repository = new TradeRepositoryImpl(connectionProvider, fallbackTracker, logger);
        testBuyer = UUID.randomUUID();
        testSeller = UUID.randomUUID();
        testTimestamp = new Timestamp(System.currentTimeMillis());
    }

    // Helper method to create test trade DTO
    private TradeRecordDTO createTestTrade() {
        return TradeRecordDTO.builder()
                .transactionId("tx-test-" + UUID.randomUUID())
                .shopId(1)
                .buyerUuid(testBuyer)
                .sellerUuid(testSeller)
                .itemStackData("DIAMOND:64")
                .quantity(64)
                .currencyMaterial("EMERALD")
                .pricePaid(10)
                .status(TradeRecordDTO.TradeStatus.COMPLETED)
                .completedAt(testTimestamp)
                .build();
    }

    // Helper method to get future result with timeout
    private <T> T getFutureResult(CompletableFuture<T> future)
            throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(5, TimeUnit.SECONDS);
    }

    @Nested
    @DisplayName("Fallback Mode Behavior")
    class FallbackModeTests {

        @Test
        @DisplayName("save() should return input unchanged in fallback mode")
        void saveShouldReturnInputInFallbackMode() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            TradeRecordDTO input = createTestTrade();
            TradeRecordDTO result = getFutureResult(repository.save(input));

            assertSame(input, result);
            verify(connectionProvider, never()).getConnection();
        }

        @Test
        @DisplayName("findByTransactionId() should return empty in fallback mode")
        void findByTransactionIdShouldReturnEmptyInFallbackMode() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            Optional<TradeRecordDTO> result = getFutureResult(repository.findByTransactionId("tx-123"));

            assertTrue(result.isEmpty());
            verify(connectionProvider, never()).getConnection();
        }

        @Test
        @DisplayName("deleteByTransactionId() should return false in fallback mode")
        void deleteByTransactionIdShouldReturnFalseInFallbackMode() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            Boolean result = getFutureResult(repository.deleteByTransactionId("tx-123"));

            assertFalse(result);
            verify(connectionProvider, never()).getConnection();
        }

        @Test
        @DisplayName("findByPlayer() should return empty list in fallback mode")
        void findByPlayerShouldReturnEmptyInFallbackMode() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            var result = getFutureResult(repository.findByPlayer(testBuyer, 100));

            assertTrue(result.isEmpty());
            verify(connectionProvider, never()).getConnection();
        }

        @Test
        @DisplayName("findByShop() should return empty list in fallback mode")
        void findByShopShouldReturnEmptyInFallbackMode() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            var result = getFutureResult(repository.findByShop(1, 100));

            assertTrue(result.isEmpty());
            verify(connectionProvider, never()).getConnection();
        }

        @Test
        @DisplayName("count() should return 0 in fallback mode")
        void countShouldReturnZeroInFallbackMode() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            Long result = getFutureResult(repository.count());

            assertEquals(0L, result);
            verify(connectionProvider, never()).getConnection();
        }

        @Test
        @DisplayName("countByShop() should return 0 in fallback mode")
        void countByShopShouldReturnZeroInFallbackMode() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            Long result = getFutureResult(repository.countByShop(1));

            assertEquals(0L, result);
            verify(connectionProvider, never()).getConnection();
        }

        @Test
        @DisplayName("countByPlayer() should return 0 in fallback mode")
        void countByPlayerShouldReturnZeroInFallbackMode() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            Long result = getFutureResult(repository.countByPlayer(testBuyer));

            assertEquals(0L, result);
            verify(connectionProvider, never()).getConnection();
        }
    }

    @Nested
    @DisplayName("CRUD Operations")
    class CrudTests {

        @BeforeEach
        void setUpCrud() throws SQLException {
            when(fallbackTracker.isInFallbackMode()).thenReturn(false);
            when(connectionProvider.getConnection()).thenReturn(connection);
        }

        @Test
        @DisplayName("findByTransactionId() should return trade when found")
        void findByTransactionIdShouldReturnTradeWhenFound() throws Exception {
            String txId = "tx-found-123";
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);

            // Mock ResultSet data
            when(resultSet.getString("transaction_id")).thenReturn(txId);
            when(resultSet.getInt("shop_id")).thenReturn(1);
            when(resultSet.getString("buyer_uuid")).thenReturn(testBuyer.toString());
            when(resultSet.getString("seller_uuid")).thenReturn(testSeller.toString());
            when(resultSet.getString("item_stack_data")).thenReturn("DIAMOND:64");
            when(resultSet.getInt("quantity")).thenReturn(64);
            when(resultSet.getString("currency_material")).thenReturn("EMERALD");
            when(resultSet.getInt("price_paid")).thenReturn(10);
            when(resultSet.getString("status")).thenReturn("COMPLETED");
            when(resultSet.getTimestamp("completed_at")).thenReturn(testTimestamp);

            Optional<TradeRecordDTO> result = getFutureResult(repository.findByTransactionId(txId));

            assertTrue(result.isPresent());
            assertEquals(txId, result.get().transactionId());
            assertEquals(64, result.get().quantity());
            verify(fallbackTracker).recordSuccess();
        }

        @Test
        @DisplayName("findByTransactionId() should return empty when not found")
        void findByTransactionIdShouldReturnEmptyWhenNotFound() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false);

            Optional<TradeRecordDTO> result = getFutureResult(repository.findByTransactionId("tx-not-found"));

            assertTrue(result.isEmpty());
            verify(fallbackTracker).recordSuccess();
        }

        @Test
        @DisplayName("deleteByTransactionId() should return true when deleted")
        void deleteByTransactionIdShouldReturnTrueWhenDeleted() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeUpdate()).thenReturn(1);

            Boolean result = getFutureResult(repository.deleteByTransactionId("tx-delete"));

            assertTrue(result);
            verify(fallbackTracker).recordSuccess();
        }

        @Test
        @DisplayName("deleteByTransactionId() should return false when not found")
        void deleteByTransactionIdShouldReturnFalseWhenNotFound() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeUpdate()).thenReturn(0);

            Boolean result = getFutureResult(repository.deleteByTransactionId("tx-not-found"));

            assertFalse(result);
            verify(fallbackTracker).recordSuccess();
        }
    }

    // Note: ErrorHandlingTests removed - requires real LogManager from RVNKCore
    // Error handling is tested via integration tests with real plugin instance

    @Nested
    @DisplayName("Statistics Operations")
    class StatisticsTests {

        @BeforeEach
        void setUpStats() throws SQLException {
            when(fallbackTracker.isInFallbackMode()).thenReturn(false);
            when(connectionProvider.getConnection()).thenReturn(connection);
        }

        @Test
        @DisplayName("count() should return correct count")
        void countShouldReturnCorrectCount() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getLong(1)).thenReturn(100L);

            Long result = getFutureResult(repository.count());

            assertEquals(100L, result);
            verify(fallbackTracker).recordSuccess();
        }

        @Test
        @DisplayName("countByShop() should return correct count")
        void countByShopShouldReturnCorrectCount() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getLong(1)).thenReturn(25L);

            Long result = getFutureResult(repository.countByShop(1));

            assertEquals(25L, result);
            verify(preparedStatement).setInt(1, 1);
            verify(fallbackTracker).recordSuccess();
        }

        @Test
        @DisplayName("countByPlayer() should return correct count")
        void countByPlayerShouldReturnCorrectCount() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getLong(1)).thenReturn(15L);

            Long result = getFutureResult(repository.countByPlayer(testBuyer));

            assertEquals(15L, result);
            verify(fallbackTracker).recordSuccess();
        }

        @Test
        @DisplayName("getTotalVolumeByShop() should return correct sum")
        void getTotalVolumeByShopShouldReturnCorrectSum() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getLong(1)).thenReturn(500L);

            Long result = getFutureResult(repository.getTotalVolumeByShop(1));

            assertEquals(500L, result);
            verify(fallbackTracker).recordSuccess();
        }

        @Test
        @DisplayName("countByDateRange() should return correct count")
        void countByDateRangeShouldReturnCorrectCount() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getLong(1)).thenReturn(50L);

            Timestamp start = new Timestamp(System.currentTimeMillis() - 86400000); // 1 day ago
            Timestamp end = testTimestamp;

            Long result = getFutureResult(repository.countByDateRange(start, end));

            assertEquals(50L, result);
            verify(fallbackTracker).recordSuccess();
        }
    }

    @Nested
    @DisplayName("Bulk Operations")
    class BulkOperationsTests {

        @BeforeEach
        void setUpBulk() throws SQLException {
            lenient().when(fallbackTracker.isInFallbackMode()).thenReturn(false);
            lenient().when(connectionProvider.getConnection()).thenReturn(connection);
            lenient().when(connectionProvider.getDatabaseType()).thenReturn("mysql");
        }

        @Test
        @DisplayName("deleteOlderThan() should return 0 in fallback mode")
        void deleteOlderThanShouldReturn0InFallbackMode() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            Timestamp cutoff = new Timestamp(System.currentTimeMillis() - 86400000 * 30);
            Integer result = getFutureResult(repository.deleteOlderThan(cutoff));

            assertEquals(0, result);
            verify(connectionProvider, never()).getConnection();
        }

        @Test
        @DisplayName("archiveOlderThan() should return 0 in fallback mode")
        void archiveOlderThanShouldReturn0InFallbackMode() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            Timestamp cutoff = new Timestamp(System.currentTimeMillis() - 86400000 * 90);
            Integer result = getFutureResult(repository.archiveOlderThan(cutoff));

            assertEquals(0, result);
            verify(connectionProvider, never()).getConnection();
        }

        @Test
        @DisplayName("findRecent() should return limited results")
        void findRecentShouldReturnLimitedResults() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false); // Empty for simplicity

            var result = getFutureResult(repository.findRecent(10));

            assertNotNull(result);
            verify(preparedStatement).setInt(1, 10);
            verify(fallbackTracker).recordSuccess();
        }
    }

    @Nested
    @DisplayName("Query Operations")
    class QueryTests {

        @BeforeEach
        void setUpQueries() throws SQLException {
            when(fallbackTracker.isInFallbackMode()).thenReturn(false);
            when(connectionProvider.getConnection()).thenReturn(connection);
        }

        @Test
        @DisplayName("findByDateRange() should use correct parameters")
        void findByDateRangeShouldUseCorrectParameters() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false);

            Timestamp start = new Timestamp(System.currentTimeMillis() - 86400000);
            Timestamp end = testTimestamp;

            var result = getFutureResult(repository.findByDateRange(start, end, 100));

            assertNotNull(result);
            assertTrue(result.isEmpty());
            verify(preparedStatement).setTimestamp(1, start);
            verify(preparedStatement).setTimestamp(2, end);
            verify(fallbackTracker).recordSuccess();
        }

        @Test
        @DisplayName("findByBuyer() should filter by buyer UUID")
        void findByBuyerShouldFilterByBuyer() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false);

            var result = getFutureResult(repository.findByBuyer(testBuyer, 100));

            assertNotNull(result);
            verify(preparedStatement).setString(1, testBuyer.toString());
            verify(fallbackTracker).recordSuccess();
        }

        @Test
        @DisplayName("findBySeller() should filter by seller UUID")
        void findBySellerShouldFilterBySeller() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false);

            var result = getFutureResult(repository.findBySeller(testSeller, 100));

            assertNotNull(result);
            verify(preparedStatement).setString(1, testSeller.toString());
            verify(fallbackTracker).recordSuccess();
        }
    }

    @Nested
    @DisplayName("Async Behavior")
    class AsyncTests {

        @Test
        @DisplayName("All operations should return CompletableFuture")
        void allOperationsShouldReturnCompletableFuture() {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            // Verify all methods return CompletableFuture
            assertInstanceOf(CompletableFuture.class, repository.save(createTestTrade()));
            assertInstanceOf(CompletableFuture.class, repository.findByTransactionId("tx-123"));
            assertInstanceOf(CompletableFuture.class, repository.deleteByTransactionId("tx-123"));
            assertInstanceOf(CompletableFuture.class, repository.findByPlayer(testBuyer, 100));
            assertInstanceOf(CompletableFuture.class, repository.findByBuyer(testBuyer, 100));
            assertInstanceOf(CompletableFuture.class, repository.findBySeller(testSeller, 100));
            assertInstanceOf(CompletableFuture.class, repository.findByShop(1, 100));
            assertInstanceOf(CompletableFuture.class, repository.count());
            assertInstanceOf(CompletableFuture.class, repository.countByShop(1));
            assertInstanceOf(CompletableFuture.class, repository.countByPlayer(testBuyer));
            assertInstanceOf(CompletableFuture.class, repository.findRecent(10));
        }

        @Test
        @DisplayName("Operations should complete within timeout")
        void operationsShouldCompleteWithinTimeout() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            // In fallback mode, operations should complete immediately
            assertTimeout(java.time.Duration.ofMillis(100), () -> {
                repository.findByTransactionId("tx-123").get();
            });

            assertTimeout(java.time.Duration.ofMillis(100), () -> {
                repository.count().get();
            });
        }
    }

    @Nested
    @DisplayName("Fallback Tracker Status")
    class FallbackTrackerStatusTests {

        @Test
        @DisplayName("isInFallbackMode() should delegate to fallbackTracker")
        void isInFallbackModeShouldDelegate() {
            when(fallbackTracker.isInFallbackMode()).thenReturn(false);
            assertFalse(repository.isInFallbackMode());

            when(fallbackTracker.isInFallbackMode()).thenReturn(true);
            assertTrue(repository.isInFallbackMode());
        }
    }

    @Nested
    @DisplayName("TradeStatus Handling")
    class TradeStatusTests {

        @BeforeEach
        void setUpStatus() throws SQLException {
            when(fallbackTracker.isInFallbackMode()).thenReturn(false);
            when(connectionProvider.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
        }

        @Test
        @DisplayName("Should correctly parse all TradeStatus values")
        void shouldParseAllTradeStatuses() throws Exception {
            for (TradeRecordDTO.TradeStatus status : TradeRecordDTO.TradeStatus.values()) {
                when(resultSet.next()).thenReturn(true, false);
                when(resultSet.getString("transaction_id")).thenReturn("tx-status-" + status.name());
                when(resultSet.getInt("shop_id")).thenReturn(1);
                when(resultSet.getString("buyer_uuid")).thenReturn(testBuyer.toString());
                when(resultSet.getString("seller_uuid")).thenReturn(testSeller.toString());
                when(resultSet.getString("item_stack_data")).thenReturn("ITEM:1");
                when(resultSet.getInt("quantity")).thenReturn(1);
                when(resultSet.getString("currency_material")).thenReturn("EMERALD");
                when(resultSet.getInt("price_paid")).thenReturn(1);
                when(resultSet.getString("status")).thenReturn(status.name());
                when(resultSet.getTimestamp("completed_at")).thenReturn(testTimestamp);

                Optional<TradeRecordDTO> result = getFutureResult(
                        repository.findByTransactionId("tx-status-" + status.name()));

                assertTrue(result.isPresent(), "Failed to parse status: " + status);
                assertEquals(status, result.get().status());
            }
        }
    }
}
