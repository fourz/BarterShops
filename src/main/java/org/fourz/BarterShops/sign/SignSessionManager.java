package org.fourz.BarterShops.sign;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player-per-sign session state for the sign UI.
 *
 * Replaces three ConcurrentHashMap fields that previously lived in
 * {@code SignInteraction}:
 * <ul>
 *   <li>{@code shiftClickPanelState}  – owner panel cycling state</li>
 *   <li>{@code customerInfoToggled}   – customer info-display toggle</li>
 *   <li>{@code lastPurchaseTime}      – purchase debounce timestamp</li>
 * </ul>
 *
 * Keys are {@code "playerUUID:locationString"} to scope each session to a
 * single player-sign pair.  On player disconnect, all sessions for that
 * player are removed to prevent unbounded map growth.
 */
public class SignSessionManager {

    private final Map<String, SignSession> sessions = new ConcurrentHashMap<>();

    private String key(UUID playerId, String locationKey) {
        return playerId.toString() + ":" + locationKey;
    }

    /**
     * Returns the existing session for this player+sign pair, or creates a fresh one.
     */
    public SignSession getOrCreate(UUID playerId, String locationKey) {
        return sessions.computeIfAbsent(
            key(playerId, locationKey),
            k -> new SignSession(playerId, locationKey)
        );
    }

    /**
     * Returns the existing session, or {@code null} if none exists yet.
     * Use this for read-only access where creating a session is undesirable
     * (e.g., debounce check before any state has been written).
     */
    public SignSession get(UUID playerId, String locationKey) {
        return sessions.get(key(playerId, locationKey));
    }

    /**
     * Removes all sessions for a player who has disconnected.
     * Called by {@code SignManager.onPlayerQuit()}.
     */
    public void cleanupPlayer(UUID playerId) {
        String prefix = playerId.toString() + ":";
        sessions.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /**
     * Removes all sessions. Called on plugin disable.
     */
    public void cleanup() {
        sessions.clear();
    }
}
