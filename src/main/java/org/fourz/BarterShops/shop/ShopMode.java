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
     * Delete confirmation - owner can break sign
     */
    DELETE("Delete Mode", "§cDelete?"),

    /**
     * Help/information display
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
     * Get next mode in cycle for owner left-click.
     * Cycle: SETUP -> TYPE -> BOARD -> DELETE -> SETUP
     */
    public ShopMode getNextMode() {
        return switch (this) {
            case SETUP -> TYPE;
            case TYPE -> BOARD;
            case BOARD -> DELETE;
            case DELETE -> SETUP;
            case HELP -> BOARD; // Help returns to active state
        };
    }
}