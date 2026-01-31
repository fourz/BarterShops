package org.fourz.BarterShops.data.repository.impl;

import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.data.FallbackTracker;
import org.fourz.BarterShops.data.IConnectionProvider;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.dto.TradeItemDTO;
import org.fourz.rvnkcore.util.log.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.*;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ShopRepositoryImpl.
 * Tests async operations, fallback mode, and database interactions.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShopRepositoryImpl Tests")
class ShopRepositoryImplTest {

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

    private ShopRepositoryImpl repository;
    private UUID testOwner;
    private Timestamp testTimestamp;

    @BeforeEach
    void setUp() {
        // Use the new constructor with injected LogManager
        repository = new ShopRepositoryImpl(connectionProvider, fallbackTracker, logger);
        testOwner = UUID.randomUUID();
        testTimestamp = new Timestamp(System.currentTimeMillis());
    }

    // Helper method to create test shop DTO
    private ShopDataDTO createTestShop() {
        return ShopDataDTO.builder()
                .shopId(1)
                .ownerUuid(testOwner)
                .shopName("Test Shop")
                .shopType(ShopDataDTO.ShopType.BARTER)
                .signLocation("world", 100.0, 64.0, 200.0)
                .chestLocation("world", 101.0, 64.0, 200.0)
                .isActive(true)
                .createdAt(testTimestamp)
                .lastModified(testTimestamp)
                .metadata(Map.of())
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

            ShopDataDTO input = createTestShop();
            ShopDataDTO result = getFutureResult(repository.save(input));

            assertSame(input, result);
            verify(connectionProvider, never()).getConnection();
        }

        @Test
        @DisplayName("findById() should return empty in fallback mode")
        void findByIdShouldReturnEmptyInFallbackMode() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            Optional<ShopDataDTO> result = getFutureResult(repository.findById(1));

