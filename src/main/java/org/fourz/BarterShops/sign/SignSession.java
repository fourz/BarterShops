package org.fourz.BarterShops.sign;

import java.util.UUID;

/**
 * Lightweight, per-player-per-sign session state for the sign UI.
 *
 * Consolidates the three ConcurrentHashMap fields that previously lived on
 * {@code SignInteraction} (shiftClickPanelState, customerInfoToggled,
 * lastPurchaseTime) into a coherent, lifecycle-aware type.
 *
 * Sessions are created on first access, scoped to one (player, sign location)
 * pair, and removed when the player disconnects via
 * {@link SignSessionManager#cleanupPlayer(UUID)}.  They are never persisted.
 */
public class SignSession {

    private final UUID playerId;
    private final String signLocationKey; // Location.toString() of the sign block

    /** Panel index for owner shift-click cycling (1 = normal, 2 = customer preview, 3 = shop info). */
    private int shiftClickPanel = 1;

    /** Customer toggle for in-chat shop-info display. */
    private boolean customerInfoToggled = false;

    /** Timestamp (ms) of the last successful purchase attempt at this sign. */
    private long lastPurchaseTime = 0L;

    SignSession(UUID playerId, String signLocationKey) {
        this.playerId = playerId;
        this.signLocationKey = signLocationKey;
    }

    public UUID   getPlayerId()         { return playerId; }
    public String getSignLocationKey()  { return signLocationKey; }

    public int  getShiftClickPanel()            { return shiftClickPanel; }
    public void setShiftClickPanel(int panel)   { this.shiftClickPanel = panel; }

    public boolean isCustomerInfoToggled()          { return customerInfoToggled; }
    public void    setCustomerInfoToggled(boolean t) { this.customerInfoToggled = t; }

    public long getLastPurchaseTime()           { return lastPurchaseTime; }
    public void setLastPurchaseTime(long time)  { this.lastPurchaseTime = time; }
}
