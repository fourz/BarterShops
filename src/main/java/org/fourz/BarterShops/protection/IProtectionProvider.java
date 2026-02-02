package org.fourz.BarterShops.protection;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for shop region protection providers.
 * Supports WorldGuard, GriefPrevention, and fallback implementations.
 * All operations are async-aware to prevent blocking the main thread.
 */
public interface IProtectionProvider {

    /**
     * Gets the provider name.
     *
     * @return Provider identifier (e.g., "worldguard", "griefprevention", "none")
     */
    String getProviderName();

    /**
     * Checks if the provider is available and initialized.
     *
     * @return true if the provider is ready to use
     */
    boolean isAvailable();

    /**
     * Creates a protected region around a shop location.
     *
     * @param shopId Shop identifier (unique shop UUID or location hash)
     * @param center Center location of the shop
     * @param radius Protection radius in blocks
     * @param owner Owner UUID of the shop
     * @return CompletableFuture containing success status
     */
    CompletableFuture<Boolean> createProtectedRegion(String shopId, Location center, int radius, UUID owner);

    /**
     * Removes a protected region for a shop.
     *
     * @param shopId Shop identifier
     * @param center Center location of the shop
     * @return CompletableFuture containing success status
     */
    CompletableFuture<Boolean> removeProtectedRegion(String shopId, Location center);

    /**
     * Checks if a location is within a protected shop region.
     *
     * @param location Location to check
     * @return CompletableFuture containing true if location is protected
     */
    CompletableFuture<Boolean> isLocationProtected(Location location);

    /**
     * Gets the shop ID for a protected region at the given location.
     *
     * @param location Location to check
     * @return CompletableFuture containing shop ID or null if not protected
     */
    CompletableFuture<String> getShopIdAtLocation(Location location);

    /**
     * Checks if a player can build/modify blocks at the location.
     * This considers shop ownership and protection rules.
     *
     * @param player Player to check
     * @param location Location to check
     * @return CompletableFuture containing true if player can build
     */
    CompletableFuture<Boolean> canPlayerBuild(Player player, Location location);

    /**
     * Counts the number of protected shops owned by a player.
     *
     * @param owner Owner UUID
     * @return CompletableFuture containing shop count
     */
    CompletableFuture<Integer> getProtectedShopCount(UUID owner);

    /**
     * Gets information about the protected region at a location.
     *
     * @param location Location to check
     * @return CompletableFuture containing region info or null if not protected
     */
    CompletableFuture<ProtectionInfo> getProtectionInfo(Location location);

    /**
     * Resizes a protected region.
     *
     * @param shopId Shop identifier
     * @param center Center location
     * @param newRadius New radius in blocks
     * @return CompletableFuture containing success status
     */
    CompletableFuture<Boolean> resizeProtectedRegion(String shopId, Location center, int newRadius);

    /**
     * Cleans up resources when the provider is disabled.
     */
    void shutdown();

    /**
     * Protection information container.
     */
    record ProtectionInfo(
            String shopId,
            UUID owner,
            Location center,
            int radius,
            String providerType
    ) {}
}
