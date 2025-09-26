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

    public SQLiteDatabaseManager(Plugin plugin) {
        this.plugin = plugin;
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
        String createShopsTable = "CREATE TABLE IF NOT EXISTS shops (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "owner_uuid TEXT NOT NULL, " +
                "chest_location TEXT NOT NULL, " +
                "sign_location TEXT NOT NULL, " +
                "trade_items TEXT NOT NULL" +
                ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createShopsTable);
        }
    }
}
