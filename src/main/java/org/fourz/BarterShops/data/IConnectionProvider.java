package org.fourz.BarterShops.data;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for database connection management.
 * Abstracts connection pooling (HikariCP) for repository implementations.
 *
 * <p>Implementations should provide connection pooling and health monitoring.</p>
 */
public interface IConnectionProvider {

    /**
     * Gets a connection from the pool.
     * The connection should be returned to the pool after use (close it).
     *
     * @return A database connection
     * @throws SQLException if unable to get a connection
     */
    Connection getConnection() throws SQLException;

    /**
     * Gets a connection asynchronously.
     *
     * @return CompletableFuture containing a database connection
     */
    default CompletableFuture<Connection> getConnectionAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getConnection();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get database connection", e);
            }
        });
    }

    /**
     * Closes all connections and shuts down the pool.
     */
    void shutdown();

    /**
     * Checks if the connection provider is healthy.
     *
     * @return true if connections can be obtained
     */
    boolean isHealthy();

    /**
     * Gets the number of active connections.
     *
     * @return Current active connection count
     */
    int getActiveConnections();

    /**
     * Gets the number of idle connections in the pool.
     *
     * @return Current idle connection count
     */
    int getIdleConnections();

    /**
     * Gets the maximum pool size.
     *
     * @return Maximum connections allowed
     */
    int getMaxPoolSize();

    /**
     * Gets the database type identifier.
     *
     * @return "mysql" or "sqlite"
     */
    String getDatabaseType();

    /**
     * Validates the database schema is correct.
     *
     * @return true if schema is valid
     */
    boolean validateSchema();

    /**
     * Runs database migrations if needed.
     *
     * @return true if migrations succeeded or were not needed
     */
    boolean runMigrations();
}
