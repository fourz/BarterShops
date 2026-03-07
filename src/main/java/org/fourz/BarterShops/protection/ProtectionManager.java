package org.fourz.BarterShops.protection;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.fourz.BarterShops.BarterShops;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Main protection coordinator for BarterShops.
 * Detects and manages protection providers (WorldGuard, GriefPrevention, or fallback).
 * Follows manager lifecycle pattern with cleanup().
 */
public class ProtectionManager {

    private final BarterShops plugin;
    private final LogManager logger;
    private IProtectionProvider provider;
    private boolean enabled;
    private int autoProtectRadius;
    private int maxShopsPerPlayer;

    public ProtectionManager(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ProtectionManager");

        loadConfiguration();
        initializeProvider();

        logger.info("ProtectionManager initialized with provider: " + provider.getProviderName());
    }

    /**
     * Loads protection settings from config.yml.
     */
    private void loadConfiguration() {
        this.enabled = plugin.getConfig().getBoolean("protection.enabled", true);
        this.autoProtectRadius = plugin.getConfig().getInt("protection.auto-protect-radius", 3);
        this.maxShopsPerPlayer = plugin.getConfig().getInt("protection.max-shops-per-player", 5);

        logger.debug("Protection enabled: " + enabled);
        logger.debug("Auto-protect radius: " + autoProtectRadius);
        logger.debug("Max shops per player: " + maxShopsPerPlayer);
    }

    /**
     * Initializes the protection provider based on configuration and available plugins.
     */
    private void initializeProvider() {
        if (!enabled) {
            logger.info("Protection system disabled - using NoOp provider");
            this.provider = new NoOpProtectionProvider();
            return;
        }

        String preferredProvider = plugin.getConfig().getString("protection.provider", "auto");
        logger.debug("Preferred provider: " + preferredProvider);

        // Try preferred provider or auto-detect
        switch (preferredProvider.toLowerCase()) {
            case "worldguard":
                if (tryInitializeWorldGuard()) return;
                logger.warning("WorldGuard not available - falling back to auto-detection");
                break;

            case "griefprevention":
                if (tryInitializeGriefPrevention()) return;
                logger.warning("GriefPrevention not available - falling back to auto-detection");
                break;

            case "none":
                logger.info("Protection provider set to 'none' - using NoOp provider");
                this.provider = new NoOpProtectionProvider();
                return;

            case "auto":
            default:
                // Auto-detect: try WorldGuard first, then GriefPrevention
                if (tryInitializeWorldGuard()) return;
                if (tryInitializeGriefPrevention()) return;
                break;
        }

        // Fallback to NoOp if no provider available
        logger.info("No protection plugin detected - using NoOp provider (graceful degradation)");
        this.provider = new NoOpProtectionProvider();
    }

    /**
     * Attempts to initialize WorldGuard provider.
     *
     * @return true if successful
     */
    private boolean tryInitializeWorldGuard() {
        Plugin worldGuard = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        if (worldGuard == null || !worldGuard.isEnabled()) {
            logger.debug("WorldGuard not found");
            return false;
        }

        try {
            this.provider = new WorldGuardProvider(plugin);
            if (provider.isAvailable()) {
                logger.info("WorldGuard provider initialized successfully");
                return true;
            }
        } catch (Exception e) {
            logger.warning("Failed to initialize WorldGuard provider: " + e.getMessage());
        }

        return false;
    }

    /**
     * Attempts to initialize GriefPrevention provider.
     *
     * @return true if successful
     */
    private boolean tryInitializeGriefPrevention() {
        Plugin griefPrevention = plugin.getServer().getPluginManager().getPlugin("GriefPrevention");
        if (griefPrevention == null || !griefPrevention.isEnabled()) {
            logger.debug("GriefPrevention not found");
            return false;
        }

        try {
            this.provider = new GriefPreventionProvider(plugin);
            if (provider.isAvailable()) {
                logger.info("GriefPrevention provider initialized successfully");
                return true;
            }
        } catch (Exception e) {
            logger.warning("Failed to initialize GriefPrevention provider: " + e.getMessage());
        }

        return false;
    }

    /**
     * Checks if protection is enabled.
     *
     * @return true if protection system is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Gets the current protection provider.
     *
     * @return Active protection provider
     */
    public IProtectionProvider getProvider() {
        return provider;
    }

    /**
     * Gets the auto-protect radius from configuration.
     *
     * @return Radius in blocks
     */
    public int getAutoProtectRadius() {
        return autoProtectRadius;
    }

    /**
     * Gets the max shops per player from configuration.
     *
     * @return Maximum shop count
     */
    public int getMaxShopsPerPlayer() {
        return maxShopsPerPlayer;
    }

    /**
     * Checks if a player can create more shops based on limit.
     *
     * @param player Player to check
     * @return CompletableFuture containing true if player can create more shops
     */
    public CompletableFuture<Boolean> canPlayerCreateShop(Player player) {
        if (!enabled) {
            return CompletableFuture.completedFuture(true);
        }

        // Admins bypass limit
        if (player.hasPermission("bartershops.admin") || player.isOp()) {
            return CompletableFuture.completedFuture(true);
        }

        return provider.getProtectedShopCount(player.getUniqueId())
                .thenApply(count -> count < maxShopsPerPlayer);
    }

    /**
     * Creates protection for a new shop.
     *
     * @param shopId Shop identifier
     * @param location Shop location
     * @param owner Shop owner
     * @return CompletableFuture containing success status
     */
    public CompletableFuture<Boolean> protectShop(String shopId, Location location, UUID owner) {
        if (!enabled) {
            return CompletableFuture.completedFuture(true);
        }

        return provider.createProtectedRegion(shopId, location, autoProtectRadius, owner);
    }

    /**
     * Removes protection from a shop.
     *
     * @param shopId Shop identifier
     * @param location Shop location
     * @return CompletableFuture containing success status
     */
    public CompletableFuture<Boolean> unprotectShop(String shopId, Location location) {
        if (!enabled) {
            return CompletableFuture.completedFuture(true);
        }

        return provider.removeProtectedRegion(shopId, location);
    }

    /**
     * Gets protection information at a location.
     *
     * @param location Location to check
     * @return CompletableFuture containing protection info or null
     */
    public CompletableFuture<IProtectionProvider.ProtectionInfo> getProtectionInfo(Location location) {
        return provider.getProtectionInfo(location);
    }

    /**
     * Checks if a player can build at a location (for shop modification).
     *
     * @param player Player to check
     * @param location Location to check
     * @return CompletableFuture containing true if player can build
     */
    public CompletableFuture<Boolean> canPlayerModify(Player player, Location location) {
        if (!enabled) {
            return CompletableFuture.completedFuture(true);
        }

        return provider.canPlayerBuild(player, location);
    }

    /**
     * Reloads configuration and reinitializes provider if needed.
     */
    public void reload() {
        logger.info("Reloading ProtectionManager configuration...");

        // Cleanup old provider
        if (provider != null) {
            provider.shutdown();
        }

        // Reload config
        plugin.reloadConfig();
        loadConfiguration();
        initializeProvider();

        logger.info("ProtectionManager reloaded with provider: " + provider.getProviderName());
    }

    /**
     * Cleanup resources on plugin disable.
     */
    public void cleanup() {
        logger.debug("Cleaning up ProtectionManager...");

        if (provider != null) {
            provider.shutdown();
            provider = null;
        }
    }
}
