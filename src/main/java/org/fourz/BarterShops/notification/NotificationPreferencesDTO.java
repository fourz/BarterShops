package org.fourz.BarterShops.notification;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object for player notification preferences.
 * Uses Java Record pattern for immutable data transfer.
 *
 * @param playerUuid Player's unique identifier
 * @param enabledChannels Map of notification types to enabled channels
 * @param enabledTypes Set of enabled notification types
 * @param quietHoursStart Quiet hours start time (0-23, -1 = disabled)
 * @param quietHoursEnd Quiet hours end time (0-23, -1 = disabled)
 * @param masterEnabled Master notification toggle
 */
public record NotificationPreferencesDTO(
        UUID playerUuid,
        Map<NotificationType, ChannelPreference> enabledChannels,
        Map<NotificationType, Boolean> enabledTypes,
        int quietHoursStart,
        int quietHoursEnd,
        boolean masterEnabled
) {
    /**
     * Channel preferences for each notification type.
     *
     * @param chat Enable chat messages
     * @param actionBar Enable action bar notifications
     * @param title Enable title/subtitle alerts
     * @param sound Enable sound effects
     */
    public record ChannelPreference(
            boolean chat,
            boolean actionBar,
            boolean title,
            boolean sound
    ) {
        /**
         * Creates default channel preferences (all enabled).
         */
        public static ChannelPreference defaults() {
            return new ChannelPreference(true, true, false, true);
        }

        /**
         * Creates channel preferences with all channels disabled.
         */
        public static ChannelPreference disabled() {
            return new ChannelPreference(false, false, false, false);
        }

        /**
         * Checks if any channel is enabled.
         *
         * @return true if at least one channel is enabled
         */
        public boolean hasAnyEnabled() {
            return chat || actionBar || title || sound;
        }
    }

    /**
     * Creates default notification preferences for a new player.
     *
     * @param playerUuid Player's UUID
     * @return Default preferences with all types enabled
     */
    public static NotificationPreferencesDTO createDefaults(UUID playerUuid) {
        Map<NotificationType, ChannelPreference> channels = new EnumMap<>(NotificationType.class);
        Map<NotificationType, Boolean> types = new EnumMap<>(NotificationType.class);

        for (NotificationType type : NotificationType.values()) {
            channels.put(type, ChannelPreference.defaults());
            types.put(type, type.isEnabledByDefault());
        }

        return new NotificationPreferencesDTO(
                playerUuid,
                channels,
                types,
                -1, // No quiet hours
                -1,
                true // Master enabled
        );
    }

    /**
     * Checks if a specific notification type is enabled.
     *
     * @param type Notification type to check
     * @return true if the type is enabled and has active channels
     */
    public boolean isTypeEnabled(NotificationType type) {
        if (!masterEnabled) {
            return false;
        }

        Boolean enabled = enabledTypes.get(type);
        if (enabled == null || !enabled) {
            return false;
        }

        ChannelPreference channels = enabledChannels.get(type);
        return channels != null && channels.hasAnyEnabled();
    }

    /**
     * Checks if currently within quiet hours.
     *
     * @param currentHour Current hour of day (0-23)
     * @return true if within quiet hours
     */
    public boolean isQuietHours(int currentHour) {
        if (quietHoursStart == -1 || quietHoursEnd == -1) {
            return false;
        }

        if (quietHoursStart <= quietHoursEnd) {
            return currentHour >= quietHoursStart && currentHour < quietHoursEnd;
        } else {
            // Quiet hours span midnight
            return currentHour >= quietHoursStart || currentHour < quietHoursEnd;
        }
    }

    /**
     * Gets channel preferences for a specific notification type.
     *
     * @param type Notification type
     * @return Channel preferences, or default if not set
     */
    public ChannelPreference getChannelPreference(NotificationType type) {
        return enabledChannels.getOrDefault(type, ChannelPreference.defaults());
    }
}
