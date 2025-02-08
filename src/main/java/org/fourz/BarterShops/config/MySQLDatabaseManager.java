package org.fourz.BarterShops.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public class MySQLDatabaseManager implements DatabaseManager {
    private final Plugin plugin;
    private Connection connection;

    public MySQLDatabaseManager(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void connect() throws SQLException {
        FileConfiguration config = plugin.getConfig();
        String host = config.getString("mysql.host", "localhost");
        int port = config.getInt("mysql.port", 3306);
        String database = config.getString("mysql.database", "bartershops");
        String user = config.getString("mysql.user", "root");
        String password = config.getString("mysql.password", "");

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
        String createShopsTable = "CREATE TABLE IF NOT EXISTS shops (" +
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
}
