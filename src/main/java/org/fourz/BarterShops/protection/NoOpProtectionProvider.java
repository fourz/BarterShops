package org.fourz.BarterShops.protection;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * No-operation protection provider.
 * Used as fallback when no protection plugin is available.
 * All operations succeed but provide no actual protection.
 */
public class NoOpProtectionProvider implements IProtectionProvider {

    @Override
    public String getProviderName() {
        return "none";
    }

    @Override
    public boolean isAvailable() {
        return true; // Always available as fallback
    }

    @Override
    public CompletableFuture<Boolean> createProtectedRegion(String shopId, Location center, int radius, UUID owner) {
        // No-op: no protection created
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Boolean> removeProtectedRegion(String shopId, Location center) {
        // No-op: nothing to remove
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Boolean> isLocationProtected(Location location) {
        // No protection available
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<String> getShopIdAtLocation(Location location) {
        // No region tracking
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Boolean> canPlayerBuild(Player player, Location location) {
        // No restrictions - rely on server default permissions
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Integer> getProtectedShopCount(UUID owner) {
        // No tracking
        return CompletableFuture.completedFuture(0);
    }

    @Override
    public CompletableFuture<ProtectionInfo> getProtectionInfo(Location location) {
        // No protection info available
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Boolean> resizeProtectedRegion(String shopId, Location center, int newRadius) {
        // No-op: nothing to resize
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public void shutdown() {
        // No cleanup needed
    }
}
