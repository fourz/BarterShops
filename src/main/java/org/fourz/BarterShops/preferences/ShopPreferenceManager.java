package org.fourz.BarterShops.preferences;

import org.fourz.BarterShops.BarterShops;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player preferences for shop info display.
 * In-memory cache with optional persistence.
 */
public class ShopPreferenceManager {
    private final BarterShops plugin;
    private final Map<UUID, PlayerShopPreferences> preferences = new ConcurrentHashMap<>();

    public ShopPreferenceManager(BarterShops plugin) {
        this.plugin = plugin;
    }

    /**
     * Get preferences for player (returns defaults if not set).
     */
    public PlayerShopPreferences getPreferences(UUID playerId) {
        return preferences.getOrDefault(playerId, PlayerShopPreferences.createDefaults(playerId));
    }

    /**
     * Update a preference setting.
     *
     * @param playerId Player ID
     * @param key Setting key: "infoDisplay", "chatFormat", "ownShopsOnly", "autoExchange"
     * @param value Boolean value
     */
    public void setPreference(UUID playerId, String key, boolean value) {
        PlayerShopPreferences current = getPreferences(playerId);

        PlayerShopPreferences updated = switch(key.toLowerCase()) {
            case "infodisplay" ->
                new PlayerShopPreferences(playerId, value, current.chatFormat(), current.ownShopsOnly(), current.autoExchangeEnabled());
            case "chatformat" ->
                new PlayerShopPreferences(playerId, current.infoDisplayEnabled(), value, current.ownShopsOnly(), current.autoExchangeEnabled());
            case "ownshopsonly" ->
                new PlayerShopPreferences(playerId, current.infoDisplayEnabled(), current.chatFormat(), value, current.autoExchangeEnabled());
            case "autoexchange" ->
                new PlayerShopPreferences(playerId, current.infoDisplayEnabled(), current.chatFormat(), current.ownShopsOnly(), value);
            default -> current;
        };

        preferences.put(playerId, updated);
    }

    /**
     * Quick check if info display is enabled for player.
     */
    public boolean isInfoDisplayEnabled(UUID playerId) {
        return getPreferences(playerId).infoDisplayEnabled();
    }

    /**
     * Quick check if player prefers chat format (vs actionbar).
     */
    public boolean useChatFormat(UUID playerId) {
        return getPreferences(playerId).chatFormat();
    }

    /**
     * Quick check if player only wants to see own shops.
     */
    public boolean showOwnShopsOnly(UUID playerId) {
        return getPreferences(playerId).ownShopsOnly();
    }

    /**
     * Quick check if auto-exchange is enabled for player.
     */
    public boolean isAutoExchangeEnabled(UUID playerId) {
        return getPreferences(playerId).autoExchangeEnabled();
    }

    /**
     * Set auto-exchange preference for a player.
     */
    public void setAutoExchangeEnabled(UUID playerId, boolean enabled) {
        setPreference(playerId, "autoexchange", enabled);
    }

    /**
     * Cleanup on player logout.
     */
    public void removePlayer(UUID playerId) {
        preferences.remove(playerId);
    }

    /**
     * Save preferences to persistent storage (stub for future).
     */
    public void save() {
        // TODO: Implement persistence when needed
    }

    /**
     * Load preferences from persistent storage (stub for future).
     */
    public void load() {
        // TODO: Implement persistence when needed
    }

    /**
     * Clear all preferences (called on shutdown).
     */
    public void clearAll() {
        preferences.clear();
    }
}