            assertTrue(result.isEmpty());
            verify(connectionProvider, never()).getConnection();
        }

        @Test
        @DisplayName("deleteById() should return false in fallback mode")
        void deleteByIdShouldReturnFalseInFallbackMode() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            Boolean result = getFutureResult(repository.deleteById(1));

            assertFalse(result);
            verify(connectionProvider, never()).getConnection();
        }

        @Test
        @DisplayName("existsById() should return false in fallback mode")
        void existsByIdShouldReturnFalseInFallbackMode() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            Boolean result = getFutureResult(repository.existsById(1));

            assertFalse(result);
            verify(connectionProvider, never()).getConnection();
        }

        @Test
        @DisplayName("findByOwner() should return empty list in fallback mode")
        void findByOwnerShouldReturnEmptyInFallbackMode() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            var result = getFutureResult(repository.findByOwner(testOwner));

            assertTrue(result.isEmpty());
            verify(connectionProvider, never()).getConnection();
        }

        @Test
        @DisplayName("count() should return 0 in fallback mode")
        void countShouldReturnZeroInFallbackMode() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            Integer result = getFutureResult(repository.count());

            assertEquals(0, result);
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
        @DisplayName("findById() should return shop when found")
        void findByIdShouldReturnShopWhenFound() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false); // First for main query, then metadata

            // Mock ResultSet data
            when(resultSet.getInt("shop_id")).thenReturn(1);
            when(resultSet.getString("owner_uuid")).thenReturn(testOwner.toString());
            when(resultSet.getString("shop_name")).thenReturn("Test Shop");
            when(resultSet.getString("shop_type")).thenReturn("BARTER");
            when(resultSet.getString("location_world")).thenReturn("world");
            when(resultSet.getDouble("location_x")).thenReturn(100.0);
            when(resultSet.getDouble("location_y")).thenReturn(64.0);
            when(resultSet.getDouble("location_z")).thenReturn(200.0);
            when(resultSet.getString("chest_location_world")).thenReturn("world");
            when(resultSet.getDouble("chest_location_x")).thenReturn(101.0);
            when(resultSet.getDouble("chest_location_y")).thenReturn(64.0);
            when(resultSet.getDouble("chest_location_z")).thenReturn(200.0);
            when(resultSet.getBoolean("is_active")).thenReturn(true);
            when(resultSet.getTimestamp("created_at")).thenReturn(testTimestamp);
            when(resultSet.getTimestamp("last_modified")).thenReturn(testTimestamp);

            Optional<ShopDataDTO> result = getFutureResult(repository.findById(1));

            assertTrue(result.isPresent());
            assertEquals(1, result.get().shopId());
            assertEquals("Test Shop", result.get().shopName());
            verify(fallbackTracker).recordSuccess();
        }

        @Test
        @DisplayName("findById() should return empty when not found")
        void findByIdShouldReturnEmptyWhenNotFound() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false);

            Optional<ShopDataDTO> result = getFutureResult(repository.findById(999));

            assertTrue(result.isEmpty());
            verify(fallbackTracker).recordSuccess();
        }

        @Test
        @DisplayName("deleteById() should return true when deleted")
        void deleteByIdShouldReturnTrueWhenDeleted() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeUpdate()).thenReturn(1);

            Boolean result = getFutureResult(repository.deleteById(1));

            assertTrue(result);
            verify(fallbackTracker).recordSuccess();
        }

        @Test
        @DisplayName("deleteById() should return false when not found")
        void deleteByIdShouldReturnFalseWhenNotFound() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeUpdate()).thenReturn(0);

            Boolean result = getFutureResult(repository.deleteById(999));

            assertFalse(result);
            verify(fallbackTracker).recordSuccess();
        }

        @Test
        @DisplayName("existsById() should return true when exists")
        void existsByIdShouldReturnTrueWhenExists() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);

            Boolean result = getFutureResult(repository.existsById(1));

            assertTrue(result);
            verify(fallbackTracker).recordSuccess();
        }

        @Test
        @DisplayName("existsById() should return false when not exists")
        void existsByIdShouldReturnFalseWhenNotExists() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false);

            Boolean result = getFutureResult(repository.existsById(999));

            assertFalse(result);
            verify(fallbackTracker).recordSuccess();
        }
    }

    // Note: ErrorHandlingTests removed - requires real LogManager from RVNKCore
    // Error handling is tested via integration tests with real plugin instance

    @Nested
    @DisplayName("Query Operations")
    class QueryTests {

        @BeforeEach
        void setUpQueries() throws SQLException {
            when(fallbackTracker.isInFallbackMode()).thenReturn(false);
            when(connectionProvider.getConnection()).thenReturn(connection);
        }

        @Test
        @DisplayName("count() should return correct count")
        void countShouldReturnCorrectCount() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getInt(1)).thenReturn(42);

            Integer result = getFutureResult(repository.count());

            assertEquals(42, result);
            verify(fallbackTracker).recordSuccess();
        }

        @Test
        @DisplayName("countByOwner() should return correct count")
        void countByOwnerShouldReturnCorrectCount() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getInt(1)).thenReturn(5);

            Integer result = getFutureResult(repository.countByOwner(testOwner));

            assertEquals(5, result);
            verify(preparedStatement).setString(1, testOwner.toString());
            verify(fallbackTracker).recordSuccess();
        }
    }

    @Nested
    @DisplayName("Metadata Operations")
    class MetadataTests {

        @BeforeEach
        void setUpMetadata() throws SQLException {
            // Use lenient stubbing since some tests override with fallback mode
            lenient().when(fallbackTracker.isInFallbackMode()).thenReturn(false);
            lenient().when(connectionProvider.getConnection()).thenReturn(connection);
        }

        @Test
        @DisplayName("getMetadata() should return empty in fallback mode")
        void getMetadataShouldReturnEmptyInFallbackMode() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            Optional<String> result = getFutureResult(repository.getMetadata(1, "key"));

            assertTrue(result.isEmpty());
            verify(connectionProvider, never()).getConnection();
        }

        @Test
        @DisplayName("setMetadata() should return false in fallback mode")
        void setMetadataShouldReturnFalseInFallbackMode() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            Boolean result = getFutureResult(repository.setMetadata(1, "key", "value"));

            assertFalse(result);
            verify(connectionProvider, never()).getConnection();
        }

        @Test
        @DisplayName("removeMetadata() should return false in fallback mode")
        void removeMetadataShouldReturnFalseInFallbackMode() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            Boolean result = getFutureResult(repository.removeMetadata(1, "key"));

            assertFalse(result);
            verify(connectionProvider, never()).getConnection();
        }
    }

    @Nested
    @DisplayName("Trade Items Operations")
    class TradeItemsTests {

        @BeforeEach
        void setUpTradeItems() throws SQLException {
            // Use lenient stubbing since some tests override with fallback mode
            lenient().when(fallbackTracker.isInFallbackMode()).thenReturn(false);
            lenient().when(connectionProvider.getConnection()).thenReturn(connection);
        }

        @Test
        @DisplayName("findTradeItems() should return empty in fallback mode")
        void findTradeItemsShouldReturnEmptyInFallbackMode() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            var result = getFutureResult(repository.findTradeItems(1));

            assertTrue(result.isEmpty());
            verify(connectionProvider, never()).getConnection();
        }

        @Test
        @DisplayName("deleteTradeItem() should return false in fallback mode")
        void deleteTradeItemShouldReturnFalseInFallbackMode() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            Boolean result = getFutureResult(repository.deleteTradeItem(1));

            assertFalse(result);
            verify(connectionProvider, never()).getConnection();
        }
    }

    @Nested
    @DisplayName("Location-Based Queries")
    class LocationQueryTests {

        @BeforeEach
        void setUpLocationQueries() {
            when(fallbackTracker.isInFallbackMode()).thenReturn(false);
        }

        @Test
        @DisplayName("findBySignLocation() should return empty in fallback mode")
        void findBySignLocationShouldReturnEmptyInFallbackMode() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            Optional<ShopDataDTO> result = getFutureResult(
                    repository.findBySignLocation("world", 100.0, 64.0, 200.0));

            assertTrue(result.isEmpty());
            verify(connectionProvider, never()).getConnection();
        }

        @Test
        @DisplayName("findNearby() should return empty in fallback mode")
        void findNearbyShouldReturnEmptyInFallbackMode() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            var result = getFutureResult(
                    repository.findNearby("world", 100.0, 64.0, 200.0, 10.0));

            assertTrue(result.isEmpty());
            verify(connectionProvider, never()).getConnection();
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
            assertInstanceOf(CompletableFuture.class, repository.save(createTestShop()));
            assertInstanceOf(CompletableFuture.class, repository.findById(1));
            assertInstanceOf(CompletableFuture.class, repository.deleteById(1));
            assertInstanceOf(CompletableFuture.class, repository.existsById(1));
            assertInstanceOf(CompletableFuture.class, repository.findByOwner(testOwner));
            assertInstanceOf(CompletableFuture.class, repository.count());
            assertInstanceOf(CompletableFuture.class, repository.countByOwner(testOwner));
            assertInstanceOf(CompletableFuture.class, repository.findAllActive());
            assertInstanceOf(CompletableFuture.class, repository.findAll());
        }

        @Test
        @DisplayName("Operations should complete within timeout")
        void operationsShouldCompleteWithinTimeout() throws Exception {
            when(fallbackTracker.isInFallbackMode()).thenReturn(true);

            // In fallback mode, operations should complete immediately
            assertTimeout(java.time.Duration.ofMillis(100), () -> {
                repository.findById(1).get();
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
}
