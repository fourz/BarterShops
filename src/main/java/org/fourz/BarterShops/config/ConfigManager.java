package org.fourz.BarterShops.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.fourz.BarterShops.BarterShops;
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
    }

    public void reloadConfig() {
        loadConfig();
        plugin.getLogger().info("Configuration reloaded");
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

    public void cleanup() {
        config = null;
    }
}
