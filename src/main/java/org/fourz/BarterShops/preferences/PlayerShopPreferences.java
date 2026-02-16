package org.fourz.BarterShops.preferences;

import java.util.UUID;

/**
 * Immutable DTO for player shop display preferences.
 * Thread-safe by design (record with no mutable fields).
 */
public record PlayerShopPreferences(
    UUID playerId,
    boolean infoDisplayEnabled,    // default: true
    boolean chatFormat,            // true=chat, false=actionbar (default: true)
    boolean ownShopsOnly,          // default: true
    boolean autoExchangeEnabled    // default: true (auto-purchase/deposit/withdrawal)
) {
    /**
     * Creates preferences with default values.
     */
    public static PlayerShopPreferences createDefaults(UUID playerId) {
        return new PlayerShopPreferences(playerId, true, true, true, true);
    }
}
