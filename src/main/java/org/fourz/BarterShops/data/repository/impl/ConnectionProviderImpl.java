package org.fourz.BarterShops.data.repository.impl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.fourz.BarterShops.BarterShops;
import org.fourz.rvnkcore.config.dto.DatabaseSettingsDTO;
import org.fourz.rvnkcore.config.dto.MySQLSettingsDTO;
import org.fourz.rvnkcore.config.dto.SQLiteSettingsDTO;
import org.fourz.BarterShops.data.IConnectionProvider;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;

/**
 * HikariCP-based connection provider implementation.
 * Supports both MySQL and SQLite with automatic configuration.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Connection pooling via HikariCP</li>
 *   <li>Health monitoring</li>
 *   <li>Schema validation and creation</li>
 *   <li>Graceful shutdown</li>
 * </ul>
 */
public class ConnectionProviderImpl implements IConnectionProvider {

    private final BarterShops plugin;
    private final LogManager logger;
    private final String databaseType;
    private final String tablePrefix;
    private HikariDataSource dataSource;

    // Table name constants (base names without prefix)
    private static final String TABLE_SHOPS = "shops";
    private static final String TABLE_TRADE_ITEMS = "trade_items";
    private static final String TABLE_TRADE_RECORDS = "trade_records";
    private static final String TABLE_SHOP_METADATA = "shop_metadata";
    private static final String TABLE_SHOP_RATINGS = "shop_ratings";

    /**
     * Creates a new ConnectionProviderImpl.
     *
     * @param plugin The BarterShops plugin instance
     */
    public ConnectionProviderImpl(BarterShops plugin) {
        this(plugin, LogManager.getInstance(plugin, "ConnectionProvider"));
    }

    /**
     * Creates a new ConnectionProviderImpl with injected LogManager.
     * This constructor supports dependency injection for testing.
     *
     * @param plugin The BarterShops plugin instance
     * @param logger The LogManager instance for logging
     */
    public ConnectionProviderImpl(BarterShops plugin, LogManager logger) {
        this.plugin = plugin;
        this.logger = logger;
        DatabaseSettingsDTO settings = plugin.getConfigManager().getDatabaseSettings();
        this.databaseType = settings.isMySQL() ? "mysql" : "sqlite";
        this.tablePrefix = settings.getTablePrefix();
        if (tablePrefix != null && !tablePrefix.isEmpty()) {
            logger.info("Using table prefix: " + tablePrefix);
        }
    }

    /**
     * Initializes the connection pool and creates schema.
     *
     * @throws SQLException if initialization fails
     */
    public void initialize() throws SQLException {
        logger.info("Initializing database connection pool (" + databaseType + ")...");

        HikariConfig config = new HikariConfig();

        if ("mysql".equals(databaseType)) {
            configureMysql(config);
        } else {
            configureSqlite(config);
        }

        // Common pool settings
        config.setPoolName("BarterShops-HikariPool");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(config);

        // Create schema
        createSchema();

        logger.info("Database connection pool initialized successfully");
    }

    private void configureMysql(HikariConfig config) {
        MySQLSettingsDTO mysql = plugin.getConfigManager().getDatabaseSettings().getMysqlSettings();

        config.setJdbcUrl("jdbc:mysql://" + mysql.getHost() + ":" + mysql.getPort() + "/" + mysql.getDatabase() +
                "?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8");
        config.setUsername(mysql.getUsername());
        config.setPassword(mysql.getPassword());
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // MySQL-specific settings
        config.setMaximumPoolSize(mysql.getPoolSize());
        config.setMinimumIdle(2);
        config.setIdleTimeout(600000); // 10 minutes
        config.setMaxLifetime(1800000); // 30 minutes
        config.setConnectionTimeout(30000); // 30 seconds
    }

    private void configureSqlite(HikariConfig config) {
        SQLiteSettingsDTO sqlite = plugin.getConfigManager().getDatabaseSettings().getSqliteSettings();

        config.setJdbcUrl("jdbc:sqlite:" + sqlite.getFilePath());
        config.setDriverClassName("org.sqlite.JDBC");

        // SQLite-specific settings (single connection is often best)
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setIdleTimeout(0); // Never timeout
        config.setMaxLifetime(0); // Infinite lifetime
        config.setConnectionTimeout(30000);

        // SQLite optimizations
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("foreign_keys", "ON");
    }

