package org.fourz.BarterShops.container;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;

/**
 * Re-resolves a shop's container from the world.
 *
 * A {@link Container} BlockState wraps the block entity that existed when getState() ran.
 * Once the chunk unloads and reloads, that block entity is detached: its inventory still
 * answers stock checks, but items taken from it are free and payments put into it vanish,
 * while the real chest keeps its stock. Shops keep a Container from creation or boot
 * hydration, so every inventory read must go through here.
 */
public final class LiveContainer {

    private LiveContainer() {}

    /**
     * Returns the container currently in the world at the stored container's location.
     * Falls back to the stored container off the main thread, when the chunk is not loaded
     * (never load a chunk just to answer a read), or when the block is no longer a container.
     */
    public static Container resolve(Container stored) {
        if (stored == null) return null;
        return resolve(stored.getLocation(), stored);
    }

    /**
     * Returns the container currently in the world at {@code location}, or {@code fallback}.
     */
    public static Container resolve(Location location, Container fallback) {
        if (location == null || !Bukkit.isPrimaryThread()) return fallback;
        World world = location.getWorld();
        if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return fallback;
        }
        BlockState state = location.getBlock().getState();
        return state instanceof Container live ? live : fallback;
    }
}
