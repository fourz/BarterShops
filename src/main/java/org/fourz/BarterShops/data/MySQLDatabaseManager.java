package org.fourz.BarterShops.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public class MySQLDatabaseManager implements DatabaseManager {
    private final Plugin plugin;
    private Connection connection;
    private final String tablePrefix;
    private final String SHOPS_TABLE;

    public MySQLDatabaseManager(Plugin plugin) {
        this.plugin = plugin;

        // Load table prefix from config
        this.tablePrefix = plugin.getConfig().getString("storage.mysql.tablePrefix", "");
        if (tablePrefix != null && !tablePrefix.isEmpty()) {
            plugin.getLogger().info("Using table prefix: " + tablePrefix);
        }

        // Initialize prefixed table name
        this.SHOPS_TABLE = table("shops");
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
        FileConfiguration config = plugin.getConfig();
        String host = config.getString("storage.mysql.host", "localhost");
        int port = config.getInt("storage.mysql.port", 3306);
        String database = config.getString("storage.mysql.database", "bartershops");
        String user = config.getString("storage.mysql.username", "root");
        String password = config.getString("storage.mysql.password", "");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true";

        connection = DriverManager.getConnection(url, user, password);
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
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "owner_uuid VARCHAR(36) NOT NULL, " +
                "chest_location TEXT NOT NULL, " +
                "sign_location TEXT NOT NULL, " +
                "trade_items TEXT NOT NULL" +
                ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createShopsTable);
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
     * Get the configured table prefix.
     * @return The table prefix, or empty string if none
     */
    public String getTablePrefix() {
        return tablePrefix != null ? tablePrefix : "";
    }
}
