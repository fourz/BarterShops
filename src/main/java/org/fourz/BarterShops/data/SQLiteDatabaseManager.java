package org.fourz.BarterShops.data;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.bukkit.plugin.Plugin;

public class SQLiteDatabaseManager implements DatabaseManager {
    private final Plugin plugin;
    private Connection connection;
    private final String tablePrefix;
    private final String SHOPS_TABLE;
    private final String RATINGS_TABLE;

    public SQLiteDatabaseManager(Plugin plugin) {
        this.plugin = plugin;

        // Load table prefix from config
        this.tablePrefix = plugin.getConfig().getString("storage.sqlite.tablePrefix", "");
        if (tablePrefix != null && !tablePrefix.isEmpty()) {
            plugin.getLogger().info("Using table prefix: " + tablePrefix);
        }

        // Initialize prefixed table names
        this.SHOPS_TABLE = table("shops");
        this.RATINGS_TABLE = table("shop_ratings");
    }

    /**
     * Get the table name with prefix applied.
     * @param baseName The base table name (e.g., "shops")
     * @return The prefixed table name (e.g., "barter_shops")
     */
    private String table(String baseName) {
        if (tablePrefix == null || tablePrefix.isEmpty()) {
            return baseName;
        }
        return tablePrefix + baseName;
    }

    @Override
    public void connect() throws SQLException {
        File databaseFile = new File(plugin.getDataFolder(), "bartershops.db");
        String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();

        connection = DriverManager.getConnection(url);
        setupTables();
    }

    @Override
    public void disconnect() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public void setupTables() throws SQLException {
        String createShopsTable = "CREATE TABLE IF NOT EXISTS " + SHOPS_TABLE + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "owner_uuid TEXT NOT NULL, " +
                "chest_location TEXT NOT NULL, " +
                "sign_location TEXT NOT NULL, " +
                "trade_items TEXT NOT NULL" +
                ");";

        String createRatingsTable = "CREATE TABLE IF NOT EXISTS " + RATINGS_TABLE + " (" +
                "rating_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "shop_id INTEGER NOT NULL, " +
                "rater_uuid TEXT NOT NULL, " +
                "rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5), " +
                "review TEXT, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(shop_id, rater_uuid)" +
                ");";

        String prefix = (tablePrefix != null && !tablePrefix.isEmpty()) ? tablePrefix : "";
        String createRatingsIndex = "CREATE INDEX IF NOT EXISTS idx_" + prefix + "shop_ratings_shop_id " +
                "ON " + RATINGS_TABLE + "(shop_id);";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createShopsTable);
            stmt.execute(createRatingsTable);
            stmt.execute(createRatingsIndex);
        }
    }

    /**
     * Get the shops table name (with prefix if configured).
     * @return The shops table name
     */
    public String getShopsTable() {
        return SHOPS_TABLE;
    }

    /**
     * Get the ratings table name (with prefix if configured).
     * @return The ratings table name
     */
    public String getRatingsTable() {
        return RATINGS_TABLE;
    }

    /**
     * Get the configured table prefix.
     * @return The table prefix, or empty string if none
     */
    public String getTablePrefix() {
        return tablePrefix != null ? tablePrefix : "";
    }
}
