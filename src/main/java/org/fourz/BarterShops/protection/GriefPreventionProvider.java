package org.fourz.BarterShops.protection;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * GriefPrevention integration for shop region protection.
 * Creates sub-claims around shops within existing player claims.
 * Note: GriefPrevention requires parent claims - this provider tracks metadata.
 */
public class GriefPreventionProvider implements IProtectionProvider {

    private final BarterShops plugin;
    private final LogManager logger;
    private final GriefPrevention griefPrevention;
    private boolean available;

    // Track shop regions (GriefPrevention doesn't support custom region metadata)
    private final Map<String, ShopRegionData> shopRegions;

    public GriefPreventionProvider(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "GriefPreventionProvider");
        this.shopRegions = new HashMap<>();

        // Initialize GriefPrevention integration
        try {
            this.griefPrevention = GriefPrevention.instance;
            this.available = griefPrevention != null;

            if (available) {
                logger.info("GriefPrevention integration initialized");
            } else {
                logger.warning("GriefPrevention plugin found but instance is null");
            }
        } catch (Exception e) {
            logger.error("Failed to initialize GriefPrevention integration", e);
            throw new RuntimeException("GriefPrevention initialization failed", e);
        }
    }

    @Override
    public String getProviderName() {
        return "griefprevention";
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
                    logger.warning("Cannot create region - GriefPrevention not available");
                    return false;
                }

                // Check if location is already claimed
                Claim existingClaim = griefPrevention.dataStore.getClaimAt(center, false, null);

                if (existingClaim == null) {
                    logger.warning("Cannot create shop protection - no GriefPrevention claim exists at location");
                    logger.info("Player must create a claim first, then place shops within it");
                    // Store metadata anyway for tracking
                    shopRegions.put(shopId, new ShopRegionData(shopId, owner, center, radius));
                    return false;
                }

                // Verify claim ownership
                if (!existingClaim.ownerID.equals(owner)) {
                    logger.warning("Cannot create shop - player doesn't own the claim at this location");
                    return false;
                }

                // Store shop region data
                shopRegions.put(shopId, new ShopRegionData(shopId, owner, center, radius));

                logger.info("Registered shop region in GriefPrevention claim: " + shopId);
                logger.debug("Shop will be protected by existing claim ID: " + existingClaim.getID());

                return true;

            } catch (Exception e) {
                logger.error("Failed to create GriefPrevention shop region: " + shopId, e);
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

                ShopRegionData removed = shopRegions.remove(shopId);
                if (removed != null) {
                    logger.info("Removed shop region tracking: " + shopId);
                    return true;
                } else {
                    logger.debug("Shop region not found for removal: " + shopId);
                    return false;
                }

            } catch (Exception e) {
                logger.error("Failed to remove shop region: " + shopId, e);
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

                // Check if location is within a GriefPrevention claim
                Claim claim = griefPrevention.dataStore.getClaimAt(location, false, null);
                return claim != null;

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

                // Find shop region containing this location
                return shopRegions.entrySet().stream()
                        .filter(entry -> isLocationInRegion(location, entry.getValue()))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(null);

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

                Claim claim = griefPrevention.dataStore.getClaimAt(location, false, null);
                if (claim == null) {
                    return true; // No claim = no restriction
                }

                // Check if player is claim owner or has build permission
                if (claim.ownerID.equals(player.getUniqueId())) {
                    return true;
                }

                // Check GriefPrevention build permission
                String denyReason = claim.allowBuild(player, location.getBlock().getType());
                if (denyReason != null) {
                    logger.debug("Player " + player.getName() + " denied build: " + denyReason);
                    return false;
                }

                // Check admin permission
                return player.hasPermission("bartershops.admin") || player.isOp();

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

                return (int) shopRegions.values().stream()
                        .filter(data -> data.owner.equals(owner))
                        .count();

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

                // Find shop region at this location
                Map.Entry<String, ShopRegionData> entry = shopRegions.entrySet().stream()
                        .filter(e -> isLocationInRegion(location, e.getValue()))
                        .findFirst()
                        .orElse(null);

                if (entry == null) {
                    return null;
                }

                ShopRegionData data = entry.getValue();
                return new ProtectionInfo(
                        data.shopId,
                        data.owner,
                        data.center,
                        data.radius,
                        "griefprevention"
                );

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

                ShopRegionData data = shopRegions.get(shopId);
                if (data == null) {
                    logger.warning("Shop region not found for resize: " + shopId);
                    return false;
                }

                // Update radius
                data.radius = newRadius;
                logger.info("Resized shop region: " + shopId + " to radius " + newRadius);
                return true;

            } catch (Exception e) {
                logger.error("Failed to resize shop region: " + shopId, e);
                return false;
            }
        });
    }

    @Override
    public void shutdown() {
        logger.debug("GriefPreventionProvider shutdown");
        shopRegions.clear();
        available = false;
    }

    /**
     * Checks if a location is within a shop region's radius.
     */
    private boolean isLocationInRegion(Location location, ShopRegionData region) {
        if (!location.getWorld().equals(region.center.getWorld())) {
            return false;
        }

        double distance = location.distance(region.center);
        return distance <= region.radius;
    }

    /**
     * Internal data class for tracking shop regions.
     */
    private static class ShopRegionData {
        final String shopId;
        final UUID owner;
        final Location center;
        int radius;

        ShopRegionData(String shopId, UUID owner, Location center, int radius) {
            this.shopId = shopId;
            this.owner = owner;
            this.center = center;
            this.radius = radius;
        }
    }
}
