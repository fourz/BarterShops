package org.fourz.BarterShops.service.impl;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.repository.IShopRepository;
import org.fourz.BarterShops.data.repository.impl.ShopRepositoryImpl;
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
 * <p>Delegates to IShopRepository for database operations. Falls back to
 * in-memory storage when repository is unavailable or in fallback mode.</p>
 *
 * <p>All methods return CompletableFuture for non-blocking operations.</p>
 */
public class ShopServiceImpl implements IShopService {

    private static final String CLASS_NAME = "ShopServiceImpl";

    private final BarterShops plugin;
    private final LogManager logger;
    private final IShopRepository repository;

    // In-memory fallback storage (used when repository is unavailable)
    private final Map<String, ShopDataDTO> fallbackShopsById;
    private final Map<String, String> fallbackShopsByLocation; // "world:x:y:z" -> shopId
    private final AtomicInteger fallbackNextShopId;

    /**
     * Creates a ShopServiceImpl with database repository support.
     *
     * @param plugin The BarterShops plugin instance
     * @param repository The shop repository for database operations (can be null for in-memory mode)
     */
    public ShopServiceImpl(BarterShops plugin, IShopRepository repository) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, CLASS_NAME);
        this.repository = repository;

        // Initialize fallback storage (always available)
        this.fallbackShopsById = new ConcurrentHashMap<>();
        this.fallbackShopsByLocation = new ConcurrentHashMap<>();
        this.fallbackNextShopId = new AtomicInteger(1);

        if (repository != null) {
            logger.info("ShopServiceImpl initialized (database mode)");
        } else {
            logger.info("ShopServiceImpl initialized (in-memory fallback mode)");
        }
    }

    /**
     * Legacy constructor for backwards compatibility.
     * Creates service in in-memory mode.
     *
     * @param plugin The BarterShops plugin instance
     */
    public ShopServiceImpl(BarterShops plugin) {
        this(plugin, null);
    }

    /**
     * Checks if using fallback mode (no repository or repository in fallback).
     */
    private boolean usesFallback() {
        if (repository == null) {
            return true;
        }
        if (repository instanceof ShopRepositoryImpl) {
            return ((ShopRepositoryImpl) repository).isInFallbackMode();
        }
        return false;
    }

    // ========================================================
    // Shop Queries
    // ========================================================

    @Override
    public CompletableFuture<Optional<ShopDataDTO>> getShopById(String shopId) {
        if (usesFallback()) {
            return CompletableFuture.supplyAsync(() -> {
                logger.debug("Getting shop by ID (fallback): " + shopId);
                return Optional.ofNullable(fallbackShopsById.get(shopId));
            });
        }

        logger.debug("Getting shop by ID: " + shopId);
        try {
            int id = Integer.parseInt(shopId);
            return repository.findById(id);
        } catch (NumberFormatException e) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    @Override
    public CompletableFuture<Optional<ShopDataDTO>> getShopAtLocation(Location location) {
        if (usesFallback()) {
            return CompletableFuture.supplyAsync(() -> {
                String locationKey = toLocationKey(location);
                logger.debug("Getting shop at location (fallback): " + locationKey);
                String shopId = fallbackShopsByLocation.get(locationKey);
                return shopId != null ? Optional.ofNullable(fallbackShopsById.get(shopId)) : Optional.empty();
            });
        }

        logger.debug("Getting shop at location: " + toLocationKey(location));
        return repository.findBySignLocation(
            location.getWorld().getName(),
            location.getX(),
            location.getY(),
            location.getZ()
        );
    }

    @Override
    public CompletableFuture<Optional<ShopDataDTO>> getShopBySign(Block signBlock) {
        return getShopAtLocation(signBlock.getLocation());
    }

    @Override
    public CompletableFuture<List<ShopDataDTO>> getShopsByOwner(UUID ownerUuid) {
        if (usesFallback()) {
            return CompletableFuture.supplyAsync(() -> {
                logger.debug("Getting shops for owner (fallback): " + ownerUuid);
                return fallbackShopsById.values().stream()
                    .filter(shop -> shop.ownerUuid().equals(ownerUuid))
                    .collect(Collectors.toList());
            });
        }

        logger.debug("Getting shops for owner: " + ownerUuid);
        return repository.findByOwner(ownerUuid);
    }

    @Override
    public CompletableFuture<List<ShopDataDTO>> getAllShops() {
        if (usesFallback()) {
            return CompletableFuture.supplyAsync(() -> {
                logger.debug("Getting all shops (fallback): " + fallbackShopsById.size() + " total");
                return new ArrayList<>(fallbackShopsById.values());
            });
        }

        logger.debug("Getting all shops from repository");
        return repository.findAll();
    }

    @Override
    public CompletableFuture<List<ShopDataDTO>> getShopsNearby(Location center, double radius) {
        if (usesFallback()) {
            return CompletableFuture.supplyAsync(() -> {
                logger.debug("Getting shops within " + radius + " blocks (fallback)");
                double radiusSquared = radius * radius;
                return fallbackShopsById.values().stream()
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

        logger.debug("Getting shops within " + radius + " blocks of " + center);
        return repository.findNearby(
            center.getWorld().getName(),
            center.getX(),
            center.getY(),
            center.getZ(),
            radius
        );
    }

    // ========================================================
    // Shop Management
    // ========================================================

    @Override
    public CompletableFuture<ShopDataDTO> createShop(UUID ownerUuid, Location location, String shopName) {
        int shopId = fallbackNextShopId.getAndIncrement();
        String name = shopName != null ? shopName : "Shop-" + shopId;

        logger.info("Creating shop '" + name + "' at " + toLocationKey(location));

        ShopDataDTO shop = ShopDataDTO.builder()
            .shopId(usesFallback() ? shopId : 0) // 0 for new inserts in DB
            .ownerUuid(ownerUuid)
            .shopName(name)
            .shopType(ShopDataDTO.ShopType.BARTER)
            .signLocation(location)
            .isActive(true)
            .createdAt(new Timestamp(System.currentTimeMillis()))
            .lastModified(new Timestamp(System.currentTimeMillis()))
            .build();

        if (usesFallback()) {
            String shopIdStr = String.valueOf(shopId);
            fallbackShopsById.put(shopIdStr, shop);
            fallbackShopsByLocation.put(toLocationKey(location), shopIdStr);
            logger.info("Created shop (fallback): " + shopIdStr);
            return CompletableFuture.completedFuture(shop);
        }

        return repository.save(shop).thenApply(savedShop -> {
            logger.info("Created shop: " + savedShop.shopId());
            return savedShop;
        });
    }

    @Override
    public CompletableFuture<Boolean> removeShop(String shopId) {
        logger.info("Removing shop: " + shopId);

        if (usesFallback()) {
            return CompletableFuture.supplyAsync(() -> {
                ShopDataDTO shop = fallbackShopsById.remove(shopId);
                if (shop != null) {
                    Location loc = shop.getSignLocation();
                    if (loc != null) {
                        fallbackShopsByLocation.remove(toLocationKey(loc));
                    }
                    logger.info("Removed shop (fallback): " + shopId);
                    return true;
                }
                logger.debug("Shop not found for removal: " + shopId);
                return false;
            });
        }

        try {
            int id = Integer.parseInt(shopId);
            return repository.deleteById(id).thenApply(deleted -> {
                if (deleted) {
                    logger.info("Removed shop: " + shopId);
                } else {
                    logger.debug("Shop not found for removal: " + shopId);
                }
                return deleted;
            });
        } catch (NumberFormatException e) {
            return CompletableFuture.completedFuture(false);
        }
    }

    @Override
    public CompletableFuture<Boolean> updateShop(String shopId, ShopUpdateRequest updates) {
        logger.debug("Updating shop: " + shopId);

        if (usesFallback()) {
            return CompletableFuture.supplyAsync(() -> {
                ShopDataDTO existing = fallbackShopsById.get(shopId);
                if (existing == null) {
                    logger.debug("Shop not found for update: " + shopId);
                    return false;
                }

                ShopDataDTO updated = buildUpdatedShop(existing, updates);
                fallbackShopsById.put(shopId, updated);
                logger.info("Updated shop (fallback): " + shopId);
                return true;
            });
        }

        try {
            int id = Integer.parseInt(shopId);
            return repository.findById(id).thenCompose(optShop -> {
                if (optShop.isEmpty()) {
                    logger.debug("Shop not found for update: " + shopId);
                    return CompletableFuture.completedFuture(false);
                }

                ShopDataDTO existing = optShop.get();
                ShopDataDTO updated = buildUpdatedShop(existing, updates);

                return repository.save(updated).thenApply(saved -> {
                    logger.info("Updated shop: " + shopId);
                    return true;
                });
            });
        } catch (NumberFormatException e) {
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Builds an updated shop DTO from existing data and update request.
     */
    private ShopDataDTO buildUpdatedShop(ShopDataDTO existing, ShopUpdateRequest updates) {
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

        return builder.build();
    }

    // ========================================================
    // Shop Statistics
    // ========================================================

    @Override
    public CompletableFuture<Integer> getShopCount() {
        if (usesFallback()) {
            return CompletableFuture.completedFuture(fallbackShopsById.size());
        }
        return repository.count();
    }

    @Override
    public CompletableFuture<Integer> getShopCountByOwner(UUID ownerUuid) {
        if (usesFallback()) {
            return CompletableFuture.supplyAsync(() ->
                (int) fallbackShopsById.values().stream()
                    .filter(shop -> shop.ownerUuid().equals(ownerUuid))
                    .count()
            );
        }
        return repository.countByOwner(ownerUuid);
    }

    // ========================================================
    // Fallback Mode
    // ========================================================

    @Override
    public boolean isInFallbackMode() {
        return usesFallback();
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
