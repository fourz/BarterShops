package org.fourz.BarterShops.notification;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.fourz.BarterShops.BarterShops;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.service.PlayerPreferencesService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Central notification dispatcher for trade events and shop updates.
 * Manages notification preferences, channels, and delivery.
 *
 * Integrated with RVNKCore PlayerPreferencesService (Phase 3).
 * Falls back to local preferences if service unavailable.
 */
public class NotificationManager {

    private final BarterShops plugin;
    private final LogManager logger;
    private final Map<UUID, NotificationPreferencesDTO> preferences;
    private final Queue<PendingNotification> notificationQueue;
    private BukkitTask queueProcessor;
    private static final int QUEUE_PROCESS_INTERVAL = 20; // 1 second
    private static final int MAX_QUEUE_SIZE = 1000;

    // Plugin identifier for PlayerPreferencesService
    private static final String PLUGIN_ID = "bartershops";

    public NotificationManager(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "NotificationManager");
        this.preferences = new ConcurrentHashMap<>();
        this.notificationQueue = new LinkedList<>();
        startQueueProcessor();
        logger.info("NotificationManager initialized");
    }

    /**
     * Sends a notification to a player through configured channels.
     *
     * @param playerUuid Player to notify
     * @param type Notification type
     * @param message Message to send
     */
    public void sendNotification(UUID playerUuid, NotificationType type, String message) {
        sendNotification(playerUuid, type, message, null);
    }

    /**
     * Sends a notification with optional sound.
     *
     * @param playerUuid Player to notify
     * @param type Notification type
     * @param message Message to send
     * @param sound Sound to play (null = default for type)
     */
    public void sendNotification(UUID playerUuid, NotificationType type, String message, Sound sound) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            logger.debug("Player not online, skipping notification: " + playerUuid);
            return;
        }

        NotificationPreferencesDTO prefs = getPreferences(playerUuid);

        // Check master toggle
        if (!prefs.masterEnabled()) {
            logger.debug("Notifications disabled for player: " + player.getName());
            return;
        }

        // Check type enabled
        if (!prefs.isTypeEnabled(type)) {
            logger.debug("Notification type disabled: " + type + " for " + player.getName());
            return;
        }

        // Queue notification for async delivery
        queueNotification(new PendingNotification(playerUuid, type, message, sound, prefs));
    }

    /**
     * Delivers a notification immediately (called async by queue processor).
     */
    private void deliverNotification(PendingNotification notification) {
        Player player = Bukkit.getPlayer(notification.playerUuid);
        if (player == null || !player.isOnline()) {
            return;
        }

        NotificationPreferencesDTO.ChannelPreference channels =
                notification.preferences.getChannelPreference(notification.type);

        String formattedMessage = formatMessage(notification.type, notification.message);

        // Deliver through enabled channels
        if (channels.chat()) {
            player.sendMessage(formattedMessage);
        }

        if (channels.actionBar()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(ChatColor.stripColor(formattedMessage)));
        }

        if (channels.title()) {
            String[] parts = notification.message.split("\n", 2);
            String title = parts[0];
            String subtitle = parts.length > 1 ? parts[1] : "";
            player.sendTitle(
                    ChatColor.translateAlternateColorCodes('&', title),
                    ChatColor.translateAlternateColorCodes('&', subtitle),
                    10, 70, 20
            );
        }

        if (channels.sound()) {
            Sound soundToPlay = notification.sound != null ?
                    notification.sound : getDefaultSound(notification.type);
            if (soundToPlay != null) {
                player.playSound(player.getLocation(), soundToPlay, 1.0f, 1.0f);
            }
        }

        logger.debug("Notification delivered: " + notification.type + " to " + player.getName());
    }

    /**
     * Formats a notification message with type-specific prefix.
     */
    private String formatMessage(NotificationType type, String message) {
        String prefix = getNotificationPrefix(type);
        return ChatColor.translateAlternateColorCodes('&', prefix + " " + message);
    }

    /**
     * Gets the prefix for a notification type.
     */
    private String getNotificationPrefix(NotificationType type) {
        return switch (type) {
            case TRADE_REQUEST -> "&e[Trade Request]";
            case TRADE_COMPLETE -> "&a[Trade Complete]";
            case TRADE_CANCELLED -> "&c[Trade Cancelled]";
            case SHOP_STOCK_LOW -> "&6[Low Stock]";
            case SHOP_SALE -> "&2[Shop Sale]";
            case REVIEW_RECEIVED -> "&b[New Review]";
            case PRICE_CHANGE -> "&d[Price Alert]";
            case SYSTEM -> "&7[System]";
        };
    }

    /**
     * Gets the default sound for a notification type.
     */
    private Sound getDefaultSound(NotificationType type) {
        return switch (type) {
            case TRADE_REQUEST -> Sound.BLOCK_NOTE_BLOCK_PLING;
            case TRADE_COMPLETE -> Sound.ENTITY_PLAYER_LEVELUP;
            case TRADE_CANCELLED -> Sound.BLOCK_NOTE_BLOCK_BASS;
            case SHOP_STOCK_LOW -> Sound.BLOCK_NOTE_BLOCK_BELL;
            case SHOP_SALE -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            case REVIEW_RECEIVED -> Sound.BLOCK_NOTE_BLOCK_CHIME;
            case PRICE_CHANGE -> Sound.BLOCK_NOTE_BLOCK_HARP;
            case SYSTEM -> Sound.BLOCK_NOTE_BLOCK_FLUTE;
        };
    }

    /**
     * Gets notification preferences for a player.
     * Checks PlayerPreferencesService first, falls back to local storage.
     * Creates defaults if not found in either system.
     */
    public NotificationPreferencesDTO getPreferences(UUID playerUuid) {
        // Try PlayerPreferencesService first
        if (RVNKCore.getServiceSafe(PlayerPreferencesService.class) != null) {
            try {
                return getPreferencesFromService(playerUuid);
            } catch (Exception e) {
                logger.debug("Failed to get preferences from service, using fallback: " + e.getMessage());
            }
        }

        // Fallback to local preferences
        return preferences.computeIfAbsent(playerUuid, NotificationPreferencesDTO::createDefaults);
    }

    /**
     * Retrieves preferences from PlayerPreferencesService.
     * Fetches the full PlayerPreferencesDTO in a single call (was 10+ sequential calls).
     */
    private NotificationPreferencesDTO getPreferencesFromService(UUID playerUuid) {
        PlayerPreferencesService service = RVNKCore.getServiceSafe(PlayerPreferencesService.class);
        if (service == null) {
            throw new IllegalStateException("Service not available");
        }

        try {
            // Single DB call instead of one per notification type
            org.fourz.rvnkcore.api.model.PlayerPreferencesDTO dto =
                    service.getPreferences(playerUuid, PLUGIN_ID)
                           .get(500, TimeUnit.MILLISECONDS);

            Map<NotificationType, Boolean> enabledTypes = new EnumMap<>(NotificationType.class);
            for (NotificationType type : NotificationType.values()) {
                boolean enabled = dto.getNotificationTypes().getOrDefault(type.name(), true);
                enabledTypes.put(type, enabled);
            }

            // Map RVNKCore string channel names to typed ChannelPreference
            Map<String, Map<String, Boolean>> serviceChannels = dto.getChannelPrefs();
            Map<NotificationType, NotificationPreferencesDTO.ChannelPreference> channels = new EnumMap<>(NotificationType.class);
            for (NotificationType type : NotificationType.values()) {
                Map<String, Boolean> tc = serviceChannels.getOrDefault(type.name(), Collections.emptyMap());
                channels.put(type, new NotificationPreferencesDTO.ChannelPreference(
                        tc.getOrDefault("CHAT", true),
                        tc.getOrDefault("ACTION_BAR", true),
                        tc.getOrDefault("TITLE", false),
                        tc.getOrDefault("SOUND", true)
                ));
            }

            return new NotificationPreferencesDTO(
                    playerUuid, channels, enabledTypes, dto.isMasterEnabled());
        } catch (Exception e) {
            logger.error("Failed to retrieve preferences from service: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Updates notification preferences for a player.
     * Updates local cache immediately, then persists to PlayerPreferencesService async.
     */
    public void updatePreferences(NotificationPreferencesDTO newPreferences) {
        // Update local cache immediately for instant effect
        preferences.put(newPreferences.playerUuid(), newPreferences);
        logger.debug("Updated preferences for player: " + newPreferences.playerUuid());

        // Persist to PlayerPreferencesService asynchronously (fire and forget)
        if (RVNKCore.getServiceSafe(PlayerPreferencesService.class) != null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    savePreferencesToService(newPreferences);
                } catch (Exception e) {
                    logger.warning("Failed to save preferences to service: " + e.getMessage());
                }
            });
        }
    }

    /**
     * Saves preferences to PlayerPreferencesService.
     * Builds a single PlayerPreferencesDTO and saves in one call (was 10+ sequential calls).
     */
    private void savePreferencesToService(NotificationPreferencesDTO prefs) {
        PlayerPreferencesService service = RVNKCore.getServiceSafe(PlayerPreferencesService.class);
        if (service == null) {
            throw new IllegalStateException("Service not available");
        }

        try {
            Map<String, Boolean> notificationTypes = new HashMap<>();
            for (Map.Entry<NotificationType, Boolean> entry : prefs.enabledTypes().entrySet()) {
                notificationTypes.put(entry.getKey().name(), entry.getValue());
            }

            // Map typed ChannelPreference to RVNKCore string channel names
            Map<String, Map<String, Boolean>> channelPrefs = new HashMap<>();
            for (Map.Entry<NotificationType, NotificationPreferencesDTO.ChannelPreference> entry : prefs.enabledChannels().entrySet()) {
                Map<String, Boolean> tc = new HashMap<>();
                tc.put("CHAT", entry.getValue().chat());
                tc.put("ACTION_BAR", entry.getValue().actionBar());
                tc.put("TITLE", entry.getValue().title());
                tc.put("SOUND", entry.getValue().sound());
                channelPrefs.put(entry.getKey().name(), tc);
            }

            org.fourz.rvnkcore.api.model.PlayerPreferencesDTO dto =
                    new org.fourz.rvnkcore.api.model.PlayerPreferencesDTO.Builder()
                            .playerUuid(prefs.playerUuid())
                            .pluginId(PLUGIN_ID)
                            .masterEnabled(prefs.masterEnabled())
                            .notificationTypes(notificationTypes)
                            .channelPrefs(channelPrefs)
                            .build();

            service.savePreferences(dto).get(5, TimeUnit.SECONDS);
            logger.debug("Saved preferences to PlayerPreferencesService: " + prefs.playerUuid());
        } catch (Exception e) {
            logger.error("Failed to save preferences to service: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Toggles master notification setting for a player.
     */
    public void toggleMasterEnabled(UUID playerUuid) {
        NotificationPreferencesDTO current = getPreferences(playerUuid);
        NotificationPreferencesDTO updated = new NotificationPreferencesDTO(
                current.playerUuid(),
                current.enabledChannels(),
                current.enabledTypes(),
                !current.masterEnabled()
        );
        updatePreferences(updated);
    }

    /**
     * Toggles a specific notification type for a player.
     */
    public void toggleNotificationType(UUID playerUuid, NotificationType type) {
        NotificationPreferencesDTO current = getPreferences(playerUuid);
        Map<NotificationType, Boolean> newTypes = new EnumMap<>(current.enabledTypes());
        newTypes.put(type, !newTypes.getOrDefault(type, true));

        NotificationPreferencesDTO updated = new NotificationPreferencesDTO(
                current.playerUuid(),
                current.enabledChannels(),
                newTypes,
                current.masterEnabled()
        );
        updatePreferences(updated);
    }

    /**
     * Adds a notification to the delivery queue.
     */
    private void queueNotification(PendingNotification notification) {
        if (notificationQueue.size() >= MAX_QUEUE_SIZE) {
            logger.warning("Notification queue full, dropping notification");
            return;
        }
        notificationQueue.offer(notification);
    }

    /**
     * Starts the async queue processor.
     */
    private void startQueueProcessor() {
        queueProcessor = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            while (!notificationQueue.isEmpty()) {
                PendingNotification notification = notificationQueue.poll();
                if (notification != null) {
                    // Deliver on main thread
                    Bukkit.getScheduler().runTask(plugin, () -> deliverNotification(notification));
                }
            }
        }, QUEUE_PROCESS_INTERVAL, QUEUE_PROCESS_INTERVAL);

        logger.debug("Notification queue processor started");
    }

    /**
     * Shuts down the notification manager.
     */
    public void shutdown() {
        if (queueProcessor != null) {
            queueProcessor.cancel();
            queueProcessor = null;
        }

        // Clear remaining notifications
        notificationQueue.clear();
        preferences.clear();

        logger.info("NotificationManager shutdown completed");
    }

    /**
     * Represents a pending notification in the delivery queue.
     */
    private record PendingNotification(
            UUID playerUuid,
            NotificationType type,
            String message,
            Sound sound,
            NotificationPreferencesDTO preferences
    ) {
    }
}
