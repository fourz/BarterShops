package org.fourz.BarterShops.notification;

/**
 * Enumeration of notification types for shop events.
 * Each type represents a distinct event that can trigger notifications.
 */
public enum NotificationType {
    /**
     * Trade request received from another player.
     */
    TRADE_REQUEST("Trade Request", false),

    /**
     * Trade successfully completed.
     */
    TRADE_COMPLETE("Trade Complete", false),

    /**
     * Trade was cancelled by either party.
     */
    TRADE_CANCELLED("Trade Cancelled", false),

    /**
     * Shop stock is running low (configurable threshold).
     */
    SHOP_STOCK_LOW("Stock Low", true),

    /**
     * New sale in your shop.
     */
    SHOP_SALE("Shop Sale", true),

    /**
     * New review received on your shop.
     */
    REVIEW_RECEIVED("Review Received", false),

    /**
     * Price change alert for watched shops.
     */
    PRICE_CHANGE("Price Change", false),

    /**
     * System notification (admin messages, updates).
     */
    SYSTEM("System", true);

    private final String displayName;
    private final boolean enabledByDefault;

    NotificationType(String displayName, boolean enabledByDefault) {
        this.displayName = displayName;
        this.enabledByDefault = enabledByDefault;
    }

    /**
     * Gets the user-friendly display name for this notification type.
     *
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Checks if this notification type is enabled by default for new players.
     *
     * @return true if enabled by default
     */
    public boolean isEnabledByDefault() {
        return enabledByDefault;
    }

    /**
     * Gets the config key for this notification type.
     *
     * @return Config key string
     */
    public String getConfigKey() {
        return name().toLowerCase();
    }
}
