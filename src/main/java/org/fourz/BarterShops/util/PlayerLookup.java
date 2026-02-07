package org.fourz.BarterShops.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.util.log.LogManager;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Centralized player name resolution with RVNKCore PlayerService integration.
 * Tries RVNKCore's cached player database first, falls back to Bukkit.getOfflinePlayer().
 */
public class PlayerLookup {

    private final LogManager logger;
    private final ConcurrentHashMap<UUID, String> nameCache = new ConcurrentHashMap<>();

    // RVNKCore reflection handles (null if unavailable)
    private Object playerService;
    private Method getPlayerMethod;
    private Method getCurrentNameMethod;
    private boolean rvnkCoreEnabled = false;

    public PlayerLookup(Plugin plugin) {
        this.logger = LogManager.getInstance(plugin, "PlayerLookup");
        initRVNKCoreIntegration();
    }

    private void initRVNKCoreIntegration() {
        try {
            Class<?> rvnkCoreClass = Class.forName("org.fourz.rvnkcore.RVNKCore");
            Object coreInstance = rvnkCoreClass.getMethod("getInstance").invoke(null);
            if (coreInstance == null) return;

            Object serviceRegistry = rvnkCoreClass.getMethod("getServiceRegistry").invoke(coreInstance);
            if (serviceRegistry == null) return;

            Class<?> playerServiceClass = Class.forName("org.fourz.rvnkcore.api.service.PlayerService");
            Method getServiceMethod = serviceRegistry.getClass().getMethod("getService", Class.class);
            Object service = getServiceMethod.invoke(serviceRegistry, playerServiceClass);

            if (service != null) {
                this.playerService = service;
                this.getPlayerMethod = service.getClass().getMethod("getPlayer", UUID.class);

                Class<?> playerDTOClass = Class.forName("org.fourz.rvnkcore.api.model.PlayerDTO");
                this.getCurrentNameMethod = playerDTOClass.getMethod("getCurrentName");

                this.rvnkCoreEnabled = true;
                logger.info("RVNKCore PlayerService integration enabled");
            } else {
                logger.info("RVNKCore PlayerService not registered - using Bukkit fallback");
            }
        } catch (ClassNotFoundException e) {
            logger.debug("RVNKCore not available - using Bukkit fallback");
        } catch (Exception e) {
            logger.debug("RVNKCore PlayerService init failed: " + e.getMessage());
        }
    }

    /**
     * Gets a player name by UUID. Tries RVNKCore cache first, falls back to Bukkit.
     *
     * @param uuid The player UUID
     * @return The player name, or a truncated UUID if unknown
     */
    public String getPlayerName(UUID uuid) {
        if (uuid == null) return "Unknown";

        String cached = nameCache.get(uuid);
        if (cached != null) return cached;

        // Try RVNKCore PlayerService
        if (rvnkCoreEnabled) {
            String name = lookupViaRVNKCore(uuid);
            if (name != null) {
                nameCache.put(uuid, name);
                return name;
            }
        }

        // Fallback to Bukkit
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String name = player.getName();
        if (name == null) {
            name = uuid.toString().substring(0, 8);
        }
        nameCache.put(uuid, name);
        return name;
    }

    @SuppressWarnings("unchecked")
    private String lookupViaRVNKCore(UUID uuid) {
        try {
            CompletableFuture<Optional<?>> future =
                    (CompletableFuture<Optional<?>>) getPlayerMethod.invoke(playerService, uuid);

            Optional<?> result = future.get(2, TimeUnit.SECONDS);
            if (result.isPresent()) {
                return (String) getCurrentNameMethod.invoke(result.get());
            }
        } catch (Exception e) {
            logger.debug("RVNKCore lookup failed for " + uuid + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Checks if RVNKCore PlayerService is available.
     */
    public boolean isRVNKCoreEnabled() {
        return rvnkCoreEnabled;
    }

    /**
     * Clears the name cache. Call on reload.
     */
    public void clearCache() {
        nameCache.clear();
    }
}
