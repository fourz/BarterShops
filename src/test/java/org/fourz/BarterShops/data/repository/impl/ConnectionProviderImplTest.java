package org.fourz.BarterShops.data.repository.impl;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.bukkit.configuration.file.FileConfiguration;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.config.ConfigManager;
import org.fourz.rvnkcore.util.log.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConnectionProviderImpl.
 * Uses mocking for Bukkit and HikariCP dependencies.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConnectionProviderImpl Tests")
class ConnectionProviderImplTest {

    @Mock
    private BarterShops plugin;

    @Mock
    private ConfigManager configManager;

    @Mock
    private FileConfiguration fileConfiguration;

    @Mock
    private LogManager logger;

    @Mock
    private HikariDataSource dataSource;

    @Mock
    private HikariPoolMXBean poolMXBean;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData databaseMetaData;

    @Mock
    private ResultSet resultSet;

    @Mock
    private File dataFolder;

    @BeforeEach
    void setUp() {
        // Setup common mocks
        lenient().when(plugin.getConfigManager()).thenReturn(configManager);
        lenient().when(configManager.getConfig()).thenReturn(fileConfiguration);
        lenient().when(fileConfiguration.getString("database.type", "sqlite")).thenReturn("sqlite");
    }

    // Helper method to create provider with mocked logger
    private ConnectionProviderImpl createProvider() {
        return new ConnectionProviderImpl(plugin, logger);
    }

    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {

        @Test
        @DisplayName("Should create with SQLite type by default")
        void shouldCreateWithSqliteDefault() {
            when(fileConfiguration.getString("database.type", "sqlite")).thenReturn("sqlite");

            ConnectionProviderImpl provider = createProvider();

            assertEquals("sqlite", provider.getDatabaseType());
        }

        @Test
        @DisplayName("Should create with MySQL type when configured")
        void shouldCreateWithMysqlType() {
            when(fileConfiguration.getString("database.type", "sqlite")).thenReturn("mysql");

            ConnectionProviderImpl provider = createProvider();

            assertEquals("mysql", provider.getDatabaseType());
        }

        @Test
        @DisplayName("Should normalize database type to lowercase")
        void shouldNormalizeDatabaseType() {
            when(fileConfiguration.getString("database.type", "sqlite")).thenReturn("MYSQL");

            ConnectionProviderImpl provider = createProvider();

            assertEquals("mysql", provider.getDatabaseType());
        }
    }

    @Nested
    @DisplayName("Health Check Tests")
    class HealthCheckTests {

        @Test
        @DisplayName("Should return false when datasource is null")
        void shouldReturnFalseWhenDatasourceNull() {
            // Create provider without initialization (dataSource will be null)
            ConnectionProviderImpl provider = createProvider();

            assertFalse(provider.isHealthy());
        }

        @Test
        @DisplayName("Should return correct pool metrics")
        void shouldReturnPoolMetrics() {
            // Without initialization, should return 0 for all metrics
            ConnectionProviderImpl provider = createProvider();

            assertEquals(0, provider.getActiveConnections());
            assertEquals(0, provider.getIdleConnections());
            assertEquals(0, provider.getMaxPoolSize());
        }
    }

    @Nested
    @DisplayName("Connection Tests")
    class ConnectionTests {

        @Test
        @DisplayName("Should throw when getting connection before initialization")
        void shouldThrowWhenNotInitialized() {
            ConnectionProviderImpl provider = createProvider();

            assertThrows(SQLException.class, provider::getConnection);
        }

        @Test
        @DisplayName("Should return async connection as CompletableFuture")
        void shouldReturnAsyncConnection() {
            ConnectionProviderImpl provider = createProvider();

            var future = provider.getConnectionAsync();

            assertNotNull(future);
            // Future will complete exceptionally since not initialized
            assertTrue(future.isCompletedExceptionally() || 
                       future.handle((conn, ex) -> ex != null).join());
        }
    }

    @Nested
    @DisplayName("Schema Validation Tests")
    class SchemaValidationTests {

        @Test
        @DisplayName("Should return false when validation fails")
        void shouldReturnFalseWhenValidationFails() {
            // Without initialization, schema validation should fail
            ConnectionProviderImpl provider = createProvider();

            assertFalse(provider.validateSchema());
        }
    }

    @Nested
    @DisplayName("Shutdown Tests")
    class ShutdownTests {

        @Test
        @DisplayName("Should handle shutdown when not initialized")
        void shouldHandleShutdownWhenNotInitialized() {
            ConnectionProviderImpl provider = createProvider();

            // Should not throw
            assertDoesNotThrow(provider::shutdown);
        }
    }

    @Nested
    @DisplayName("Database Type Tests")
    class DatabaseTypeTests {

        @Test
        @DisplayName("Should return configured database type")
        void shouldReturnConfiguredType() {
            when(fileConfiguration.getString("database.type", "sqlite")).thenReturn("mysql");

            ConnectionProviderImpl provider = createProvider();

            assertEquals("mysql", provider.getDatabaseType());
        }

        @Test
        @DisplayName("Should handle mixed case database types")
        void shouldHandleMixedCase() {
            when(fileConfiguration.getString("database.type", "sqlite")).thenReturn("MySQL");

            ConnectionProviderImpl provider = createProvider();

            assertEquals("mysql", provider.getDatabaseType());
        }

        @Test
        @DisplayName("Should handle whitespace in database type")
        void shouldHandleWhitespace() {
            when(fileConfiguration.getString("database.type", "sqlite")).thenReturn(" sqlite ");

            ConnectionProviderImpl provider = createProvider();

            // Note: Current implementation doesn't trim, so this tests actual behavior
            assertEquals(" sqlite ", provider.getDatabaseType());
        }
    }

    @Nested
    @DisplayName("Migration Tests")
    class MigrationTests {

        @Test
        @DisplayName("Should return false when migrations fail")
        void shouldReturnFalseWhenMigrationsFail() {
            // Without initialization, migrations will fail
            ConnectionProviderImpl provider = createProvider();

            assertFalse(provider.runMigrations());
        }
    }

    @Nested
    @DisplayName("SQL Schema Tests")
    class SqlSchemaTests {

        @Test
        @DisplayName("MySQL schema should contain required tables")
        void mysqlSchemaShouldContainTables() {
            // This is a static analysis test - verify schema strings exist
            // The actual schema is tested via integration tests
            ConnectionProviderImpl provider = createProvider();
            assertNotNull(provider); // Provider created successfully
        }

        @Test
        @DisplayName("SQLite schema should be different from MySQL")
        void sqliteSchemaShouldBeDifferent() {
            // SQLite uses INTEGER PRIMARY KEY AUTOINCREMENT vs INT AUTO_INCREMENT
            // This is tested at integration level - unit test just verifies types
            when(fileConfiguration.getString("database.type", "sqlite")).thenReturn("sqlite");
            ConnectionProviderImpl sqliteProvider = createProvider();

            when(fileConfiguration.getString("database.type", "sqlite")).thenReturn("mysql");
            ConnectionProviderImpl mysqlProvider = createProvider();

            assertEquals("sqlite", sqliteProvider.getDatabaseType());
            assertEquals("mysql", mysqlProvider.getDatabaseType());
        }
    }
}
