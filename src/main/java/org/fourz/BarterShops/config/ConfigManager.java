package org.fourz.BarterShops.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.fourz.BarterShops.BarterShops;
import org.fourz.rvnkcore.config.dto.DatabaseSettingsDTO;
import org.fourz.rvnkcore.config.dto.MySQLSettingsDTO;
import org.fourz.rvnkcore.config.dto.SQLiteSettingsDTO;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.sign.SignType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Level;

public class ConfigManager {
    private final BarterShops plugin;
    private FileConfiguration config;
    private File configFile;
    private DatabaseSettingsDTO databaseSettings;

    public ConfigManager(BarterShops plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "config.yml");
        }
        
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        try {
            this.databaseSettings = createDatabaseSettings();
        } catch (IllegalArgumentException e) {
            plugin.getLogger().severe("Invalid database configuration: " + e.getMessage());
        }
    }

    private DatabaseSettingsDTO createDatabaseSettings() {
        String storageType = config.getString("storage.type", "sqlite");
        DatabaseSettingsDTO.DatabaseType type = "mysql".equalsIgnoreCase(storageType)
                ? DatabaseSettingsDTO.DatabaseType.MYSQL
                : DatabaseSettingsDTO.DatabaseType.SQLITE;

        MySQLSettingsDTO mysqlSettings = null;
        if (type == DatabaseSettingsDTO.DatabaseType.MYSQL) {
            mysqlSettings = new MySQLSettingsDTO(
                    config.getString("storage.mysql.host", "localhost"),
                    config.getInt("storage.mysql.port", 3306),
                    config.getString("storage.mysql.database", "bartershops"),
                    config.getString("storage.mysql.username", "root"),
                    config.getString("storage.mysql.password", ""),
                    config.getBoolean("storage.mysql.useSSL", false),
                    config.getString("storage.mysql.tablePrefix", ""),
                    config.getInt("storage.mysql.pool-size", 10)
            );
        }

        SQLiteSettingsDTO sqliteSettings = null;
        if (type == DatabaseSettingsDTO.DatabaseType.SQLITE) {
            String dbFile = config.getString("storage.sqlite.database", "bartershops.db");
            String filePath = new File(plugin.getDataFolder(), dbFile).getAbsolutePath();
            sqliteSettings = new SQLiteSettingsDTO(
                    filePath,
                    config.getString("storage.sqlite.tablePrefix", "")
            );
        }

        DatabaseSettingsDTO dto = new DatabaseSettingsDTO(type, mysqlSettings, sqliteSettings);
        dto.validate();
        return dto;
    }

    public DatabaseSettingsDTO getDatabaseSettings() {
        if (databaseSettings == null) {
            databaseSettings = createDatabaseSettings();
        }
        return databaseSettings;
    }

    public void reloadConfig() {
        loadConfig();
        plugin.getLogger().info("Configuration reloaded");
    }

    public void cleanup() {
        config = null;
        databaseSettings = null;
    }

    public void saveConfig() {
        if (config == null || configFile == null) return;
        
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config to " + configFile, e);
        }
    }

    public FileConfiguration getConfig() {
        if (config == null) {
            loadConfig();
        }
        return config;
    }

    public String getMessage(String path) {
        return getConfig().getString("messages." + path, "Message not found: " + path);
    }

    public String getStorageType() {
        return getConfig().getString("storage.type", "sqlite");
    }

    public Level getLogLevel() {
        String levelStr = getConfig().getString("general.logLevel", "INFO");
        return LogManager.parseLevel(levelStr);
    }

    public int getInt(String path, int defaultValue) {
        return getConfig().getInt(path, defaultValue);
    }

    public long getLong(String path, long defaultValue) {
        return getConfig().getLong(path, defaultValue);
    }

    public boolean getBoolean(String path, boolean defaultValue) {
        return getConfig().getBoolean(path, defaultValue);
    }

    /**
     * Checks if a specific ShopType is enabled in configuration.
     * @param shopType The ShopType to check
     * @return true if enabled, false otherwise (default: true)
     */
    public boolean isShopTypeEnabled(ShopDataDTO.ShopType shopType) {
        String path = "shop-types.enabled." + shopType.name().toLowerCase();
        return getConfig().getBoolean(path, true);
    }

    /**
     * Gets all enabled ShopTypes from configuration.
     * @return Set of enabled ShopTypes
     */
    public Set<ShopDataDTO.ShopType> getEnabledShopTypes() {
        Set<ShopDataDTO.ShopType> enabled = EnumSet.noneOf(ShopDataDTO.ShopType.class);
        for (ShopDataDTO.ShopType type : ShopDataDTO.ShopType.values()) {
            if (isShopTypeEnabled(type)) {
                enabled.add(type);
            }
        }
        return enabled;
    }

    /**
     * Checks if a specific SignType is enabled in configuration.
     * @param signType The SignType to check
     * @return true if enabled, false otherwise (default: true)
     */
    public boolean isSignTypeEnabled(SignType signType) {
        String path = "sign-types.enabled." + signType.name().toLowerCase();
        return getConfig().getBoolean(path, true);
    }

    /**
     * Gets all enabled SignTypes from configuration.
     * @return Set of enabled SignTypes
     */
    public Set<SignType> getEnabledSignTypes() {
        Set<SignType> enabled = EnumSet.noneOf(SignType.class);
        for (SignType type : SignType.values()) {
            if (isSignTypeEnabled(type)) {
                enabled.add(type);
            }
        }
        return enabled;
    }

}
