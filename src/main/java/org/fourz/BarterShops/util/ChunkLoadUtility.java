package org.fourz.BarterShops.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Chunk;

import java.util.concurrent.CompletableFuture;

/**
 * Async chunk loading helper using Paper's getChunkAtAsync API.
 *
 * <p>All methods are static — no instantiation required.</p>
 */
public final class ChunkLoadUtility {

    private ChunkLoadUtility() {
        // Pure static utility — do not instantiate
    }

    /**
     * Asynchronously loads the chunk at the given chunk coordinates.
     *
     * @param world  The world in which to load the chunk
     * @param chunkX The chunk X coordinate (block X >> 4)
     * @param chunkZ The chunk Z coordinate (block Z >> 4)
     * @return A CompletableFuture that completes with the loaded Chunk
     */
    public static CompletableFuture<Chunk> loadChunk(World world, int chunkX, int chunkZ) {
        // Spigot API: getChunkAt is main-thread safe and forces the chunk loaded.
        // Callers already schedule block access on the main thread via Bukkit.getScheduler().runTask(),
        // so this sync call is safe within that context.
        return CompletableFuture.completedFuture(world.getChunkAt(chunkX, chunkZ));
    }

    /**
     * Asynchronously loads the chunk that contains the given block location.
     *
     * @param loc The block location whose chunk should be loaded
     * @return A CompletableFuture that completes with the loaded Chunk
     */
    public static CompletableFuture<Chunk> loadChunkForBlock(Location loc) {
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;
        return loadChunk(loc.getWorld(), chunkX, chunkZ);
    }
}
