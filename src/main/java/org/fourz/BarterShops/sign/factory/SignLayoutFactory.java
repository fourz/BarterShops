package org.fourz.BarterShops.sign.factory;

import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.BarterShops.sign.SignType;

/**
 * Factory for generating 4-line sign layouts.
 * Centralizes sign text rendering logic for testability and consistency.
 * Handles owner views, customer views, and various sign modes.
 */
public class SignLayoutFactory {
    /**
     * Maximum characters per sign line.
     */
    private static final int MAX_LINE_LENGTH = 15;

    /**
     * Truncates text to fit on a sign line with ellipsis if needed.
     *
     * @param text The text to truncate
     * @param maxLength Maximum length (e.g., 15 for sign lines)
     * @return Truncated text with "..." if too long
     */
    public static String truncateForSign(String text, int maxLength) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Creates a TYPE mode sign layout showing the shop type.
     * Format: [barter]
     *         Shop Type
     *         (empty)
     *         (empty)
     *
     * @param barterSign The barter sign
     * @return 4-line sign layout
     */
    public static String[] createTypeLayout(BarterSign barterSign) {
        String[] layout = new String[4];
        layout[0] = "\u00a7f[barter]"; // White [barter]
        layout[1] = formatType(barterSign.getType());
        layout[2] = "";
        layout[3] = "";
        return layout;
    }

    /**
     * Creates a SETUP mode sign layout for configuration step 1.
     * Format: [Setup]
     *         Step 1 of 2
     *         Hold Item
     *         (type)
     *
     * @param barterSign The barter sign
     * @return 4-line sign layout
     */
    public static String[] createSetupStep1Layout(BarterSign barterSign) {
        String[] layout = new String[4];
        layout[0] = "\u00a7e[Setup]"; // Yellow [Setup]
        layout[1] = "Step 1/2";
        layout[2] = "Hold Item";
        layout[3] = formatType(barterSign.getType());
        return layout;
    }

    /**
     * Creates a SETUP mode sign layout for configuration step 2.
     * Format: [Setup]
     *         Step 2 of 2
     *         Price/Payment
     *         (details)
     *
     * @param barterSign The barter sign
     * @return 4-line sign layout
     */
    public static String[] createSetupStep2Layout(BarterSign barterSign) {
        String[] layout = new String[4];
        layout[0] = "\u00a7e[Setup]"; // Yellow [Setup]
        layout[1] = "Step 2/2";

        SignType type = barterSign.getType();
        switch (type) {
            case BARTER:
                layout[2] = "L-Click: Add";
                layout[3] = "\u00a77Shift: Remove"; // Gray
                break;
            case BUY:
                layout[2] = "Set Price";
                layout[3] = "L: +1, Shift: -1";
                break;
            case SELL:
                layout[2] = "Set Price";
                layout[3] = "L: +1, Shift: -1";
                break;
            default:
                layout[2] = "Configure";
                layout[3] = "Shop Type";
        }
        return layout;
    }

    /**
     * Creates a BOARD mode owner summary view.
     * Shows offering, quantity, and payment count.
     * Format: [Barter]
     *         Qty Item
     *         N payment
     *         options
     *
     * @param barterSign The barter sign
     * @return 4-line sign layout
     */
    public static String[] createBoardOwnerSummaryLayout(BarterSign barterSign) {
        String[] layout = new String[4];
        layout[0] = "\u00a72[" + barterSign.getType() + "]"; // Green

        ItemStack offering = barterSign.getItemOffering();
        if (offering != null && offering.getType().name().length() > 0) {
            String itemName = truncateForSign(offering.getType().toString().toLowerCase().replace('_', ' '), 13);
            layout[1] = offering.getAmount() + "x " + itemName;
        } else {
            layout[1] = "Not Set";
        }

        int paymentCount = barterSign.getAcceptedPayments().size();
        if (paymentCount == 0) {
            layout[2] = "No Payments";
            layout[3] = "Configured";
        } else if (paymentCount == 1) {
            layout[2] = "1 Payment";
            layout[3] = "Option";
        } else {
            layout[2] = paymentCount + " Payment";
            layout[3] = "Options";
        }
        return layout;
    }

    /**
     * Creates a BOARD mode customer payment page layout.
     * Shows paginated payment option with indicator.
     * Format: [Barter]
     *         Qty Item
     *         Payment (X/Y)
     *         Details
     *
     * @param barterSign The barter sign
     * @param pageIndex The current page index (0-based)
     * @return 4-line sign layout
     */
    public static String[] createBoardCustomerPageLayout(BarterSign barterSign, int pageIndex) {
        String[] layout = new String[4];
        layout[0] = "\u00a72[" + barterSign.getType() + "]"; // Green

        ItemStack offering = barterSign.getItemOffering();
        if (offering != null && offering.getType().name().length() > 0) {
            String itemName = truncateForSign(offering.getType().toString().toLowerCase().replace('_', ' '), 13);
            layout[1] = offering.getAmount() + "x " + itemName;
        } else {
            layout[1] = "Not Set";
        }

        // Get payment for current page
        int paymentCount = barterSign.getAcceptedPayments().size();
        if (paymentCount == 0) {
            layout[2] = "No Payments";
            layout[3] = "";
        } else {
            // Wrap page index in case it's out of bounds
            int validPageIndex = Math.floorMod(pageIndex, paymentCount);
            ItemStack payment = barterSign.getAcceptedPayments().get(validPageIndex);

            String paymentName = truncateForSign(
                payment.getType().toString().toLowerCase().replace('_', ' '),
                10
            );

            // Show pagination indicator only if multiple payments
            if (paymentCount > 1) {
                layout[2] = "for: " + payment.getAmount() + "x";
                layout[3] = paymentName + " \u00a78(" + (validPageIndex + 1) + "/" + paymentCount + ")"; // Gray page indicator
            } else {
                layout[2] = "for: " + payment.getAmount() + "x";
                layout[3] = paymentName;
            }
        }
        return layout;
    }

    /**
     * Creates a DELETE mode confirmation layout.
     * Format: [DELETE?]
     *         Confirm
     *         shop
     *         removal
     *
     * @return 4-line sign layout
     */
    public static String[] createDeleteLayout() {
        String[] layout = new String[4];
        layout[0] = "\u00a7c[DELETE?]"; // Red [DELETE?]
        layout[1] = "Confirm";
        layout[2] = "shop";
        layout[3] = "removal";
        return layout;
    }

    /**
     * Formats a shop type for sign display.
     *
     * @param type The SignType to format
     * @return Formatted type string with color
     */
    private static String formatType(SignType type) {
        return switch (type) {
            case BARTER -> "\u00a7bBarter"; // Cyan
            case BUY -> "\u00a76Buy"; // Gold
            case SELL -> "\u00a72Sell"; // Green
        };
    }

    /**
     * Joins a 4-line layout array for display.
     * Useful for debugging.
     *
     * @param layout The 4-line layout
     * @return Joined string with newlines
     */
    public static String joinLayout(String[] layout) {
        if (layout.length != 4) {
            return "Invalid layout";
        }
        return String.join("\n", layout);
    }
}
