package org.fourz.BarterShops.notification;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.fourz.rvnkcore.api.event.PlayerPreferenceChangedEvent;

/**
 * Evicts a player's BarterShops notification cache entry when their preferences
 * are written externally (e.g. via the central /pref command).
 * Ensures the next notification delivery reads fresh data from PlayerPreferencesService.
 */
public class PreferenceCacheInvalidationListener implements Listener {

    private final NotificationManager notificationManager;

    public PreferenceCacheInvalidationListener(NotificationManager notificationManager) {
        this.notificationManager = notificationManager;
    }

    @EventHandler
    public void onPreferenceChanged(PlayerPreferenceChangedEvent event) {
        if (!"bartershops".equals(event.getPluginId())) return;
        notificationManager.invalidateCache(event.getPlayerUuid());
    }
}
