package org.fourz.BarterShops.protection;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * WorldGuard integration for shop region protection.
 * Creates cuboid regions around shops with owner permissions.
 */
public class WorldGuardProvider implements IProtectionProvider {

    private static final String REGION_PREFIX = "bartershop_";

    private final BarterShops plugin;
    private final LogManager logger;
    private final WorldGuard worldGuard;
    private final WorldGuardPlugin worldGuardPlugin;
    private boolean available;

    public WorldGuardProvider(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "WorldGuardProvider");

        // Initialize WorldGuard integration
        try {
            this.worldGuard = WorldGuard.getInstance();
            this.worldGuardPlugin = WorldGuardPlugin.inst();
            this.available = worldGuard != null && worldGuardPlugin != null;

            if (available) {
                logger.info("WorldGuard integration initialized");
            } else {
                logger.warning("WorldGuard plugin found but initialization failed");
            }
        } catch (Exception e) {
            logger.error("Failed to initialize WorldGuard integration", e);
            throw new RuntimeException("WorldGuard initialization failed", e);
        }
    }

    @Override
    public String getProviderName() {
        return "worldguard";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public CompletableFuture<Boolean> createProtectedRegion(String shopId, Location center, int radius, UUID owner) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!available) {
                    logger.warning("Cannot create region - WorldGuard not available");
                    return false;
                }

                String regionId = REGION_PREFIX + shopId;
                RegionManager regionManager = worldGuard.getPlatform().getRegionContainer()
                        .get(BukkitAdapter.adapt(center.getWorld()));

                if (regionManager == null) {
                    logger.warning("RegionManager is null for world: " + center.getWorld().getName());
                    return false;
                }

                // Check if region already exists
                if (regionManager.hasRegion(regionId)) {
                    logger.debug("Region already exists: " + regionId);
                    return true;
                }

                // Create cuboid region
                BlockVector3 min = BlockVector3.at(
                        center.getBlockX() - radius,
                        center.getBlockY() - radius,
                        center.getBlockZ() - radius
                );
                BlockVector3 max = BlockVector3.at(
                        center.getBlockX() + radius,
                        center.getBlockY() + radius,
                        center.getBlockZ() + radius
                );

                ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionId, min, max);

                // Set owner
                region.getOwners().addPlayer(owner);

                // Set flags for shop protection
                region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.DENY);
                region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.DENY);
                region.setFlag(Flags.INTERACT, StateFlag.State.ALLOW); // Allow sign interaction
                region.setFlag(Flags.CHEST_ACCESS, StateFlag.State.DENY); // Protect shop chest

                // Add region to manager
                regionManager.addRegion(region);

                logger.info("Created WorldGuard region: " + regionId + " at " +
                        center.getBlockX() + "," + center.getBlockY() + "," + center.getBlockZ());

                return true;

            } catch (Exception e) {
                logger.error("Failed to create WorldGuard region for shop: " + shopId, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> removeProtectedRegion(String shopId, Location center) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!available) {
                    return false;
                }

                String regionId = REGION_PREFIX + shopId;
                RegionManager regionManager = worldGuard.getPlatform().getRegionContainer()
                        .get(BukkitAdapter.adapt(center.getWorld()));

                if (regionManager == null) {
                    return false;
                }

                // Remove region and check if it existed
                ProtectedRegion region = regionManager.getRegion(regionId);
                if (region != null) {
                    regionManager.removeRegion(regionId);
                    logger.info("Removed WorldGuard region: " + regionId);
                    return true;
                } else {
                    logger.debug("Region not found for removal: " + regionId);
                    return false;
                }

            } catch (Exception e) {
                logger.error("Failed to remove WorldGuard region: " + shopId, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> isLocationProtected(Location location) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!available) {
                    return false;
                }

                RegionManager regionManager = worldGuard.getPlatform().getRegionContainer()
                        .get(BukkitAdapter.adapt(location.getWorld()));

                if (regionManager == null) {
                    return false;
                }

                BlockVector3 vector = BukkitAdapter.asBlockVector(location);
                return regionManager.getApplicableRegions(vector).getRegions().stream()
                        .anyMatch(r -> r.getId().startsWith(REGION_PREFIX));

            } catch (Exception e) {
                logger.error("Failed to check location protection", e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<String> getShopIdAtLocation(Location location) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!available) {
                    return null;
                }

                RegionManager regionManager = worldGuard.getPlatform().getRegionContainer()
                        .get(BukkitAdapter.adapt(location.getWorld()));

                if (regionManager == null) {
                    return null;
                }

                BlockVector3 vector = BukkitAdapter.asBlockVector(location);
                Optional<ProtectedRegion> regionOpt = regionManager.getApplicableRegions(vector)
                        .getRegions().stream()
                        .filter(r -> r.getId().startsWith(REGION_PREFIX))
                        .findFirst();

                return regionOpt.map(r -> r.getId().substring(REGION_PREFIX.length())).orElse(null);

            } catch (Exception e) {
                logger.error("Failed to get shop ID at location", e);
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> canPlayerBuild(Player player, Location location) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!available) {
                    return true;
                }

                RegionManager regionManager = worldGuard.getPlatform().getRegionContainer()
                        .get(BukkitAdapter.adapt(location.getWorld()));

                if (regionManager == null) {
                    return true;
                }

                BlockVector3 vector = BukkitAdapter.asBlockVector(location);
                Optional<ProtectedRegion> regionOpt = regionManager.getApplicableRegions(vector)
                        .getRegions().stream()
                        .filter(r -> r.getId().startsWith(REGION_PREFIX))
                        .findFirst();

                if (regionOpt.isEmpty()) {
                    return true; // No shop region
                }

                ProtectedRegion region = regionOpt.get();

                // Check if player is owner or admin
                return region.getOwners().contains(player.getUniqueId()) ||
                        player.hasPermission("bartershops.admin") ||
                        player.isOp();

            } catch (Exception e) {
                logger.error("Failed to check player build permission", e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Integer> getProtectedShopCount(UUID owner) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!available) {
                    return 0;
                }

                int count = 0;

                // Check all loaded worlds
                for (org.bukkit.World world : plugin.getServer().getWorlds()) {
                    RegionManager regionManager = worldGuard.getPlatform().getRegionContainer()
                            .get(BukkitAdapter.adapt(world));

                    if (regionManager != null) {
                        count += (int) regionManager.getRegions().values().stream()
                                .filter(r -> r.getId().startsWith(REGION_PREFIX))
                                .filter(r -> r.getOwners().contains(owner))
                                .count();
                    }
                }

                return count;

            } catch (Exception e) {
                logger.error("Failed to count protected shops", e);
                return 0;
            }
        });
    }

    @Override
    public CompletableFuture<ProtectionInfo> getProtectionInfo(Location location) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!available) {
                    return null;
                }

                RegionManager regionManager = worldGuard.getPlatform().getRegionContainer()
                        .get(BukkitAdapter.adapt(location.getWorld()));

                if (regionManager == null) {
                    return null;
                }

                BlockVector3 vector = BukkitAdapter.asBlockVector(location);
                Optional<ProtectedRegion> regionOpt = regionManager.getApplicableRegions(vector)
                        .getRegions().stream()
                        .filter(r -> r.getId().startsWith(REGION_PREFIX))
                        .findFirst();

                if (regionOpt.isEmpty()) {
                    return null;
                }

                ProtectedRegion region = regionOpt.get();
                String shopId = region.getId().substring(REGION_PREFIX.length());
                UUID owner = region.getOwners().getUniqueIds().stream().findFirst().orElse(null);

                // Calculate radius and center
                BlockVector3 min = region.getMinimumPoint();
                BlockVector3 max = region.getMaximumPoint();
                int radius = (max.getX() - min.getX()) / 2;
                Location center = new Location(
                        location.getWorld(),
                        (min.getX() + max.getX()) / 2.0,
                        (min.getY() + max.getY()) / 2.0,
                        (min.getZ() + max.getZ()) / 2.0
                );

                return new ProtectionInfo(shopId, owner, center, radius, "worldguard");

            } catch (Exception e) {
                logger.error("Failed to get protection info", e);
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> resizeProtectedRegion(String shopId, Location center, int newRadius) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!available) {
                    return false;
                }

                String regionId = REGION_PREFIX + shopId;
                RegionManager regionManager = worldGuard.getPlatform().getRegionContainer()
                        .get(BukkitAdapter.adapt(center.getWorld()));

                if (regionManager == null) {
                    return false;
                }

                ProtectedRegion region = regionManager.getRegion(regionId);
                if (region == null) {
                    logger.warning("Region not found for resize: " + regionId);
                    return false;
                }

                // Update region bounds
                BlockVector3 min = BlockVector3.at(
                        center.getBlockX() - newRadius,
                        center.getBlockY() - newRadius,
                        center.getBlockZ() - newRadius
                );
                BlockVector3 max = BlockVector3.at(
                        center.getBlockX() + newRadius,
                        center.getBlockY() + newRadius,
                        center.getBlockZ() + newRadius
                );

                if (region instanceof ProtectedCuboidRegion) {
                    ProtectedCuboidRegion cuboid = (ProtectedCuboidRegion) region;
                    cuboid.setMinimumPoint(min);
                    cuboid.setMaximumPoint(max);
                    logger.info("Resized WorldGuard region: " + regionId + " to radius " + newRadius);
                    return true;
                }

                return false;

            } catch (Exception e) {
                logger.error("Failed to resize WorldGuard region: " + shopId, e);
                return false;
            }
        });
    }

    @Override
    public void shutdown() {
        logger.debug("WorldGuardProvider shutdown");
        available = false;
    }
}