    private void createSchema() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            if ("mysql".equals(databaseType)) {
                createMySQLSchema(stmt);
            } else {
                createSQLiteSchema(stmt);
            }
        }

        logger.info("Database schema validated/created successfully");
    }

    private void createMySQLSchema(Statement stmt) throws SQLException {
        String shops = table(TABLE_SHOPS);
        String tradeItems = table(TABLE_TRADE_ITEMS);
        String tradeRecords = table(TABLE_TRADE_RECORDS);
        String shopMetadata = table(TABLE_SHOP_METADATA);
        String shopRatings = table(TABLE_SHOP_RATINGS);
        String p = getTablePrefix();

        stmt.execute("CREATE TABLE IF NOT EXISTS " + shops + " (" +
                "shop_id INT AUTO_INCREMENT PRIMARY KEY, " +
                "owner_uuid VARCHAR(36) NOT NULL, " +
                "shop_name VARCHAR(64), " +
                "shop_type VARCHAR(16) NOT NULL DEFAULT 'BARTER', " +
                "location_world VARCHAR(64), " +
                "location_x DOUBLE, " +
                "location_y DOUBLE, " +
                "location_z DOUBLE, " +
                "chest_location_world VARCHAR(64), " +
                "chest_location_x DOUBLE, " +
                "chest_location_y DOUBLE, " +
                "chest_location_z DOUBLE, " +
                "is_active BOOLEAN NOT NULL DEFAULT TRUE, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "INDEX idx_" + p + "owner (owner_uuid), " +
                "INDEX idx_" + p + "location (location_world, location_x, location_y, location_z), " +
                "INDEX idx_" + p + "active (is_active)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        stmt.execute("CREATE TABLE IF NOT EXISTS " + tradeItems + " (" +
                "trade_item_id INT AUTO_INCREMENT PRIMARY KEY, " +
                "shop_id INT NOT NULL, " +
                "item_stack_data TEXT NOT NULL, " +
                "currency_material VARCHAR(64), " +
                "price_amount INT NOT NULL DEFAULT 0, " +
                "stock_quantity INT NOT NULL DEFAULT 0, " +
                "is_offering BOOLEAN NOT NULL DEFAULT TRUE, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (shop_id) REFERENCES " + shops + "(shop_id) ON DELETE CASCADE, " +
                "INDEX idx_" + p + "shop (shop_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        stmt.execute("CREATE TABLE IF NOT EXISTS " + tradeRecords + " (" +
                "transaction_id VARCHAR(36) PRIMARY KEY, " +
                "shop_id INT NOT NULL, " +
                "buyer_uuid VARCHAR(36) NOT NULL, " +
                "seller_uuid VARCHAR(36) NOT NULL, " +
                "item_stack_data TEXT NOT NULL, " +
                "quantity INT NOT NULL, " +
                "currency_material VARCHAR(64), " +
                "price_paid INT NOT NULL DEFAULT 0, " +
                "status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED', " +
                "completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "INDEX idx_" + p + "buyer (buyer_uuid), " +
                "INDEX idx_" + p + "seller (seller_uuid), " +
                "INDEX idx_" + p + "shop_trade (shop_id), " +
                "INDEX idx_" + p + "status (status), " +
                "INDEX idx_" + p + "completed (completed_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        stmt.execute("CREATE TABLE IF NOT EXISTS " + shopMetadata + " (" +
                "shop_id INT NOT NULL, " +
                "meta_key VARCHAR(64) NOT NULL, " +
                "meta_value TEXT, " +
                "PRIMARY KEY (shop_id, meta_key), " +
                "FOREIGN KEY (shop_id) REFERENCES " + shops + "(shop_id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        stmt.execute("CREATE TABLE IF NOT EXISTS " + shopRatings + " (" +
                "rating_id INT AUTO_INCREMENT PRIMARY KEY, " +
                "shop_id INT NOT NULL, " +
                "rater_uuid VARCHAR(36) NOT NULL, " +
                "rating INT NOT NULL, " +
                "review TEXT, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE KEY uq_" + p + "shop_rater (shop_id, rater_uuid), " +
                "FOREIGN KEY (shop_id) REFERENCES " + shops + "(shop_id) ON DELETE CASCADE, " +
                "INDEX idx_" + p + "ratings_shop (shop_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    private void createSQLiteSchema(Statement stmt) throws SQLException {
        String shops = table(TABLE_SHOPS);
        String tradeItems = table(TABLE_TRADE_ITEMS);
        String tradeRecords = table(TABLE_TRADE_RECORDS);
        String shopMetadata = table(TABLE_SHOP_METADATA);
        String shopRatings = table(TABLE_SHOP_RATINGS);
        String p = getTablePrefix();

        stmt.execute("CREATE TABLE IF NOT EXISTS " + shops + " (" +
                "shop_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "owner_uuid TEXT NOT NULL, " +
                "shop_name TEXT, " +
                "shop_type TEXT NOT NULL DEFAULT 'BARTER', " +
                "location_world TEXT, " +
                "location_x REAL, " +
                "location_y REAL, " +
                "location_z REAL, " +
                "chest_location_world TEXT, " +
                "chest_location_x REAL, " +
                "chest_location_y REAL, " +
                "chest_location_z REAL, " +
                "is_active INTEGER NOT NULL DEFAULT 1, " +
                "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
                "last_modified TEXT DEFAULT CURRENT_TIMESTAMP" +
                ")");

        stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + p + "shops_owner ON " + shops + "(owner_uuid)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + p + "shops_location ON " + shops + "(location_world, location_x, location_y, location_z)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + p + "shops_active ON " + shops + "(is_active)");

        stmt.execute("CREATE TABLE IF NOT EXISTS " + tradeItems + " (" +
                "trade_item_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "shop_id INTEGER NOT NULL, " +
                "item_stack_data TEXT NOT NULL, " +
                "currency_material TEXT, " +
                "price_amount INTEGER NOT NULL DEFAULT 0, " +
                "stock_quantity INTEGER NOT NULL DEFAULT 0, " +
                "is_offering INTEGER NOT NULL DEFAULT 1, " +
                "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (shop_id) REFERENCES " + shops + "(shop_id) ON DELETE CASCADE" +
                ")");

        stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + p + "trade_items_shop ON " + tradeItems + "(shop_id)");

        stmt.execute("CREATE TABLE IF NOT EXISTS " + tradeRecords + " (" +
                "transaction_id TEXT PRIMARY KEY, " +
                "shop_id INTEGER NOT NULL, " +
                "buyer_uuid TEXT NOT NULL, " +
                "seller_uuid TEXT NOT NULL, " +
                "item_stack_data TEXT NOT NULL, " +
                "quantity INTEGER NOT NULL, " +
                "currency_material TEXT, " +
                "price_paid INTEGER NOT NULL DEFAULT 0, " +
                "status TEXT NOT NULL DEFAULT 'COMPLETED', " +
                "completed_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (shop_id) REFERENCES " + shops + "(shop_id) ON DELETE CASCADE" +
                ")");

        stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + p + "trade_records_buyer ON " + tradeRecords + "(buyer_uuid)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + p + "trade_records_seller ON " + tradeRecords + "(seller_uuid)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + p + "trade_records_shop ON " + tradeRecords + "(shop_id)");

        stmt.execute("CREATE TABLE IF NOT EXISTS " + shopMetadata + " (" +
                "shop_id INTEGER NOT NULL, " +
                "meta_key TEXT NOT NULL, " +
                "meta_value TEXT, " +
                "PRIMARY KEY (shop_id, meta_key), " +
                "FOREIGN KEY (shop_id) REFERENCES " + shops + "(shop_id) ON DELETE CASCADE" +
                ")");

        stmt.execute("CREATE TABLE IF NOT EXISTS " + shopRatings + " (" +
                "rating_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "shop_id INTEGER NOT NULL, " +
                "rater_uuid TEXT NOT NULL, " +
                "rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5), " +
                "review TEXT, " +
                "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(shop_id, rater_uuid), " +
                "FOREIGN KEY (shop_id) REFERENCES " + shops + "(shop_id) ON DELETE CASCADE" +
                ")");

        stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + p + "shop_ratings_shop_id ON " + shopRatings + "(shop_id)");
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Connection pool is not initialized or has been closed");
        }
        return dataSource.getConnection();
    }

    @Override
    public CompletableFuture<Connection> getConnectionAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getConnection();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get database connection", e);
            }
        });
    }

    @Override
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Shutting down database connection pool...");
            dataSource.close();
            logger.info("Database connection pool shut down successfully");
        }
    }

    @Override
    public boolean isHealthy() {
        if (dataSource == null || dataSource.isClosed()) {
            return false;
        }

        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            logger.warning("Database health check failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public int getActiveConnections() {
        return dataSource != null ? dataSource.getHikariPoolMXBean().getActiveConnections() : 0;
    }

    @Override
    public int getIdleConnections() {
        return dataSource != null ? dataSource.getHikariPoolMXBean().getIdleConnections() : 0;
    }

    @Override
    public int getMaxPoolSize() {
        return dataSource != null ? dataSource.getMaximumPoolSize() : 0;
    }

    @Override
    public String getDatabaseType() {
        return databaseType;
    }

    @Override
    public boolean validateSchema() {
        try (Connection conn = getConnection()) {
            // Check if shops table exists (with prefix if configured)
            var meta = conn.getMetaData();
            var rs = meta.getTables(null, null, table(TABLE_SHOPS), null);
            return rs.next();
        } catch (SQLException e) {
            logger.warning("Schema validation failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean runMigrations() {
        try {
            createSchema();
            return true;
        } catch (SQLException e) {
            logger.error("Failed to run database migrations: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getTablePrefix() {
        return tablePrefix != null ? tablePrefix : "";
    }

    @Override
    public String table(String baseName) {
        if (tablePrefix == null || tablePrefix.isEmpty()) {
            return baseName;
        }
        return tablePrefix + baseName;
    }
}
