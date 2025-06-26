package org.fourz.BarterShops.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.util.Debug;

import java.io.File;
import java.io.IOException;
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
        return Debug.getLevel(levelStr);
    }
    
    public void cleanup() {
        saveConfig();
        config = null;
    }
}
