package org.fourz.BarterShops.trade;

/**
 * Identifies the source/context of a trade transaction.
 * Used for transaction logging, analytics, and auditing.
 */
public enum TradeSource {
    /**
     * Trade initiated via GUI confirmation (traditional flow).
     */
    GUI_CONFIRMATION,

    /**
     * Instant purchase via left-click with payment in hand.
     */
    INSTANT_PURCHASE,

    /**
     * Auto-exchange triggered by depositing payment in chest.
     */
    DEPOSIT_EXCHANGE,

    /**
     * Auto-exchange triggered by taking offering from chest.
     */
    WITHDRAWAL_EXCHANGE,

    /**
     * Admin-initiated trade override.
     */
    ADMIN_OVERRIDE
}
