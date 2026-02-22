package org.fourz.BarterShops.shop;

/**
 * Unified shop mode state machine for sign-based shops.
 * Replaces the previous SignMode/ShopMode split.
 * Single source of truth for all shop mode states.
 */
public enum ShopMode {
    /**
     * Active shop state - customers can trade.
     * Starting point of the owner mode cycle.
     */
    BOARD("Active Shop", "§2Ready"),

    /**
     * Initial setup state - owner configures item and price.
     *
     * Stackable: Left-click with item in hand to set offering
     * Non-stackable: Place items in chest, cycle to TYPE, set UNSTACKABLE
     */
    SETUP("Setup Mode", "§5Setup"),

    /**
     * Type selection - owner cycles SignType (STACKABLE, UNSTACKABLE, BARTER)
     */
    TYPE("Type Selection", "§eType Mode"),

    /**
     * Delete confirmation - owner can break sign.
     * Always the last mode in the cycle before wrapping back to BOARD.
     */
    DELETE("Delete Mode", "§cDelete?"),

    /**
     * Help/information display. Not part of the main cycle.
     * Left-click in HELP returns to BOARD.
     */
    HELP("Help", "§bHelp");

    private final String displayName;
    private final String signText;

    ShopMode(String displayName, String signText) {
        this.displayName = displayName;
        this.signText = signText;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSignText() {
        return signText;
    }

    /**
     * Get next mode in cycle for owner right-click.
     * Cycle: BOARD -> SETUP -> TYPE -> DELETE -> BOARD
     * HELP is not part of the cycle; left-click in HELP returns to BOARD.
     */
    public ShopMode getNextMode() {
        return switch (this) {
            case BOARD   -> SETUP;
            case SETUP   -> TYPE;
            case TYPE    -> DELETE;
            case DELETE  -> BOARD;
            case HELP    -> BOARD;
        };
    }
}