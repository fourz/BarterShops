package org.fourz.BarterShops.service.impl;

import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.data.FallbackTracker;
import org.fourz.BarterShops.data.dto.TradeRecordDTO;
import org.fourz.BarterShops.data.repository.ITradeRepository;
import org.fourz.BarterShops.service.ITradeService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concrete implementation of ITradeService for RVNKCore ServiceRegistry.
 * Delegates trade history and statistics to ITradeRepository.
 * Trade execution is handled by TradeEngine; this service covers
 * persistence, querying, and cross-plugin access.
 */
public class TradeServiceImpl implements ITradeService {

    private final BarterShops plugin;
    private final LogManager logger;
    private final ITradeRepository tradeRepository;
    private final FallbackTracker fallbackTracker;

    /** Active trade sessions tracked by session ID */
    private final Map<String, TradeSessionDTO> activeSessions = new ConcurrentHashMap<>();

    public TradeServiceImpl(BarterShops plugin, ITradeRepository tradeRepository, FallbackTracker fallbackTracker) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "TradeServiceImpl");
        this.tradeRepository = tradeRepository;
        this.fallbackTracker = fallbackTracker;

        logger.info("TradeServiceImpl initialized");
    }

    // ========================================================
    // Trade Execution (delegated from TradeEngine for service API)
    // ========================================================

    @Override
    public CompletableFuture<Optional<TradeSessionDTO>> initiateTrade(UUID buyerUuid, String shopId) {
        TradeSessionDTO session = TradeSessionDTO.create(buyerUuid, shopId, 1);
        activeSessions.put(session.sessionId(), session);
        logger.debug("Trade session initiated: " + session.sessionId());
        return CompletableFuture.completedFuture(Optional.of(session));
    }

    @Override
    public CompletableFuture<TradeResultDTO> executeTrade(String tradeSessionId) {
        TradeSessionDTO session = activeSessions.remove(tradeSessionId);
        if (session == null) {
            return CompletableFuture.completedFuture(TradeResultDTO.failure("Session not found"));
        }
        if (session.isExpired()) {
            return CompletableFuture.completedFuture(TradeResultDTO.failure("Session expired"));
        }
        // Actual item exchange is handled by TradeEngine.
        // This method exists for the service API contract.
        return CompletableFuture.completedFuture(TradeResultDTO.failure("Use TradeEngine for item exchange"));
    }

    @Override
    public CompletableFuture<Boolean> cancelTrade(String tradeSessionId) {
        TradeSessionDTO removed = activeSessions.remove(tradeSessionId);
        return CompletableFuture.completedFuture(removed != null);
    }

    // ========================================================
    // Trade Validation
    // ========================================================

    @Override
    public CompletableFuture<TradeValidationResultDTO> validateTrade(UUID buyerUuid, String shopId, int quantity) {
        if (buyerUuid == null || shopId == null || quantity <= 0) {
            return CompletableFuture.completedFuture(
                TradeValidationResultDTO.failure("Invalid trade parameters"));
        }
        return CompletableFuture.completedFuture(TradeValidationResultDTO.success());
    }

    @Override
    public CompletableFuture<Boolean> canAffordTrade(UUID playerUuid, String shopId, int quantity) {
        // Barter trades don't have currency cost — always true for basic check.
        // Real validation happens in TradeValidator during execution.
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Boolean> hasStock(String shopId, int quantity) {
        // Stock check requires world access — delegated to TradeValidator at execution time.
        return CompletableFuture.completedFuture(true);
    }

    // ========================================================
    // Trade History (delegates to ITradeRepository)
    // ========================================================

    @Override
    public CompletableFuture<List<TradeRecordDTO>> getTradeHistory(UUID playerUuid, int limit) {
        if (isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return tradeRepository.findByPlayer(playerUuid, limit);
    }

    @Override
    public CompletableFuture<List<TradeRecordDTO>> getShopTradeHistory(String shopId, int limit) {
        if (isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return tradeRepository.findByShop(Integer.parseInt(shopId), limit);
    }

    @Override
    public CompletableFuture<Long> getTotalTradeCount() {
        if (isInFallbackMode()) {
            return CompletableFuture.completedFuture(0L);
        }
        return tradeRepository.count();
    }

    /**
     * Gets trade count for a specific player.
     */
    public CompletableFuture<Long> getPlayerTradeCount(UUID playerUuid) {
        if (isInFallbackMode()) {
            return CompletableFuture.completedFuture(0L);
        }
        return tradeRepository.countByPlayer(playerUuid);
    }

    /**
     * Gets trade count for a specific shop.
     */
    public CompletableFuture<Long> getShopTradeCount(String shopId) {
        if (isInFallbackMode()) {
            return CompletableFuture.completedFuture(0L);
        }
        return tradeRepository.countByShop(Integer.parseInt(shopId));
    }

    /**
     * Saves a trade record to the repository.
     * Called by TradeEngine after successful trade execution.
     */
    public CompletableFuture<TradeRecordDTO> saveTrade(TradeRecordDTO record) {
        if (isInFallbackMode()) {
            logger.warning("Cannot persist trade in fallback mode: " + record.transactionId());
            return CompletableFuture.completedFuture(record);
        }
        return tradeRepository.save(record)
            .thenApply(saved -> {
                logger.debug("Trade persisted: " + saved.transactionId());
                fallbackTracker.recordSuccess();
                return saved;
            })
            .exceptionally(ex -> {
                logger.error("Failed to persist trade: " + ex.getMessage());
                fallbackTracker.recordFailure("Trade persistence: " + ex.getMessage());
                return record;
            });
    }

    /**
     * Gets recent trades across all shops.
     */
    public CompletableFuture<List<TradeRecordDTO>> getRecentTrades(int limit) {
        if (isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return tradeRepository.findRecent(limit);
    }

    // ========================================================
    // Item Utilities
    // ========================================================

    @Override
    public String serializeItem(ItemStack item) {
        if (item == null) return null;
        return item.getType().name() + ":" + item.getAmount();
    }

    @Override
    public ItemStack deserializeItem(String serialized) {
        if (serialized == null || serialized.isEmpty()) return null;
        String[] parts = serialized.split(":");
        if (parts.length < 2) return null;
        try {
            org.bukkit.Material material = org.bukkit.Material.valueOf(parts[0]);
            int amount = Integer.parseInt(parts[1]);
            return new ItemStack(material, amount);
        } catch (IllegalArgumentException e) {
            logger.warning("Failed to deserialize item: " + serialized);
            return null;
        }
    }

    // ========================================================
    // Fallback Mode
    // ========================================================

    @Override
    public boolean isInFallbackMode() {
        return fallbackTracker != null && fallbackTracker.isInFallbackMode();
    }

    /**
     * Gets the underlying trade repository.
     */
    public ITradeRepository getTradeRepository() {
        return tradeRepository;
    }
}
