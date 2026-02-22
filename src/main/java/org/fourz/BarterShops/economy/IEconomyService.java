package org.fourz.BarterShops.economy;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for economy operations with Vault integration.
 * Provides shop listing fees, trade taxes, and currency-based transactions.
 *
 * <p>Graceful fallback: All operations return defaults when Vault is unavailable.</p>
 */
public interface IEconomyService {

    // ========================================================
    // Economy Availability
    // ========================================================

    /**
     * Checks if Vault economy is available.
     *
     * @return true if Vault is loaded and economy provider exists
     */
    boolean isEconomyEnabled();

    /**
     * Gets the name of the economy provider.
     *
     * @return Economy provider name, or "None" if unavailable
     */
    String getEconomyProvider();

    // ========================================================
    // Balance Operations
    // ========================================================

    /**
     * Gets a player's current balance.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture containing the balance (0.0 if economy disabled)
     */
    CompletableFuture<Double> getBalance(UUID playerUuid);

    /**
     * Checks if a player has sufficient funds.
     *
     * @param playerUuid The player's UUID
     * @param amount The amount to check
     * @return CompletableFuture with true if player has enough money
     */
    CompletableFuture<Boolean> has(UUID playerUuid, double amount);

    /**
     * Withdraws money from a player's account.
     *
     * @param playerUuid The player's UUID
     * @param amount The amount to withdraw
     * @return CompletableFuture containing transaction result
     */
    CompletableFuture<TransactionResult> withdraw(UUID playerUuid, double amount);

    /**
     * Deposits money to a player's account.
     *
     * @param playerUuid The player's UUID
     * @param amount The amount to deposit
     * @return CompletableFuture containing transaction result
     */
    CompletableFuture<TransactionResult> deposit(UUID playerUuid, double amount);

    // ========================================================
    // Shop Fees
    // ========================================================

    /**
     * Calculates the listing fee for creating a shop.
     *
     * @param shopType The type of shop being created
     * @return The listing fee amount (0.0 if fees disabled)
     */
    double calculateListingFee(String shopType);

    /**
     * Charges a shop listing fee to a player.
     *
     * @param playerUuid The player's UUID
     * @param shopType The type of shop being created
     * @return CompletableFuture containing transaction result
     */
    CompletableFuture<TransactionResult> chargeListingFee(UUID playerUuid, String shopType);

    // ========================================================
    // Trade Taxes
    // ========================================================

    /**
     * Calculates trade tax for a transaction.
     *
     * @param tradeValue The value of the trade
     * @return The tax amount (0.0 if taxes disabled)
     */
    double calculateTradeTax(double tradeValue);

    /**
     * Applies trade tax to a transaction.
     *
     * @param buyerUuid The buyer's UUID
     * @param sellerUuid The seller's UUID
     * @param tradeValue The value of the trade
     * @return CompletableFuture containing transaction result
     */
    CompletableFuture<TransactionResult> applyTradeTax(UUID buyerUuid, UUID sellerUuid, double tradeValue);

    // ========================================================
    // Currency Formatting
    // ========================================================

    /**
     * Formats a currency amount for display.
     *
     * @param amount The amount to format
     * @return Formatted currency string (e.g., "$100.00")
     */
    String format(double amount);

    /**
     * Gets the currency name in singular form.
     *
     * @return Currency name (e.g., "Dollar")
     */
    String currencyNameSingular();

    /**
     * Gets the currency name in plural form.
     *
     * @return Currency name (e.g., "Dollars")
     */
    String currencyNamePlural();

    // ========================================================
    // Economy Statistics
    // ========================================================

    /**
     * Gets total fees collected from shop listings.
     *
     * @return CompletableFuture containing total fees collected
     */
    CompletableFuture<Double> getTotalFeesCollected();

    /**
     * Gets total taxes collected from trades.
     *
     * @return CompletableFuture containing total taxes collected
     */
    CompletableFuture<Double> getTotalTaxesCollected();

    // ========================================================
    // Transaction Result Record
    // ========================================================

    /**
     * Represents the result of an economy transaction.
     */
    record TransactionResult(
        boolean success,
        double amount,
        String message,
        double newBalance
    ) {
        /**
         * Creates a successful transaction result.
         */
        public static TransactionResult success(double amount, double newBalance) {
            return new TransactionResult(true, amount, "Transaction completed", newBalance);
        }

        /**
         * Creates a failed transaction result.
         */
        public static TransactionResult failure(String reason) {
            return new TransactionResult(false, 0.0, reason, 0.0);
        }

        /**
         * Creates a result for when economy is disabled.
         */
        public static TransactionResult economyDisabled() {
            return new TransactionResult(false, 0.0, "Economy is not enabled", 0.0);
        }
    }
}
