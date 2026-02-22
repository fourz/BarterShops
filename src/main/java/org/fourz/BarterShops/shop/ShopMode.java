package org.fourz.BarterShops.shop;

/**
 * Unified shop mode state machine for sign-based shops.
 * Replaces the previous SignMode/ShopMode split.
 * Single source of truth for all shop mode states.
 */
public enum ShopMode {
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
     * Active shop state - customers can trade
     */
    BOARD("Active Shop", "§2Ready"),

    /**
     * Help/information display
     */
    HELP("Help", "§bHelp"),

    /**
     * Delete confirmation - owner can break sign.
     * Always the last mode in the cycle before wrapping back to SETUP.
     */
    DELETE("Delete Mode", "§cDelete?");

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
     * Cycle: SETUP -> TYPE -> BOARD -> HELP -> DELETE -> SETUP
     * DELETE is always last before wrapping back to SETUP.
     */
    public ShopMode getNextMode() {
        return switch (this) {
            case SETUP   -> TYPE;
            case TYPE    -> BOARD;
            case BOARD   -> HELP;
            case HELP    -> DELETE;
            case DELETE  -> SETUP;
        };
    }
}