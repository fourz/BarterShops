package org.fourz.BarterShops.service;

import org.fourz.BarterShops.data.dto.TradeRecordDTO;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for database operations.
 * Exposes database connectivity and health for cross-plugin access via RVNKCore ServiceRegistry.
 *
 * <p>Register with RVNKCore ServiceRegistry for use by other plugins:</p>
 * <pre>
 * ServiceRegistry.registerService(IShopDatabaseService.class, databaseManager);
 * </pre>
 *
 * <p>Follows the RVNKCore database abstraction pattern with ConnectionProvider
 * and FallbackTracker integration.</p>
 */
public interface IShopDatabaseService {

    // ========================================================
    // Connection Management
    // ========================================================

    /**
     * Gets a database connection from the pool.
     * Callers are responsible for closing the connection when done.
     *
     * @return A database connection, or null if unavailable
     */
    Connection getConnection();

    /**
     * Checks if a database connection is available.
     *
     * @return true if a connection can be obtained, false otherwise
     */
    boolean isConnected();

    /**
     * Tests database connectivity asynchronously.
     *
     * @return CompletableFuture with true if database is reachable
     */
    CompletableFuture<Boolean> testConnection();

    // ========================================================
    // Schema Management
    // ========================================================

    /**
     * Initializes the database schema.
     * Creates tables if they don't exist.
     *
     * @return CompletableFuture with true if schema initialized successfully
     */
    CompletableFuture<Boolean> initializeSchema();

    /**
     * Validates the database schema.
     * Checks if all required tables and columns exist.
     *
     * @return CompletableFuture containing validation result
     */
    CompletableFuture<SchemaValidationResult> validateSchema();

    /**
     * Gets the database dialect (MySQL, SQLite, etc.).
     *
     * @return The database dialect identifier
     */
    String getDatabaseDialect();

    // ========================================================
    // Health Monitoring
    // ========================================================

    /**
     * Gets database health statistics.
     *
     * @return CompletableFuture containing database health info
     */
    CompletableFuture<DatabaseHealth> getHealth();

    /**
     * Gets the number of active connections in the pool.
     *
     * @return The active connection count
     */
    int getActiveConnectionCount();

    /**
     * Gets the total number of connections in the pool.
     *
     * @return The total connection pool size
     */
    int getConnectionPoolSize();

    // ========================================================
    // Direct Query Methods (for API endpoints)
    // ========================================================

    /**
     * Gets recent trade records across all shops.
     *
     * @param limit Maximum number of trades to return
     * @return CompletableFuture containing list of recent trade records
     */
    CompletableFuture<List<TradeRecordDTO>> getRecentTrades(int limit);

    /**
     * Gets a trade record by transaction ID.
     *
     * @param transactionId The transaction ID
     * @return CompletableFuture containing the trade record, or empty if not found
     */
    CompletableFuture<Optional<TradeRecordDTO>> getTradeByTransactionId(String transactionId);

    // ========================================================
    // Fallback Mode
    // ========================================================

    /**
     * Checks if the service is operating in fallback mode.
     * In fallback mode, database operations return cached data or fail gracefully.
     *
     * @return true if in fallback mode, false if normal operation
     */
    boolean isInFallbackMode();

    /**
     * Gets the reason for entering fallback mode.
     *
     * @return The fallback reason, or null if not in fallback mode
     */
    String getFallbackReason();

    /**
     * Attempts to recover from fallback mode.
     *
     * @return CompletableFuture with true if recovery successful
     */
    CompletableFuture<Boolean> attemptRecovery();

    // ========================================================
    // Inner Types (will be moved to DTO package in impl-08)
    // ========================================================

    /**
     * Represents schema validation result.
     */
    interface SchemaValidationResult {
        boolean isValid();
        java.util.List<String> getMissingTables();
        java.util.List<String> getMissingColumns();
        java.util.List<String> getWarnings();
    }

    /**
     * Represents database health information.
     */
    interface DatabaseHealth {
        boolean isHealthy();
        long getResponseTimeMs();
        int getActiveConnections();
        int getIdleConnections();
        long getLastSuccessfulQueryTime();
        java.util.Optional<String> getLastError();
    }
}
