package org.fourz.BarterShops.sign.renderer;

import org.bukkit.block.sign.SignSide;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.sign.SignType;
import org.fourz.BarterShops.sign.factory.SignLayoutFactory;

/**
 * Shared sign-rendering helpers used by the mode renderers.
 *
 * Extracts the DRY violations that existed in SignDisplay:
 * - {@code getTypeHeader} was duplicated 3× across displayNotConfigured,
 *   displayCustomerPaymentPage, and displayOwnerBoardView.
 * - The wrapping helpers were private and inaccessible to renderer classes.
 *
 * All methods are static; instances are not meaningful.
 */
public final class SignRenderUtil {

    private SignRenderUtil() {}

    /**
     * Returns the coloured header text for a given shop type.
     * Previously inlined at 3 call sites inside SignDisplay.
     */
    public static String getTypeHeader(SignType type) {
        return switch (type) {
            case BARTER -> "§2[Barter]";
            case BUY    -> "§e[We Buy]";
            case SELL   -> "§a[We Sell]";
        };
    }

    /**
     * Computes the word-break split index for an item name that follows a prefix
     * on a sign line. Finds the last space within the characters available after
     * the prefix, falling back to a hard split only when no word boundary exists.
     *
     * @param name         Item name to split
     * @param prefixLength Characters already consumed by the prefix (e.g. "70x " = 4)
     * @return Index into name: [0..index) on line N, [index..) on line N+1
     */
    public static int computeNameSplit(String name, int prefixLength) {
        int available = SignLayoutFactory.MAX_LINE_LENGTH - prefixLength;
        if (available <= 0) return 0;
        int splitIndex = name.lastIndexOf(' ', available);
        return splitIndex <= 0 ? Math.min(available, name.length()) : splitIndex;
    }

    /**
     * Formats an ItemStack display name for use on a sign.
     * Uses the item's custom display name if present; otherwise title-cases
     * the material enum name (e.g. DIAMOND_SWORD → "Diamond Sword").
     */
    public static String formatItemName(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "None";
        }
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        String[] words = item.getType().name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1));
            }
        }
        return sb.toString();
    }

    /**
     * Applies a 4-line layout array to a sign side.
     */
    public static void applyLayoutToSign(SignSide side, String[] layout) {
        for (int i = 0; i < 4 && i < layout.length; i++) {
            side.setLine(i, layout[i] != null ? layout[i] : "");
        }
    }

    /**
     * Writes the offering on the sign with "Qx " prefix, wrapping to a second
     * line when the full line would overflow {@link SignLayoutFactory#MAX_LINE_LENGTH}.
     *
     * @param side      Sign side to update
     * @param offering  Offering item
     * @param startLine Starting line number (0 for single-payment, 1 for standard)
     * @return true if the name was wrapped across two lines
     */
    public static boolean displayOfferingWithWrapping(SignSide side, ItemStack offering, int startLine) {
        if (offering == null) return false;

        String itemName = formatItemName(offering);
        int amount = offering.getAmount();
        String prefix = amount + "x ";

        if ((prefix + itemName).length() > SignLayoutFactory.MAX_LINE_LENGTH) {
            int splitIndex = computeNameSplit(itemName, prefix.length());
            side.setLine(startLine,     "§b" + prefix + itemName.substring(0, splitIndex).trim());
            side.setLine(startLine + 1, "§b" + SignLayoutFactory.truncateForSign(
                itemName.substring(splitIndex).trim(), SignLayoutFactory.MAX_LINE_LENGTH));
            return true;
        } else {
            side.setLine(startLine, "§b" + prefix + itemName);
            return false;
        }
    }

    /** Backward-compatible overload: defaults to {@code startLine=1}. */
    public static boolean displayOfferingWithWrapping(SignSide side, ItemStack offering) {
        return displayOfferingWithWrapping(side, offering, 1);
    }

    /**
     * Writes the payment on the sign with "for: Qx " prefix, wrapping when needed.
     *
     * @param side      Sign side to update
     * @param payment   Payment item
     * @param startLine Starting line number (typically 2)
     * @return true if the name was wrapped across two lines
     */
    public static boolean displayPaymentWithWrapping(SignSide side, ItemStack payment, int startLine) {
        if (payment == null) return false;

        String itemName = formatItemName(payment);
        int amount = payment.getAmount();
        String prefix = "for: " + amount + "x ";

        if ((prefix + itemName).length() > SignLayoutFactory.MAX_LINE_LENGTH) {
            int splitIndex = computeNameSplit(itemName, prefix.length());
            side.setLine(startLine,     "§e" + prefix + itemName.substring(0, splitIndex).trim());
            side.setLine(startLine + 1, "§e" + SignLayoutFactory.truncateForSign(
                itemName.substring(splitIndex).trim(), SignLayoutFactory.MAX_LINE_LENGTH));
            return true;
        } else {
            side.setLine(startLine, "§e" + prefix + itemName);
            return false;
        }
    }

    /**
     * Dual-wrap mode: removes the header and uses all 4 sign lines for offering + payment.
     * Only called when BOTH the offering name and the payment name exceed the line limit.
     */
    public static void displayDualWrapMode(SignSide side, ItemStack offering, ItemStack payment) {
        String offeringName = formatItemName(offering);
        String paymentName  = formatItemName(payment);

        String offeringPrefix = offering.getAmount() + "x ";
        int offeringSplit = computeNameSplit(offeringName, offeringPrefix.length());
        side.setLine(0, "§b" + offeringPrefix + offeringName.substring(0, offeringSplit).trim());
        side.setLine(1, "§b" + SignLayoutFactory.truncateForSign(
            offeringName.substring(offeringSplit).trim(), SignLayoutFactory.MAX_LINE_LENGTH));

        String paymentPrefix = "for: " + payment.getAmount() + "x ";
        int paymentSplit = computeNameSplit(paymentName, paymentPrefix.length());
        side.setLine(2, "§e" + paymentPrefix + paymentName.substring(0, paymentSplit).trim());
        side.setLine(3, "§e" + SignLayoutFactory.truncateForSign(
            paymentName.substring(paymentSplit).trim(), SignLayoutFactory.MAX_LINE_LENGTH));
    }
}
