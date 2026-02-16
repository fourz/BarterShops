package org.fourz.BarterShops.trade;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.data.FallbackTracker;
import org.fourz.BarterShops.data.dto.TradeRecordDTO;
import org.fourz.BarterShops.service.ITradeService.TradeResultDTO;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.rvnkcore.util.log.LogManager;

import org.fourz.BarterShops.service.impl.TradeServiceImpl;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core trade transaction engine for BarterShops.
 * Handles trade initiation, validation, execution, and logging.
 */
public class TradeEngine {

    private final BarterShops plugin;
    private final LogManager logger;
    private final TradeValidator validator;
    private final FallbackTracker fallbackTracker;

    /** Active trade sessions by session ID */
    private final Map<String, TradeSession> activeSessions = new ConcurrentHashMap<>();

    /** Active sessions by player UUID (one session per player) */
    private final Map<UUID, String> playerSessions = new ConcurrentHashMap<>();

    public TradeEngine(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "TradeEngine");
        this.validator = new TradeValidator(plugin);
        this.fallbackTracker = new FallbackTracker(plugin);
    }

    /**
     * Initiates a new trade session for a player at a shop.
     *
     * @param buyer The buying player
     * @param shop The shop to trade with
     * @return The created trade session, or empty if failed
     */
    public Optional<TradeSession> initiateTrade(Player buyer, BarterSign shop) {
        if (buyer == null || shop == null) {
            logger.warning("Cannot initiate trade: null buyer or shop");
            return Optional.empty();
        }

        // Check if player already has an active session
        String existingSession = playerSessions.get(buyer.getUniqueId());
        if (existingSession != null) {
            TradeSession existing = activeSessions.get(existingSession);
            if (existing != null && existing.isActive()) {
                logger.debug("Player already has active trade session");
                return Optional.of(existing);
            }
            // Clean up expired session
            cancelSession(existingSession);
        }

        // Prevent owner from trading with own shop
        if (shop.getOwner().equals(buyer.getUniqueId())) {
            logger.debug("Owner cannot trade with own shop");
            return Optional.empty();
        }

        // Create new session
        TradeSession session = new TradeSession(buyer.getUniqueId(), shop);
        activeSessions.put(session.getSessionId(), session);
        playerSessions.put(buyer.getUniqueId(), session.getSessionId());

        logger.debug("Trade session created: " + session.getSessionId() +
                " for player " + buyer.getName());

        return Optional.of(session);
    }

    /**
     * Gets an active trade session by ID.
     */
    public Optional<TradeSession> getSession(String sessionId) {
        TradeSession session = activeSessions.get(sessionId);
        if (session == null) return Optional.empty();

        // Check expiration
        if (session.isExpired()) {
            cancelSession(sessionId);
            return Optional.empty();
        }

        return Optional.of(session);
    }

    /**
     * Gets a player's active trade session.
     */
    public Optional<TradeSession> getPlayerSession(UUID playerUuid) {
        String sessionId = playerSessions.get(playerUuid);
        if (sessionId == null) return Optional.empty();
        return getSession(sessionId);
    }

    /**
     * Validates and executes a trade.
     *
     * @param sessionId The session ID to execute
     * @return CompletableFuture with the trade result
     */
    public CompletableFuture<TradeResultDTO> executeTrade(String sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            TradeSession session = activeSessions.get(sessionId);
            if (session == null) {
                return TradeResultDTO.failure("Trade session not found");
            }

            if (session.isExpired()) {
                cancelSession(sessionId);
                return TradeResultDTO.failure("Trade session expired");
            }

            // Get buyer player
            Player buyer = Bukkit.getPlayer(session.getBuyerUuid());
            if (buyer == null || !buyer.isOnline()) {
                session.setState(TradeSession.TradeState.FAILED);
                return TradeResultDTO.failure("Buyer is not online");
            }

            // Validate the trade
            session.setState(TradeSession.TradeState.VALIDATING);
            TradeValidator.ValidationResult validation = validator.validate(session, buyer);

            if (!validation.valid()) {
                session.setState(TradeSession.TradeState.FAILED);
                String errors = String.join(", ", validation.errors());
                return TradeResultDTO.failure("Validation failed: " + errors);
            }

            // Execute the item exchange
            session.setState(TradeSession.TradeState.PROCESSING);
            return executeItemExchange(session, buyer, TradeSource.GUI_CONFIRMATION);

        }).exceptionally(ex -> {
            logger.error("Trade execution failed: " + ex.getMessage());
            fallbackTracker.recordFailure("Trade execution: " + ex.getMessage());
            return TradeResultDTO.failure("Internal error: " + ex.getMessage());
        });
    }

    /**
     * Executes a direct trade without creating a session.
     * Used for auto-exchange features (instant purchase, deposit, withdrawal).
     * Bypasses GUI confirmation and trade session management.
     *
     * @param buyer The buying player
     * @param shop The shop to trade with
     * @param offering Item being offered by shop
     * @param offeredQty Quantity of offering
     * @param payment Payment item from buyer
     * @param paymentQty Payment amount
     * @param source Trade source (instant, deposit, withdrawal)
     * @return CompletableFuture with the trade result
     */
    public CompletableFuture<TradeResultDTO> executeDirectTrade(
        Player buyer,
        BarterSign shop,
        ItemStack offering,
        int offeredQty,
        ItemStack payment,
        int paymentQty,
        TradeSource source
    ) {
        return CompletableFuture.supplyAsync(() -> {
            if (buyer == null || shop == null) {
                return TradeResultDTO.failure("Invalid trade parameters");
            }

            // Prevent owner from trading with own shop
            if (shop.getOwner().equals(buyer.getUniqueId())) {
                return TradeResultDTO.failure("Cannot trade with own shop");
            }

            // Create temporary session for validation and execution
            TradeSession tempSession = new TradeSession(buyer.getUniqueId(), shop);
            tempSession.setOfferedItem(offering, offeredQty);
            tempSession.setRequestedItem(payment, paymentQty);

            // Validate the trade
            tempSession.setState(TradeSession.TradeState.VALIDATING);
            TradeValidator.ValidationResult validation = validator.validate(tempSession, buyer);

            if (!validation.valid()) {
                tempSession.setState(TradeSession.TradeState.FAILED);
                String errors = String.join(", ", validation.errors());
                return TradeResultDTO.failure(errors);
            }

            // Execute the item exchange
            tempSession.setState(TradeSession.TradeState.PROCESSING);
            return executeItemExchange(tempSession, buyer, source);

        }).exceptionally(ex -> {
            logger.error("Direct trade execution failed: " + ex.getMessage());
            fallbackTracker.recordFailure("Direct trade: " + ex.getMessage());
            return TradeResultDTO.failure("Internal error: " + ex.getMessage());
        });
    }

    /**
     * Executes the actual item exchange between buyer and shop.
     * Implements full transaction safety with snapshots and rollback on any failure.
     *
     * @param session Trade session containing trade details
     * @param buyer The buying player
     * @param source Trade source for logging (GUI, instant, deposit, withdrawal)
     */
    private TradeResultDTO executeItemExchange(TradeSession session, Player buyer, TradeSource source) {
        BarterSign shop = session.getShop();
        ItemStack offered = session.getOfferedItem();
        int offeredQty = session.getOfferedQuantity();
        ItemStack requested = session.getRequestedItem();
        int requestedQty = session.getRequestedQuantity();

        // Step 1: Create snapshots BEFORE any modifications (wrapper-aware)
        InventorySnapshot buyerSnapshot = new InventorySnapshot(buyer.getInventory());
        InventorySnapshot shopSnapshot = null;
        org.fourz.BarterShops.container.ShopContainer snapshotWrapper = shop.getShopContainerWrapper();
        if (snapshotWrapper != null) {
            shopSnapshot = new InventorySnapshot(snapshotWrapper.getInventory());
        } else if (shop.getShopContainer() != null) {
            shopSnapshot = new InventorySnapshot(shop.getShopContainer().getInventory());
        }

        // Resolve shop inventory once (wrapper-aware) - used for trade + rollback
        Inventory shopInv = null;
        org.fourz.BarterShops.container.ShopContainer wrapper = shop.getShopContainerWrapper();
        if (wrapper != null) {
            shopInv = wrapper.getInventory();
        } else if (shop.getShopContainer() != null) {
            shopInv = shop.getShopContainer().getInventory();
        }

        try {
            // Step 2: Remove payment from buyer
            if (requested != null && requestedQty > 0) {
                if (!removeItems(buyer.getInventory(), requested, requestedQty)) {
                    throw new TradeException("Failed to take payment from buyer");
                }
            }

            // Step 3: Remove items from shop container (if not admin shop)
            if (shopInv != null && offered != null) {
                if (!removeItems(shopInv, offered, offeredQty)) {
                    throw new TradeException("Shop out of stock");
                }

                // Step 4: Add payment to shop container
                if (requested != null && requestedQty > 0) {
                    int givenPayment = giveItems(shopInv, requested, requestedQty);
                    if (givenPayment < requestedQty) {
                        // Shop inventory too full to hold payment - this is a critical error
                        throw new TradeException("Shop inventory full - cannot accept payment");
                    }
                }
            }

            // Step 5: Give items to buyer (with overflow handling)
            int givenQty = 0;
            if (offered != null && offeredQty > 0) {
                givenQty = giveItems(buyer.getInventory(), offered, offeredQty);

                // Handle overflow: drop at buyer's feet if inventory full
                if (givenQty < offeredQty) {
                    int overflow = offeredQty - givenQty;
                    dropItems(buyer.getLocation(), offered, overflow);
                    buyer.sendMessage(ChatColor.YELLOW + "! " + overflow +
                        " items dropped at your feet - inventory full");
                    logger.debug("Dropped " + overflow + " items for " + buyer.getName() +
                        " - inventory full");
                }
            }

            // Step 6: Log successful trade
            session.setState(TradeSession.TradeState.COMPLETED);
            String transactionId = UUID.randomUUID().toString();
            logTrade(session, transactionId, source);

            // Step 7: Cleanup session
            cleanupSession(session.getSessionId());

            logger.info("Trade completed (" + source + "): " + transactionId + " for " + buyer.getName());
            fallbackTracker.recordSuccess();

            return TradeResultDTO.success(transactionId);

        } catch (TradeException e) {
            // Transaction failed: ROLLBACK both inventories (wrapper-aware)
            logger.error("Trade failed, rolling back: " + e.getMessage());
            buyerSnapshot.restore(buyer.getInventory());
            if (shopSnapshot != null && shopInv != null) {
                shopSnapshot.restore(shopInv);
            }

            session.setState(TradeSession.TradeState.FAILED);
            cleanupSession(session.getSessionId());

            return TradeResultDTO.failure("Trade failed and rolled back: " + e.getMessage());

        } catch (Exception e) {
            // Unexpected exception: ROLLBACK both inventories (wrapper-aware)
            logger.error("Unexpected error during trade execution: " + e.getMessage());
            buyerSnapshot.restore(buyer.getInventory());
            if (shopSnapshot != null && shopInv != null) {
                shopSnapshot.restore(shopInv);
            }

            session.setState(TradeSession.TradeState.FAILED);
            cleanupSession(session.getSessionId());

            return TradeResultDTO.failure("Trade failed: " + e.getMessage());
        }
    }

    /**
     * Removes items from an inventory.
     */
    private boolean removeItems(Inventory inventory, ItemStack item, int amount) {
        if (inventory == null || item == null || amount <= 0) return true;

        int remaining = amount;
        ItemStack[] contents = inventory.getContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack != null && stack.isSimilar(item)) {
                int take = Math.min(remaining, stack.getAmount());
                if (take >= stack.getAmount()) {
                    inventory.setItem(i, null);
                } else {
                    stack.setAmount(stack.getAmount() - take);
                    inventory.setItem(i, stack);
                }
                remaining -= take;
            }
        }

        return remaining == 0;
    }

    /**
     * Gives items to an inventory.
     * Returns the number of items actually added (may be less if inventory is full).
     */
    private int giveItems(Inventory inventory, ItemStack item, int amount) {
        if (inventory == null || item == null || amount <= 0) return 0;

        int remaining = amount;
        int maxStack = item.getMaxStackSize();
        int given = 0;

        while (remaining > 0) {
            ItemStack toGive = item.clone();
            toGive.setAmount(Math.min(remaining, maxStack));

            // Check if inventory has space
            java.util.HashMap<Integer, ItemStack> failed = inventory.addItem(toGive);
            int added = toGive.getAmount() - failed.values().stream()
                .mapToInt(ItemStack::getAmount)
                .sum();

            given += added;
            remaining -= added;

            // If items were rejected, inventory is full
            if (added == 0) break;
        }

        return given;
    }

    /**
     * Drops items at a location (useful when inventory is full).
     * Respects max stack size by splitting into multiple drops if needed.
     */
    private void dropItems(org.bukkit.Location location, ItemStack item, int amount) {
        if (location == null || item == null || amount <= 0) return;

        int maxStack = item.getMaxStackSize();
        while (amount > 0) {
            int dropAmount = Math.min(amount, maxStack);
            ItemStack toDrop = item.clone();
            toDrop.setAmount(dropAmount);
            location.getWorld().dropItem(location, toDrop);
            amount -= dropAmount;
        }
    }

    /**
     * Logs a completed trade to the repository.
     *
     * @param session Trade session
     * @param transactionId Transaction ID
     * @param source Trade source for analytics
     */
    private void logTrade(TradeSession session, String transactionId, TradeSource source) {
        // Build trade record DTO
        TradeRecordDTO record = TradeRecordDTO.builder()
                .transactionId(transactionId)
                .shopId(Integer.parseInt(session.getShop().getId()))
                .buyerUuid(session.getBuyerUuid())
                .sellerUuid(session.getSellerUuid())
                .itemStackData(serializeItem(session.getOfferedItem()))
                .quantity(session.getOfferedQuantity())
                .currencyMaterial(session.getRequestedItem() != null ?
                        session.getRequestedItem().getType().name() : null)
                .pricePaid(session.getRequestedQuantity())
                .build();

        // Persist via TradeServiceImpl if available
        TradeServiceImpl tradeService = plugin.getTradeService();
        if (tradeService != null) {
            tradeService.saveTrade(record)
                .exceptionally(ex -> {
                    logger.error("Failed to persist trade record: " + ex.getMessage());
                    return record;
                });
        } else {
            logger.debug("Trade logged (no persistence — TradeServiceImpl not available): " + transactionId);
        }
    }

    /**
     * Serializes an ItemStack to string for storage.
     */
    private String serializeItem(ItemStack item) {
        if (item == null) return null;
        // Simple serialization - can be enhanced with NBT
        return item.getType().name() + ":" + item.getAmount();
    }

    /**
     * Cancels a trade session.
     */
    public void cancelSession(String sessionId) {
        TradeSession session = activeSessions.remove(sessionId);
        if (session != null) {
            session.setState(TradeSession.TradeState.CANCELLED);
            playerSessions.remove(session.getBuyerUuid());
            logger.debug("Trade session cancelled: " + sessionId);
        }
    }

    /**
     * Cleans up a completed session.
     */
    private void cleanupSession(String sessionId) {
        TradeSession session = activeSessions.remove(sessionId);
        if (session != null) {
            playerSessions.remove(session.getBuyerUuid());
        }
    }

    /**
     * Cancels all sessions for a player.
     */
    public void cancelPlayerSessions(UUID playerUuid) {
        String sessionId = playerSessions.remove(playerUuid);
        if (sessionId != null) {
            cancelSession(sessionId);
        }
    }

    /**
     * Invalidate all active trade sessions for a shop.
     * Used when shop ownership changes to prevent old owner from completing trades.
     *
     * @param shopId The shop ID
     * @return Number of sessions invalidated
     */
    public int invalidateSessionsForShop(int shopId) {
        int invalidated = 0;

        // Find all sessions for this shop
        java.util.List<String> sessionsToRemove = new java.util.ArrayList<>();
        for (Map.Entry<String, TradeSession> entry : activeSessions.entrySet()) {
            TradeSession session = entry.getValue();
            if (session.getShop() != null && session.getShop().getShopId() == shopId) {
                sessionsToRemove.add(entry.getKey());

                // Notify player
                UUID playerId = session.getBuyerUuid();
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.sendMessage(org.bukkit.ChatColor.YELLOW + "⚠ Shop ownership changed - trade cancelled");
                }
            }
        }

        // Remove sessions
        for (String sessionId : sessionsToRemove) {
            activeSessions.remove(sessionId);
            playerSessions.values().remove(sessionId);
            invalidated++;
        }

        logger.info("Invalidated " + invalidated + " active trade sessions for shop " + shopId);
        return invalidated;
    }

    /**
     * Gets the trade validator.
     */
    public TradeValidator getValidator() {
        return validator;
    }

    /**
     * Gets the fallback tracker.
     */
    public FallbackTracker getFallbackTracker() {
        return fallbackTracker;
    }

    /**
     * Checks if in fallback mode.
     */
    public boolean isInFallbackMode() {
        return fallbackTracker.isInFallbackMode();
    }

    /**
     * Gets count of active sessions.
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Cleans up expired sessions.
     */
    public void cleanupExpiredSessions() {
        activeSessions.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                playerSessions.remove(entry.getValue().getBuyerUuid());
                logger.debug("Cleaned up expired session: " + entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Shuts down the trade engine.
     */
    public void shutdown() {
        logger.info("Shutting down TradeEngine...");
        // Cancel all active sessions
        activeSessions.keySet().forEach(this::cancelSession);
        activeSessions.clear();
        playerSessions.clear();
        logger.info("TradeEngine shutdown complete");
    }
}
