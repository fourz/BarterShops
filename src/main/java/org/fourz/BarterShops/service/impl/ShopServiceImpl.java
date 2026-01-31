package org.fourz.BarterShops.service.impl;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.service.IShopService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Concrete implementation of IShopService for RVNKCore ServiceRegistry.
 *
 * <p>Currently uses in-memory storage. Will be updated to use IShopRepository
 * once database implementation is complete.</p>
 *
 * <p>All methods return CompletableFuture for non-blocking operations.</p>
 */
public class ShopServiceImpl implements IShopService {

    private static final String CLASS_NAME = "ShopServiceImpl";

    private final BarterShops plugin;
    private final LogManager logger;

    // In-memory shop storage (will be replaced by IShopRepository)
    private final Map<String, ShopDataDTO> shopsById;
    private final Map<String, String> shopsByLocation; // "world:x:y:z" -> shopId
    private final AtomicInteger nextShopId;

    private volatile boolean fallbackMode = false;

    public ShopServiceImpl(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, CLASS_NAME);
        this.shopsById = new ConcurrentHashMap<>();
        this.shopsByLocation = new ConcurrentHashMap<>();
        this.nextShopId = new AtomicInteger(1);

        logger.info("ShopServiceImpl initialized (in-memory mode)");
    }

    // ========================================================
    // Shop Queries
    // ========================================================

    @Override
    public CompletableFuture<Optional<ShopDataDTO>> getShopById(String shopId) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debug("Getting shop by ID: " + shopId);
            return Optional.ofNullable(shopsById.get(shopId));
        });
    }

    @Override
    public CompletableFuture<Optional<ShopDataDTO>> getShopAtLocation(Location location) {
        return CompletableFuture.supplyAsync(() -> {
            String locationKey = toLocationKey(location);
            logger.debug("Getting shop at location: " + locationKey);
            String shopId = shopsByLocation.get(locationKey);
            return shopId != null ? Optional.ofNullable(shopsById.get(shopId)) : Optional.empty();
        });
    }

    @Override
    public CompletableFuture<Optional<ShopDataDTO>> getShopBySign(Block signBlock) {
        return getShopAtLocation(signBlock.getLocation());
    }

    @Override
    public CompletableFuture<List<ShopDataDTO>> getShopsByOwner(UUID ownerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debug("Getting shops for owner: " + ownerUuid);
            return shopsById.values().stream()
                .filter(shop -> shop.ownerUuid().equals(ownerUuid))
                .collect(Collectors.toList());
        });
    }

    @Override
    public CompletableFuture<List<ShopDataDTO>> getAllShops() {
        return CompletableFuture.supplyAsync(() -> {
            logger.debug("Getting all shops (" + shopsById.size() + " total)");
            return new ArrayList<>(shopsById.values());
        });
    }

    @Override
    public CompletableFuture<List<ShopDataDTO>> getShopsNearby(Location center, double radius) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debug("Getting shops within " + radius + " blocks of " + center);
            double radiusSquared = radius * radius;
            return shopsById.values().stream()
                .filter(shop -> {
                    Location shopLoc = shop.getSignLocation();
                    return shopLoc != null
                        && shopLoc.getWorld() != null
                        && center.getWorld() != null
                        && shopLoc.getWorld().equals(center.getWorld())
                        && shopLoc.distanceSquared(center) <= radiusSquared;
                })
                .collect(Collectors.toList());
        });
    }

    // ========================================================
    // Shop Management
    // ========================================================

    @Override
    public CompletableFuture<ShopDataDTO> createShop(UUID ownerUuid, Location location, String shopName) {
        return CompletableFuture.supplyAsync(() -> {
            int shopId = nextShopId.getAndIncrement();
            String shopIdStr = String.valueOf(shopId);
            String name = shopName != null ? shopName : "Shop-" + shopId;

            logger.info("Creating shop '" + name + "' at " + toLocationKey(location));

            ShopDataDTO shop = ShopDataDTO.builder()
                .shopId(shopId)
                .ownerUuid(ownerUuid)
                .shopName(name)
                .shopType(ShopDataDTO.ShopType.BARTER)
                .signLocation(location)
                .isActive(true)
                .createdAt(new Timestamp(System.currentTimeMillis()))
                .lastModified(new Timestamp(System.currentTimeMillis()))
                .build();

            shopsById.put(shopIdStr, shop);
            shopsByLocation.put(toLocationKey(location), shopIdStr);

            logger.info("Created shop: " + shopIdStr);
            return shop;
        });
    }

    @Override
    public CompletableFuture<Boolean> removeShop(String shopId) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Removing shop: " + shopId);
            ShopDataDTO shop = shopsById.remove(shopId);
            if (shop != null) {
                Location loc = shop.getSignLocation();
                if (loc != null) {
                    shopsByLocation.remove(toLocationKey(loc));
                }
                logger.info("Removed shop: " + shopId);
                return true;
            }
            logger.debug("Shop not found for removal: " + shopId);
            return false;
        });
    }

    @Override
    public CompletableFuture<Boolean> updateShop(String shopId, ShopUpdateRequest updates) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debug("Updating shop: " + shopId);
            ShopDataDTO existing = shopsById.get(shopId);
            if (existing == null) {
                logger.debug("Shop not found for update: " + shopId);
                return false;
            }

            // Build updated shop using existing values where updates are null
            ShopDataDTO.Builder builder = ShopDataDTO.builder()
                .shopId(existing.shopId())
                .ownerUuid(existing.ownerUuid())
                .shopName(updates.newName() != null ? updates.newName() : existing.shopName())
                .shopType(updates.shopType() != null ? updates.shopType() : existing.shopType())
                .signLocation(existing.locationWorld(), existing.locationX(), existing.locationY(), existing.locationZ())
                .isActive(updates.active() != null ? updates.active() : existing.isActive())
                .createdAt(existing.createdAt())
                .lastModified(new Timestamp(System.currentTimeMillis()));

            // Handle chest location update
            if (updates.chestLocation() != null) {
                builder.chestLocation(updates.chestLocation());
            } else if (existing.chestLocationWorld() != null) {
                builder.chestLocation(
                    existing.chestLocationWorld(),
                    existing.chestLocationX(),
                    existing.chestLocationY(),
                    existing.chestLocationZ()
                );
            }

            // Merge metadata
            Map<String, String> metadata = new HashMap<>(existing.metadata());
            if (updates.metadataUpdates() != null) {
                metadata.putAll(updates.metadataUpdates());
            }
            builder.metadata(metadata);

            ShopDataDTO updated = builder.build();
            shopsById.put(shopId, updated);

            logger.info("Updated shop: " + shopId);
            return true;
        });
    }

    // ========================================================
    // Shop Statistics
    // ========================================================

    @Override
    public CompletableFuture<Integer> getShopCount() {
        return CompletableFuture.completedFuture(shopsById.size());
    }

    @Override
    public CompletableFuture<Integer> getShopCountByOwner(UUID ownerUuid) {
        return CompletableFuture.supplyAsync(() ->
            (int) shopsById.values().stream()
                .filter(shop -> shop.ownerUuid().equals(ownerUuid))
                .count()
        );
    }

    // ========================================================
    // Fallback Mode
    // ========================================================

    @Override
    public boolean isInFallbackMode() {
        return fallbackMode;
    }

    /**
     * Sets the fallback mode state.
     *
     * @param fallback true to enable fallback mode
     */
    public void setFallbackMode(boolean fallback) {
        this.fallbackMode = fallback;
        logger.info("Fallback mode " + (fallback ? "enabled" : "disabled"));
    }

    // ========================================================
    // Helper Methods
    // ========================================================

    /**
     * Creates a location key for the location index.
     */
    private String toLocationKey(Location location) {
        return location.getWorld().getName() + ":"
            + location.getBlockX() + ":"
            + location.getBlockY() + ":"
            + location.getBlockZ();
    }

    /**
     * Gets the plugin instance.
     */
    public BarterShops getPlugin() {
        return plugin;
    }
}
