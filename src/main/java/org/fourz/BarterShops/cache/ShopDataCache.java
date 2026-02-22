package org.fourz.BarterShops.cache;

import org.fourz.BarterShops.data.dto.ShopDataDTO;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TTL-based cache for frequently accessed shop data.
 * Reduces database queries and improves performance.
 * Cache entries expire after 5 minutes or on explicit invalidation.
 */
public class ShopDataCache {
    private static final long TTL_MILLIS = 5 * 60 * 1000; // 5 minutes
    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long creationTime = System.currentTimeMillis();

    /**
     * Cache entry wrapper with timestamp.
     */
    private static class CacheEntry {
        final ShopDataDTO data;
        final long createdAt;

        CacheEntry(ShopDataDTO data) {
            this.data = data;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > TTL_MILLIS;
        }

        long getAgeMillis() {
            return System.currentTimeMillis() - createdAt;
        }
    }

    /**
     * Gets a cached shop, or empty Optional if expired/not found.
     *
     * @param shopId The shop ID to look up
     * @return Optional containing the cached shop, or empty if not found/expired
     */
    public Optional<ShopDataDTO> get(UUID shopId) {
        CacheEntry entry = cache.get(shopId);
        if (entry == null) {
            return Optional.empty();
        }

        if (entry.isExpired()) {
            cache.remove(shopId);
            return Optional.empty();
        }

        return Optional.of(entry.data);
    }

    /**
     * Puts a shop in the cache.
     *
     * @param shopId The shop ID to cache
     * @param shop The shop data to cache
     */
    public void put(UUID shopId, ShopDataDTO shop) {
        cache.put(shopId, new CacheEntry(shop));
    }

    /**
     * Invalidates a specific cache entry.
     * Called when a shop is modified.
     *
     * @param shopId The shop ID to invalidate
     */
    public void invalidate(UUID shopId) {
        cache.remove(shopId);
    }

    /**
     * Invalidates all cache entries.
     * Called when cache needs full reset.
     */
    public void invalidateAll() {
        cache.clear();
    }

    /**
     * Cleans up expired entries.
     * Can be called periodically to reclaim memory.
     */
    public void cleanup() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Gets the number of valid entries in cache.
     */
    public int getValidEntryCount() {
        cleanup(); // Remove expired entries first
        return cache.size();
    }

    /**
     * Gets cache statistics for debugging.
     *
     * @return String with cache status information
     */
    public String getStats() {
        int totalEntries = cache.size();
        int validEntries = 0;
        long totalAgeMillis = 0;

        for (CacheEntry entry : cache.values()) {
            if (!entry.isExpired()) {
                validEntries++;
                totalAgeMillis += entry.getAgeMillis();
            }
        }

        double avgAgeSeconds = validEntries > 0 ? totalAgeMillis / 1000.0 / validEntries : 0;
        int expiredEntries = totalEntries - validEntries;
        long cacheUptimeMillis = System.currentTimeMillis() - creationTime;
        long cacheUptimeSeconds = cacheUptimeMillis / 1000;

        return String.format(
            "ShopDataCache[uptime=%ds, entries=%d, valid=%d, expired=%d, avgAge=%.1fs]",
            cacheUptimeSeconds,
            totalEntries,
            validEntries,
            expiredEntries,
            avgAgeSeconds
        );
    }

    /**
     * Gets the TTL for cache entries in milliseconds.
     */
    public static long getTtlMillis() {
        return TTL_MILLIS;
    }

    /**
     * Gets the TTL for cache entries in seconds.
     */
    public static long getTtlSeconds() {
        return TTL_MILLIS / 1000;
    }
}
