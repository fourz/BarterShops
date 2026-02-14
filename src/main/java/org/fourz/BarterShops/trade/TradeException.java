package org.fourz.BarterShops.trade;

/**
 * Exception thrown when a trade operation fails.
 * Used in transaction processing for controlled error handling and rollback.
 */
public class TradeException extends Exception {

    public TradeException(String message) {
        super(message);
    }

    public TradeException(String message, Throwable cause) {
        super(message, cause);
    }
}
