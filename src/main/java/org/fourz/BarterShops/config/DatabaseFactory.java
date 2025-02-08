package org.fourz.BarterShops.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;

public class DatabaseFactory {
    private static DatabaseManager databaseManager;

    public static void initialize(Plugin plugin) throws SQLException {
        FileConfiguration config = plugin.getConfig();
        String type = config.getString("database.type", "sqlite");

        if (type.equalsIgnoreCase("mysql")) {
            databaseManager = new MySQLDatabaseManager(plugin);
        } else {
            databaseManager = new SQLiteDatabaseManager(plugin);
        }

        databaseManager.connect();
    }

    public static DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
