package org.fourz.BarterShops.data.repository;

import org.fourz.BarterShops.data.dto.TradeRecordDTO;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for trade transaction data access.
 * Follows the Repository pattern for data access abstraction.
 *
 * <p>All methods return CompletableFuture for async database operations.</p>
 * <p>Implementations should use FallbackTracker for graceful degradation.</p>
 */
public interface ITradeRepository {

    // ========================================================
    // Trade Record CRUD
    // ========================================================

    /**
     * Saves a new trade record.
     *
     * @param trade The trade record to save
     * @return CompletableFuture containing the saved record
     */
    CompletableFuture<TradeRecordDTO> save(TradeRecordDTO trade);

    /**
     * Finds a trade record by transaction ID.
     *
     * @param transactionId The transaction ID
     * @return CompletableFuture containing the record, or empty if not found
     */
    CompletableFuture<Optional<TradeRecordDTO>> findByTransactionId(String transactionId);

    /**
     * Deletes a trade record by transaction ID.
     * Note: Generally trade records should not be deleted for audit purposes.
     *
     * @param transactionId The transaction ID to delete
     * @return CompletableFuture with true if deleted
     */
    CompletableFuture<Boolean> deleteByTransactionId(String transactionId);

    // ========================================================
    // Trade History Queries
    // ========================================================

    /**
     * Finds trade history for a player (as buyer or seller).
     *
     * @param playerUuid The player's UUID
     * @param limit Maximum number of records to return
     * @return CompletableFuture containing list of trade records
     */
    CompletableFuture<List<TradeRecordDTO>> findByPlayer(UUID playerUuid, int limit);

    /**
     * Finds trade history for a player as buyer only.
     *
     * @param buyerUuid The buyer's UUID
     * @param limit Maximum number of records to return
     * @return CompletableFuture containing list of trade records
     */
    CompletableFuture<List<TradeRecordDTO>> findByBuyer(UUID buyerUuid, int limit);

    /**
     * Finds trade history for a player as seller only.
     *
     * @param sellerUuid The seller's UUID
     * @param limit Maximum number of records to return
     * @return CompletableFuture containing list of trade records
     */
    CompletableFuture<List<TradeRecordDTO>> findBySeller(UUID sellerUuid, int limit);

    /**
     * Finds trade history for a specific shop.
     *
     * @param shopId The shop ID
     * @param limit Maximum number of records to return
     * @return CompletableFuture containing list of trade records
     */
    CompletableFuture<List<TradeRecordDTO>> findByShop(int shopId, int limit);

    /**
     * Finds trades within a date range.
     *
     * @param start Start timestamp (inclusive)
     * @param end End timestamp (exclusive)
     * @param limit Maximum number of records to return
     * @return CompletableFuture containing list of trade records
     */
    CompletableFuture<List<TradeRecordDTO>> findByDateRange(
            Timestamp start, Timestamp end, int limit);

    /**
     * Finds recent trades (all shops, all players).
     *
     * @param limit Maximum number of records to return
     * @return CompletableFuture containing list of recent trade records
     */
    CompletableFuture<List<TradeRecordDTO>> findRecent(int limit);

    // ========================================================
    // Statistics
    // ========================================================

    /**
     * Gets total count of all trade records.
     *
     * @return CompletableFuture containing the total count
     */
    CompletableFuture<Long> count();

    /**
     * Gets count of trades for a specific shop.
     *
     * @param shopId The shop ID
     * @return CompletableFuture containing the shop's trade count
     */
    CompletableFuture<Long> countByShop(int shopId);

    /**
     * Gets count of trades for a player.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture containing the player's trade count
     */
    CompletableFuture<Long> countByPlayer(UUID playerUuid);

    /**
     * Gets total trade volume (sum of all quantities) for a shop.
     *
     * @param shopId The shop ID
     * @return CompletableFuture containing total volume
     */
    CompletableFuture<Long> getTotalVolumeByShop(int shopId);

    /**
     * Gets count of trades within a date range.
     *
     * @param start Start timestamp (inclusive)
     * @param end End timestamp (exclusive)
     * @return CompletableFuture containing the count
     */
    CompletableFuture<Long> countByDateRange(Timestamp start, Timestamp end);

    // ========================================================
    // Cleanup Operations
    // ========================================================

    /**
     * Deletes trade records older than the specified timestamp.
     * Use with caution - typically for archival purposes only.
     *
     * @param before Delete records before this timestamp
     * @return CompletableFuture containing number of deleted records
     */
    CompletableFuture<Integer> deleteOlderThan(Timestamp before);

    /**
     * Archives trade records older than the specified timestamp.
     * Implementation should move records to an archive table.
     *
     * @param before Archive records before this timestamp
     * @return CompletableFuture containing number of archived records
     */
    CompletableFuture<Integer> archiveOlderThan(Timestamp before);
}
