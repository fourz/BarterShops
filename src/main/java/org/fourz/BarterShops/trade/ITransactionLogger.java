package org.fourz.BarterShops.trade;

import org.fourz.BarterShops.data.dto.TradeRecordDTO;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for transaction logging and analytics.
 * Provides abstraction for future enhancement of trade tracking features.
 */
public interface ITransactionLogger {

    /**
     * Log a successful trade transaction.
     *
     * @param session Trade session that completed
     * @param transactionId Unique transaction ID
     * @param source Trade source (GUI, instant, deposit, withdrawal, admin)
     * @return CompletableFuture that completes when log is persisted
     */
    CompletableFuture<Void> logTransaction(
        TradeSession session,
        String transactionId,
        TradeSource source
    );

    /**
     * Log a failed trade attempt.
     *
     * @param playerId Player who attempted trade
     * @param shopId Shop ID involved
     * @param reason Failure reason
     * @param source Trade source
     * @return CompletableFuture that completes when log is persisted
     */
    CompletableFuture<Void> logFailedTrade(
        UUID playerId,
        UUID shopId,
        String reason,
        TradeSource source
    );

    /**
     * Get transaction history for a player.
     * Stub for future analytics features.
     *
     * @param playerId Player UUID
     * @param limit Maximum number of records
     * @return CompletableFuture with trade records (empty list in default implementation)
     */
    CompletableFuture<List<TradeRecordDTO>> getPlayerTransactions(UUID playerId, int limit);

    /**
     * Get transaction history for a shop.
     * Stub for future analytics features.
     *
     * @param shopId Shop UUID
     * @param limit Maximum number of records
     * @return CompletableFuture with trade records (empty list in default implementation)
     */
    CompletableFuture<List<TradeRecordDTO>> getShopTransactions(UUID shopId, int limit);
}
