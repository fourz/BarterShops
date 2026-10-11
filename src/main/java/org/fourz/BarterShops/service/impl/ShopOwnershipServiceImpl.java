package org.fourz.BarterShops.service.impl;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.data.dto.OwnershipChangeResult;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.repository.IShopRepository;
import org.fourz.BarterShops.service.IShopOwnershipService;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.BarterShops.sign.SignDisplay;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of IShopOwnershipService.
 * Handles real-time ownership transfers with cache synchronization,
 * session invalidation, and multi-player broadcast.
 */
public class ShopOwnershipServiceImpl implements IShopOwnershipService {

    private final BarterShops plugin;
    private final LogManager logger;
    private final Executor executor;
    private final ConcurrentHashMap<Integer, Object> shopLocks = new ConcurrentHashMap<>();

    public ShopOwnershipServiceImpl(BarterShops plugin) {
        this(plugin, LogManager.getInstance(plugin, "OwnershipService"), ForkJoinPool.commonPool());
    }

    /**
     * Test constructor: a direct executor runs the transfer on the calling thread.
     */
    ShopOwnershipServiceImpl(BarterShops plugin, LogManager logger, Executor executor) {
        this.plugin = plugin;
        this.logger = logger;
        this.executor = executor;
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

                    IShopRepository shopRepository = plugin.getShopRepository();
                    if (shopRepository == null) {
                        return OwnershipChangeResult.failure(
                            "Shop database is not available - ownership not changed");
                    }

                    // Step 2: Load existing shop
                    ShopDataDTO existingShop = shopRepository
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

                    return transferLocked(shopRepository, shopId, oldOwner, newOwner,
                        existingShop.groupId());

                } catch (Exception e) {
                    logger.error("Ownership transfer failed for shop #" + shopId, e);
                    return OwnershipChangeResult.failure(
                        "Error: " + e.getMessage() + " - ownership not changed");
                }
            }
        }, executor);
    }

    /**
     * Writes the new owner and ungroups the shop in one statement, then brings the cache in
     * step. A failed write changes nothing; a failed cache reload restores the old owner and
     * group with the same conditional statement (#2285). Caller holds the per-shop lock.
     */
    private OwnershipChangeResult transferLocked(IShopRepository shopRepository, int shopId,
                                                 UUID oldOwner, UUID newOwner, Integer oldGroupId) {
        // Step 3: Owner and group change together. The old owner's group - and its co-owners -
        // must not keep the shop (#2118), and a separate ungroup call whose failure went
        // unnoticed gave them access back after a restart (#2285).
        CompletableFuture<Boolean> update = null;
        try {
            update = shopRepository.updateOwnerAndGroup(shopId, oldOwner, newOwner, null);
            if (!Boolean.TRUE.equals(update.get(5, TimeUnit.SECONDS))) {
                logger.warning("Shop #" + shopId + " ownership not changed: the database update "
                    + "failed or the owner changed meanwhile");
                return OwnershipChangeResult.failure("Database update failed - ownership not changed");
            }
        } catch (Exception e) {
            logger.error("Ownership update failed for shop #" + shopId, e);
            if (update != null) {
                // A timed-out statement can still commit later; undo it once it settles
                update.thenAccept(applied -> {
                    if (Boolean.TRUE.equals(applied)) {
                        restoreOwner(shopRepository, shopId, oldOwner, newOwner, oldGroupId);
                    }
                });
            }
            return OwnershipChangeResult.failure("Database error - ownership not changed");
        }

        // Step 4: Reload BarterSign from database (updates in-memory owner)
        CompletableFuture<BarterSign> reload = null;
        BarterSign reloadedSign;
        try {
            reload = plugin.getSignManager().reloadShopFromDatabase(shopId);
            reloadedSign = reload.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Cache reload failed for shop #" + shopId, e);
            reloadedSign = null;
        }

        if (reloadedSign == null) {
            // CRITICAL: Rollback database if cache reload fails
            boolean restored = restoreOwner(shopRepository, shopId, oldOwner, newOwner, oldGroupId);
            if (reload != null && !reload.isDone()) {
                // A late reload would cache the new owner; reload again so the cache matches the DB
                reload.whenComplete((sign, ex) -> {
                    if (sign != null) {
                        plugin.getSignManager().reloadShopFromDatabase(shopId);
                    }
                });
            }
            return OwnershipChangeResult.failure(restored
                ? "Cache reload failed - ownership rolled back"
                : "Cache reload failed and the rollback failed - check the owner of shop #" + shopId);
        }

        // The reload read group_id back as NULL; set it anyway so no co-owner check
        // can match the old group through this sign (#2118)
        final BarterSign cachedSign = reloadedSign;
        cachedSign.setGroupId(0);

        // The owner change is committed and cached; the steps below only tidy up around it
        int tradesInvalidated = 0;
        try {
            // Step 5: Invalidate active sessions
            tradesInvalidated = plugin.getTradeEngine().invalidateSessionsForShop(shopId);
            plugin.getShopManager().invalidateSessionsForShop(cachedSign);

            // Step 6: Update sign display on main thread (Bukkit requires main thread for block operations)
            Location signLoc = cachedSign.getSignLocation();
            if (signLoc != null && signLoc.getWorld() != null) {
                // Schedule on main thread - block state access requires main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    BlockState state = signLoc.getBlock().getState();
                    if (state instanceof Sign sign) {
                        SignDisplay.updateSign(sign, cachedSign);  // Broadcasts to all nearby players
                    }
                });
            }
        } catch (Exception e) {
            logger.error("Shop #" + shopId + " changed owner, but a session or sign refresh failed", e);
        }

        logger.info("Shop #" + shopId + " ownership changed: " +
            oldOwner + " → " + newOwner +
            " (trades invalidated: " + tradesInvalidated + ")");

        return OwnershipChangeResult.success(oldOwner, newOwner, tradesInvalidated);
    }

    /**
     * Puts the old owner and group back. Matches only while the row still has the new owner,
     * so it is a no-op when the transfer never landed.
     *
     * @return true if the row was restored
     */
    private boolean restoreOwner(IShopRepository shopRepository, int shopId,
                                 UUID oldOwner, UUID newOwner, Integer oldGroupId) {
        try {
            boolean restored = Boolean.TRUE.equals(shopRepository
                .updateOwnerAndGroup(shopId, newOwner, oldOwner, oldGroupId)
                .get(5, TimeUnit.SECONDS));
            if (!restored) {
                logger.error("Rollback of shop #" + shopId + " did not apply: owner "
                    + oldOwner + " and group " + oldGroupId + " need a manual check");
            }
            return restored;
        } catch (Exception e) {
            logger.error("Failed to roll back ownership of shop #" + shopId, e);
            return false;
        }
    }

    @Override
    public CompletableFuture<Boolean> canChangeOwner(CommandSender sender, int shopId) {
        return CompletableFuture.supplyAsync(() -> {
            // Check permission node
            if (sender.hasPermission("bartershops.admin")) {
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
