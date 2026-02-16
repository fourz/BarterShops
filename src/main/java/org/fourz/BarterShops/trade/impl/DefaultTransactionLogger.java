package org.fourz.BarterShops.trade.impl;

import org.fourz.BarterShops.data.dto.TradeRecordDTO;
import org.fourz.BarterShops.service.ITradeService;
import org.fourz.BarterShops.trade.ITransactionLogger;
import org.fourz.BarterShops.trade.TradeSession;
import org.fourz.BarterShops.trade.TradeSource;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Default transaction logger implementation.
 * Delegates to existing TradeService for persistence.
 * Provides stubs for future analytics features.
 */
public class DefaultTransactionLogger implements ITransactionLogger {

    private final ITradeService tradeService;

    public DefaultTransactionLogger(ITradeService tradeService) {
        this.tradeService = tradeService;
    }

    @Override
    public CompletableFuture<Void> logTransaction(
        TradeSession session,
        String transactionId,
        TradeSource source
    ) {
        // Stub for future enhancement
        // TODO: Create TradeRecordDTO with TradeSource and persist via TradeService
        // For now, logging is handled by TradeEngine.logTrade()
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> logFailedTrade(
        UUID playerId,
        UUID shopId,
        String reason,
        TradeSource source
    ) {
        // Stub for future enhancement
        // TODO: Persist failed trade attempts for analytics
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<TradeRecordDTO>> getPlayerTransactions(UUID playerId, int limit) {
        // Stub for future analytics
        // TODO: Implement query with TradeSource filtering
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<TradeRecordDTO>> getShopTransactions(UUID shopId, int limit) {
        // Stub for future analytics
        // TODO: Implement query with TradeSource filtering
        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}
