package org.fourz.BarterShops.service.impl;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.data.dto.OwnershipChangeResult;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.service.IShopOwnershipService;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.BarterShops.sign.SignDisplay;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of IShopOwnershipService.
 * Handles real-time ownership transfers with cache synchronization,
 * session invalidation, and multi-player broadcast.
 */
public class ShopOwnershipServiceImpl implements IShopOwnershipService {

    private final BarterShops plugin;
    private final LogManager logger;
    private final ConcurrentHashMap<Integer, Object> shopLocks = new ConcurrentHashMap<>();

    public ShopOwnershipServiceImpl(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "OwnershipService");
    }

    @Override
    public CompletableFuture<OwnershipChangeResult> transferOwnership(
        int shopId,
        UUID newOwner,
        CommandSender initiator
    ) {
        // CRITICAL: Per-shop locking to prevent race conditions
        Object lock = shopLocks.computeIfAbsent(shopId, k -> new Object());

        return CompletableFuture.supplyAsync(() -> {
            synchronized (lock) {
                try {
                    // Step 1: Permission check
                    boolean hasPermission = canChangeOwner(initiator, shopId).join();
                    if (!hasPermission) {
                        return OwnershipChangeResult.failure("No permission to change ownership");
                    }

                    // Step 2: Load existing shop
                    ShopDataDTO existingShop = plugin.getShopRepository()
                        .findById(shopId)
                        .get(5, TimeUnit.SECONDS)
                        .orElse(null);

                    if (existingShop == null) {
                        return OwnershipChangeResult.failure("Shop #" + shopId + " not found");
                    }

                    UUID oldOwner = existingShop.ownerUuid();

                    // Prevent changing to same owner
                    if (oldOwner.equals(newOwner)) {
                        return OwnershipChangeResult.failure("New owner is the same as current owner");
                    }

                    // Step 3: Build updated DTO with new owner
                    ShopDataDTO updatedShop = ShopDataDTO.builder()
                        .shopId(existingShop.shopId())
                        .ownerUuid(newOwner)  // NEW OWNER
                        .shopName(existingShop.shopName())
                        .shopType(existingShop.shopType())
                        .signLocation(existingShop.locationWorld(), existingShop.locationX(),
                            existingShop.locationY(), existingShop.locationZ())
                        .chestLocation(existingShop.chestLocationWorld(), existingShop.chestLocationX(),
                            existingShop.chestLocationY(), existingShop.chestLocationZ())
                        .isActive(existingShop.isActive())
                        .createdAt(existingShop.createdAt())
                        .lastModified(new java.sql.Timestamp(System.currentTimeMillis()))
                        .build();

                    // Step 4: Save to database
                    ShopDataDTO savedShop = plugin.getShopRepository()
                        .save(updatedShop)
                        .get(5, TimeUnit.SECONDS);

                    if (savedShop == null) {
                        return OwnershipChangeResult.failure("Database save failed");
                    }

                    // Step 5: Reload BarterSign from database (updates in-memory owner)
                    BarterSign reloadedSign = plugin.getSignManager()
                        .reloadShopFromDatabase(shopId)
                        .get(5, TimeUnit.SECONDS);

                    if (reloadedSign == null) {
                        // CRITICAL: Rollback database if cache reload fails
                        try {
                            plugin.getShopRepository().save(existingShop).join();
                        } catch (Exception e) {
                            logger.error("Failed to rollback database after reload failure", e);
                        }
                        return OwnershipChangeResult.failure("Cache reload failed - rolled back");
                    }

                    // Step 6: Invalidate active sessions
                    int tradesInvalidated = plugin.getTradeEngine().invalidateSessionsForShop(shopId);
                    plugin.getShopManager().invalidateSessionsForShop(reloadedSign);

                    // Step 7: Update sign display (automatic broadcast via sign.update())
                    Location signLoc = new Location(
                        Bukkit.getWorld(updatedShop.locationWorld()),
                        updatedShop.locationX(),
                        updatedShop.locationY(),
                        updatedShop.locationZ()
                    );

                    if (signLoc.getWorld() != null) {
                        BlockState state = signLoc.getBlock().getState();
                        if (state instanceof Sign sign) {
                            SignDisplay.updateSign(sign, reloadedSign);  // Broadcasts to all nearby players
                        }
                    }

                    logger.info("Shop #" + shopId + " ownership changed: " +
                        oldOwner + " → " + newOwner +
                        " (trades invalidated: " + tradesInvalidated + ")");

                    return OwnershipChangeResult.success(oldOwner, newOwner, tradesInvalidated);

                } catch (Exception e) {
                    logger.error("Ownership transfer failed for shop #" + shopId, e);
                    return OwnershipChangeResult.failure("Error: " + e.getMessage());
                }
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> canChangeOwner(CommandSender sender, int shopId) {
        return CompletableFuture.supplyAsync(() -> {
            // Check permission node
            if (sender.hasPermission("bartershops.admin.changeowner")) {
                return true;
            }

            // Bypass permission (for automation/scripts)
            if (sender.hasPermission("bartershops.admin.changeowner.bypass")) {
                return true;
            }

            logger.debug("Permission denied for " + sender.getName() +
                " to change ownership of shop #" + shopId);
            return false;
        });
    }

    @Override
    public CompletableFuture<List<UUID>> getOwnershipHistory(int shopId) {
        // TODO: Implement ownership history tracking (optional feature)
        // Would require additional database table: bartershops_ownership_history
        return CompletableFuture.completedFuture(List.of());
    }
}
