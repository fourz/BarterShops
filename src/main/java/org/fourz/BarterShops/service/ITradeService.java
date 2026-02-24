package org.fourz.BarterShops.service;

import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.data.dto.TradeRecordDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for trade operations.
 * Exposes trade processing and validation for cross-plugin access via RVNKCore ServiceRegistry.
 *
 * <p>Register with RVNKCore ServiceRegistry for use by other plugins:</p>
 * <pre>
 * ServiceRegistry.registerService(ITradeService.class, tradeManager);
 * </pre>
 *
 * <p>All async methods return CompletableFuture for non-blocking operations.</p>
 */
public interface ITradeService {

    // ========================================================
    // Trade Execution
    // ========================================================

    /**
     * Initiates a trade at a shop.
     *
     * @param buyerUuid The UUID of the buyer
     * @param shopId The shop ID to trade at
     * @return CompletableFuture containing the trade session, or empty if trade cannot be initiated
     */
    CompletableFuture<Optional<TradeSessionDTO>> initiateTrade(UUID buyerUuid, String shopId);

    /**
     * Executes a pending trade.
     *
     * @param tradeSessionId The trade session ID to execute
     * @return CompletableFuture containing the trade result
     */
    CompletableFuture<TradeResultDTO> executeTrade(String tradeSessionId);

    /**
     * Cancels a pending trade.
     *
     * @param tradeSessionId The trade session ID to cancel
     * @return CompletableFuture with true if cancelled, false if not found or already completed
     */
    CompletableFuture<Boolean> cancelTrade(String tradeSessionId);

    // ========================================================
    // Trade Validation
    // ========================================================

    /**
     * Validates if a trade can be executed.
     *
     * @param buyerUuid The UUID of the buyer
     * @param shopId The shop ID to validate
     * @param quantity The quantity to trade
     * @return CompletableFuture containing the validation result
     */
    CompletableFuture<TradeValidationResultDTO> validateTrade(UUID buyerUuid, String shopId, int quantity);

    /**
     * Checks if a player can afford a trade.
     *
     * @param playerUuid The player's UUID
     * @param shopId The shop ID
     * @param quantity The quantity to purchase
     * @return CompletableFuture with true if player can afford the trade
     */
    CompletableFuture<Boolean> canAffordTrade(UUID playerUuid, String shopId, int quantity);

    /**
     * Checks if a shop has sufficient stock.
     *
     * @param shopId The shop ID
     * @param quantity The quantity requested
     * @return CompletableFuture with true if shop has sufficient stock
     */
    CompletableFuture<Boolean> hasStock(String shopId, int quantity);

    // ========================================================
    // Trade History
    // ========================================================

    /**
     * Gets trade history for a player.
     *
     * @param playerUuid The player's UUID
     * @param limit Maximum number of trades to return
     * @return CompletableFuture containing list of trade records
     */
    CompletableFuture<List<TradeRecordDTO>> getTradeHistory(UUID playerUuid, int limit);

    /**
     * Gets trade history for a shop.
     *
     * @param shopId The shop ID
     * @param limit Maximum number of trades to return
     * @return CompletableFuture containing list of trade records
     */
    CompletableFuture<List<TradeRecordDTO>> getShopTradeHistory(String shopId, int limit);

    /**
     * Gets total trade count for statistics.
     *
     * @return CompletableFuture containing the total trade count
     */
    CompletableFuture<Long> getTotalTradeCount();

    // ========================================================
    // Fallback Mode
    // ========================================================

    /**
     * Checks if the service is operating in fallback mode.
     * In fallback mode, database operations are unavailable but
     * in-memory trades may still work.
     *
     * @return true if in fallback mode, false if normal operation
     */
    boolean isInFallbackMode();

    // ========================================================
    // DTO Records for Trade Operations
    // ========================================================

    /**
     * Represents an active trade session.
     */
    record TradeSessionDTO(
        String sessionId,
        UUID buyerUuid,
        String shopId,
        int quantity,
        long expiresAt
    ) {
        /**
         * Checks if the trade session has expired.
         *
         * @return true if expired
         */
        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        /**
         * Creates a new trade session with default expiry (5 minutes).
         */
        public static TradeSessionDTO create(UUID buyerUuid, String shopId, int quantity) {
            return new TradeSessionDTO(
                UUID.randomUUID().toString(),
                buyerUuid,
                shopId,
                quantity,
                System.currentTimeMillis() + (5 * 60 * 1000)
            );
        }
    }

    /**
     * Represents the result of a trade execution.
     */
    record TradeResultDTO(
        boolean success,
        String message,
        String transactionId
    ) {
        /**
         * Creates a successful trade result.
         */
        public static TradeResultDTO success(String transactionId) {
            return new TradeResultDTO(true, "Trade completed successfully", transactionId);
        }

        /**
         * Creates a failed trade result.
         */
        public static TradeResultDTO failure(String reason) {
            return new TradeResultDTO(false, reason, null);
        }

        /**
         * Gets the transaction ID as an Optional.
         */
        public Optional<String> getTransactionId() {
            return Optional.ofNullable(transactionId);
        }
    }

    /**
     * Represents trade validation result.
     */
    record TradeValidationResultDTO(
        boolean valid,
        List<String> errors
    ) {
        /**
         * Compact constructor with defensive copy.
         */
        public TradeValidationResultDTO {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }

        /**
         * Creates a valid result.
         */
        public static TradeValidationResultDTO success() {
            return new TradeValidationResultDTO(true, List.of());
        }

        /**
         * Creates an invalid result with errors.
         */
        public static TradeValidationResultDTO failure(List<String> errors) {
            return new TradeValidationResultDTO(false, errors);
        }

        /**
         * Creates an invalid result with a single error.
         */
        public static TradeValidationResultDTO failure(String error) {
            return new TradeValidationResultDTO(false, List.of(error));
        }

        /**
         * Checks if the validation passed.
         */
        public boolean isValid() {
            return valid;
        }
    }
}
