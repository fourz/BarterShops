package org.fourz.BarterShops.sign;

import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.shop.ShopMode;
import org.fourz.BarterShops.sign.factory.SignLayoutFactory;

import java.util.List;

public class SignDisplay {

    /**
     * Updates the sign display with customer or owner view.
     * @param sign The sign to update
     * @param barterSign The sign data
     * @param isCustomerView True to show customer view, false for owner view
     */
    public static void updateSign(Sign sign, BarterSign barterSign, boolean isCustomerView) {
        SignSide frontSide = sign.getSide(Side.FRONT);
        // Store the current sign side in the BarterSign
        barterSign.setSignSideDisplayFront(frontSide);

        // DEBUG: Log preview mode state for bug-35 investigation
        if (barterSign.getMode().equals(ShopMode.BOARD)) {
            org.bukkit.Bukkit.getLogger().fine(String.format(
                "[BarterShops-DEBUG] updateSign BOARD: isCustomerView=%b, ownerPreviewMode=%b, configMode=%s",
                isCustomerView, barterSign.isOwnerPreviewMode(), barterSign.getType()
            ));
        }

        switch (barterSign.getMode()) {
            case SETUP -> displaySetupMode(frontSide, barterSign);
            case BOARD -> displayBoardMode(frontSide, barterSign, isCustomerView);
            case TYPE -> displayTypeMode(frontSide, barterSign);
            case HELP -> displayHelpMode(frontSide);
            case DELETE -> displayDeleteMode(frontSide);
        }
        sign.update();
    }

    /**
     * Backward-compatible overload: defaults to owner view (isCustomerView=false)
     */
    public static void updateSign(Sign sign, BarterSign barterSign) {
        // Show customer view if owner is in preview mode, otherwise show owner view
        boolean shouldShowCustomerView = barterSign.isOwnerPreviewMode();
        org.bukkit.Bukkit.getLogger().fine(String.format(
            "[BarterShops-DEBUG] updateSign overload: isOwnerPreviewMode=%b -> passing isCustomerView=%b",
            barterSign.isOwnerPreviewMode(), shouldShowCustomerView
        ));
        updateSign(sign, barterSign, shouldShowCustomerView);
    }

    public static void displayTemporaryMessage(Sign sign, String line1, String line2) {
        SignSide frontSide = sign.getSide(Side.FRONT);
        frontSide.setLine(0, "\u00A72[Barter Shop]");
        frontSide.setLine(1, line1);
        frontSide.setLine(2, line2 != null ? line2 : "");
        frontSide.setLine(3, "");
        sign.update();
    }

    private static void displaySetupMode(SignSide side, BarterSign barterSign) {
        // DELEGATION: Use SignLayoutFactory for centralized layout generation (Phase 2 refactoring)
        applyLayoutToSign(side, SignLayoutFactory.createSetupLayout(barterSign));
    }

    /**
     * Router for BOARD mode display: customer view or owner view based on context.
     */
    private static void displayBoardMode(SignSide side, BarterSign barterSign, boolean isCustomerView) {
        if (!barterSign.isConfigured()) {
            displayNotConfigured(side, barterSign);
            return;
        }

        // DEBUG: Log which branch is being taken
        org.bukkit.Bukkit.getLogger().fine(String.format(
            "[BarterShops-DEBUG] displayBoardMode router: isCustomerView=%b -> %s",
            isCustomerView, isCustomerView ? "CUSTOMER" : "OWNER"
        ));

        if (isCustomerView) {
            displayCustomerPaymentPage(side, barterSign);
        } else {
            displayOwnerBoardView(side, barterSign);
        }
    }

    /**
     * Display when shop is not configured (shown to both customer and owner).
     */
    private static void displayNotConfigured(SignSide side, BarterSign barterSign) {
        SignType type = barterSign.getType();
        String header = switch(type) {
            case BARTER -> "§2[Barter]";
            case BUY -> "§e[We Buy]";
            case SELL -> "§a[We Sell]";
        };
        side.setLine(0, header);
        side.setLine(1, "§eNot configured");
        side.setLine(2, "§eAsk owner");
        side.setLine(3, "");
    }

    /**
     * Displays customer-facing payment page for BARTER shops.
     * - Single payment: No header (barter is implicit), uses all 4 lines (0-3)
     * - Multiple payments: Shows header + offering + summary (pages with pagination)
     * - BUY/SELL: Shows header + offering + price
     */
    private static void displayCustomerPaymentPage(SignSide side, BarterSign barterSign) {
        SignType type = barterSign.getType();
        ItemStack offering = barterSign.getItemOffering();

        // Determine if this is a single-payment BARTER shop (no header needed)
        List<ItemStack> payments = (type == SignType.BARTER) ? barterSign.getAcceptedPayments() : null;
        boolean isSinglePaymentBarter = (type == SignType.BARTER && payments != null && payments.size() == 1);

        // Line 0: Shop type header (only for non-single-payment or non-BARTER)
        if (!isSinglePaymentBarter) {
            String header = switch(type) {
                case BARTER -> "§2[Barter]";
                case BUY -> "§e[We Buy]";
                case SELL -> "§a[We Sell]";
            };
            side.setLine(0, header);
        }

        // Offering display: Use line 0 for single-payment, line 1 for multi-payment/BUY/SELL
        int offeringStartLine = isSinglePaymentBarter ? 0 : 1;
        boolean offeringWrapped = false;
        if (offering != null) {
            offeringWrapped = displayOfferingWithWrapping(side, offering, offeringStartLine);
        }

        // Lines 2-3 or Line 3: Payment info (type-dependent)
        if (type == SignType.BARTER) {
            // payments already fetched above
            if (payments.isEmpty()) {
                if (offeringWrapped) {
                    side.setLine(3, "§eNo pay options");
                } else {
                    side.setLine(2, "§eNo payment");
                    side.setLine(3, "§eoptions");
                }
            } else if (payments.size() == 1) {
                // Single payment: Show payment details (no pagination)
                ItemStack payment = payments.get(0);

                // Check if payment needs wrapping: account for "for: Nx " prefix on the line
                String paymentName = formatItemName(payment);
                boolean paymentNeedsWrapping = paymentName != null &&
                    ("for: " + payment.getAmount() + "x " + paymentName).length() > SignLayoutFactory.MAX_LINE_LENGTH;

                if (offeringWrapped && paymentNeedsWrapping) {
                    // DUAL-WRAP MODE: Both offering and payment have long names
                    // Remove header, use all 4 lines (0-3)
                    displayDualWrapMode(side, offering, payment);
                } else if (offeringWrapped) {
                    // Offering wrapped, payment short: condensed on line 3
                    // MUST clear line 2 when offering uses lines 0-1
                    side.setLine(2, "");
                    side.setLine(3, "§efor: " + payment.getAmount() + "x " + formatItemName(payment));
                } else if (paymentNeedsWrapping) {
                    // Offering short, payment long: payment wraps on lines 2-3
                    displayPaymentWithWrapping(side, payment, 2);
                } else {
                    // Both short: standard 2-line display
                    side.setLine(2, "§efor: " + payment.getAmount() + "x /");
                    side.setLine(3, "§e" + formatItemName(payment));
                }
            } else {
                // Multiple payments: Show summary (page 0) or single payment per page (pages 1+)
                int currentPaymentIndex = barterSign.getCurrentPaymentPage();

                if (currentPaymentIndex == 0) {
                    // Summary view (page 1)
                    if (offeringWrapped) {
                        side.setLine(3, "§e" + payments.size() + " pay options");
                    } else {
                        side.setLine(2, "§e" + payments.size() + " payment");
                        side.setLine(3, "§eoptions");
                    }
                } else {
                    // Single payment per page (page 2+)
                    int paymentIndex = currentPaymentIndex - 1;  // Convert to 0-based payment array index
                    ItemStack payment = payments.get(paymentIndex);

                    int displayPageNumber = currentPaymentIndex + 1;  // Page 1 is summary, page 2+ are payments
                    int totalPages = payments.size() + 1;  // 1 summary + N payments

                    String itemName = formatItemName(payment);

                    // Line 0: "for Qx"
                    side.setLine(0, "§efor " + payment.getAmount() + "x");

                    // Lines 1-2: Item name (wrap if > 15 chars)
                    if (itemName.length() > 15) {
                        // Split at last space before 15 chars, or at 15 if no space
                        int splitIndex = itemName.lastIndexOf(' ', 15);
                        if (splitIndex == -1) splitIndex = 15;

                        side.setLine(1, "§e" + itemName.substring(0, splitIndex).trim());
                        side.setLine(2, "§e" + itemName.substring(splitIndex).trim());
                    } else {
                        // Short name: Line 1 only, line 2 blank
                        side.setLine(1, "§e" + itemName);
                        side.setLine(2, "");
                    }

                    // Line 3: "page X of Y"
                    side.setLine(3, "§6page " + displayPageNumber + " of " + totalPages);
                }
            }
        } else {
            // BUY/SELL mode
            ItemStack priceItem = barterSign.getPriceItem();
            int priceAmount = barterSign.getPriceAmount();

            if (offeringWrapped) {
                // Offering uses lines 1-2, condense price to line 3
                side.setLine(3, "§e" + priceAmount + "x " + formatItemName(priceItem));
            } else {
                // Offering uses line 1, price uses lines 2-3 as before
                side.setLine(2, "§e" + priceAmount + "x");
                side.setLine(3, "§e" + formatItemName(priceItem));
            }
        }
    }

    /**
     * Displays owner-facing BOARD view with shop summary.
     * Shows payment count and may display owner preview mode indicator.
     * Single-payment BARTER shops omit header (same as customer view).
     */
    private static void displayOwnerBoardView(SignSide side, BarterSign barterSign) {
        SignType type = barterSign.getType();
        ItemStack offering = barterSign.getItemOffering();

        // Determine if this is a single-payment BARTER shop (no header needed)
        List<ItemStack> payments = (type == SignType.BARTER) ? barterSign.getAcceptedPayments() : null;
        boolean isSinglePaymentBarter = (type == SignType.BARTER && payments != null && payments.size() == 1);

        // Line 0: Shop type header (only for non-single-payment or non-BARTER)
        if (!isSinglePaymentBarter) {
            String header = switch(type) {
                case BARTER -> "§2[Barter]";
                case BUY -> "§e[We Buy]";
                case SELL -> "§a[We Sell]";
            };
            side.setLine(0, header);
        }

        // Show offering (with wrapping support) - use line 0 for single-payment, line 1 for others
        int offeringStartLine = isSinglePaymentBarter ? 0 : 1;
        boolean offeringWrapped = false;
        if (offering != null) {
            offeringWrapped = displayOfferingWithWrapping(side, offering, offeringStartLine);
        }

        // Show preview mode indicator or payment summary
        if (barterSign.isOwnerPreviewMode()) {
            if (offeringWrapped) {
                side.setLine(3, "§eCustomer View");
            } else {
                side.setLine(2, "§e[Customer View]");
                side.setLine(3, "§eSneak+R: exit");
            }
        } else if (type == SignType.BARTER) {
            // payments already fetched above
            if (payments.size() == 1) {
                ItemStack payment = payments.get(0);
                String paymentName = formatItemName(payment);
                // Account for "for: Nx " prefix when deciding if payment needs wrapping
                boolean paymentNeedsWrapping = paymentName != null &&
                    ("for: " + payment.getAmount() + "x " + paymentName).length() > SignLayoutFactory.MAX_LINE_LENGTH;

                if (offeringWrapped && paymentNeedsWrapping) {
                    // Both offering and payment wrapped: wrap payment across lines 2-3
                    String paymentPrefix = "for: " + payment.getAmount() + "x ";
                    int paymentSplit = computeNameSplit(paymentName, paymentPrefix.length());
                    side.setLine(2, "§e" + paymentPrefix + paymentName.substring(0, paymentSplit).trim());
                    side.setLine(3, "§e" + SignLayoutFactory.truncateForSign(
                        paymentName.substring(paymentSplit).trim(), SignLayoutFactory.MAX_LINE_LENGTH));
                } else if (offeringWrapped) {
                    // Offering wrapped, payment short: condensed on line 3
                    side.setLine(2, "");
                    side.setLine(3, "§efor: " + payment.getAmount() + "x " + formatItemName(payment));
                } else {
                    side.setLine(2, "§efor: " + payment.getAmount() + "x");
                    side.setLine(3, "§e" + formatItemName(payment));
                }
            } else {
                if (offeringWrapped) {
                    side.setLine(3, "§e" + payments.size() + " pay options");
                } else {
                    side.setLine(2, "§e" + payments.size() + " payment");
                    side.setLine(3, "§eoptions");
                }
            }
        } else {
            ItemStack priceItem = barterSign.getPriceItem();
            int priceAmount = barterSign.getPriceAmount();

            if (offeringWrapped) {
                // Offering uses lines 1-2, condense price to line 3
                side.setLine(3, "§e" + priceAmount + "x " + formatItemName(priceItem));
            } else {
                // Offering uses line 1, price uses lines 2-3 as before
                side.setLine(2, "§e" + priceAmount + "x");
                side.setLine(3, "§e" + formatItemName(priceItem));
            }
        }
    }

    /**
     * Displays offering with name wrapping support.
     * If offering name > 15 chars, wraps across two consecutive lines.
     *
     * @param side Sign side to update
     * @param offering Offering item
     * @param startLine Starting line number (default 1 for standard display, 0 for single-payment)
     * @return true if name was wrapped, false if single-line
     */
    private static boolean displayOfferingWithWrapping(SignSide side, ItemStack offering, int startLine) {
        if (offering == null) {
            return false;
        }

        String itemName = formatItemName(offering);
        int amount = offering.getAmount();
        String prefix = amount + "x ";

        // Wrap when the full line (prefix + name) overflows
        if ((prefix + itemName).length() > SignLayoutFactory.MAX_LINE_LENGTH) {
            int splitIndex = computeNameSplit(itemName, prefix.length());

            // First line: amount + first part of name
            side.setLine(startLine, "§b" + prefix + itemName.substring(0, splitIndex).trim());

            // Second line: remainder (truncate only as last resort)
            side.setLine(startLine + 1, "§b" + SignLayoutFactory.truncateForSign(
                itemName.substring(splitIndex).trim(), SignLayoutFactory.MAX_LINE_LENGTH));

            return true; // Wrapped
        } else {
            // Full line fits: single line
            side.setLine(startLine, "§b" + prefix + itemName);
            return false; // Not wrapped
        }
    }

    /**
     * Backward-compatible overload: defaults to line 1 for standard header display.
     */
    private static boolean displayOfferingWithWrapping(SignSide side, ItemStack offering) {
        return displayOfferingWithWrapping(side, offering, 1);
    }

    /**
     * Displays payment with name wrapping support.
     * If payment name > 15 chars, wraps across two lines.
     *
     * @param side Sign side to update
     * @param payment Payment item
     * @param startLine Starting line number (typically 2)
     * @return true if name was wrapped, false if single-line
     */
    private static boolean displayPaymentWithWrapping(SignSide side, ItemStack payment, int startLine) {
        if (payment == null) {
            return false;
        }

        String itemName = formatItemName(payment);
        int amount = payment.getAmount();
        String prefix = "for: " + amount + "x ";

        // Wrap when the full line (prefix + name) overflows
        if ((prefix + itemName).length() > SignLayoutFactory.MAX_LINE_LENGTH) {
            int splitIndex = computeNameSplit(itemName, prefix.length());

            // Line N: "for: Qx FirstPart"
            side.setLine(startLine, "§e" + prefix + itemName.substring(0, splitIndex).trim());

            // Line N+1: remainder (truncate only as last resort)
            side.setLine(startLine + 1, "§e" + SignLayoutFactory.truncateForSign(
                itemName.substring(splitIndex).trim(), SignLayoutFactory.MAX_LINE_LENGTH));

            return true; // Wrapped
        } else {
            // Full line fits: single line
            side.setLine(startLine, "§e" + prefix + itemName);
            return false; // Not wrapped
        }
    }

    /**
     * Displays dual-wrap mode for single-payment BARTER shops.
     * Removes header and uses all 4 lines for offering + payment.
     * Only called when BOTH offering and payment names > 15 chars.
     *
     * @param side Sign side to update
     * @param offering Offering item
     * @param payment Payment item
     */
    private static void displayDualWrapMode(SignSide side, ItemStack offering, ItemStack payment) {
        String offeringName = formatItemName(offering);
        String paymentName = formatItemName(payment);

        // Lines 0-1: Offering (wrapped, starting at line 0 - no header)
        String offeringPrefix = offering.getAmount() + "x ";
        int offeringSplit = computeNameSplit(offeringName, offeringPrefix.length());

        side.setLine(0, "§b" + offeringPrefix + offeringName.substring(0, offeringSplit).trim());
        side.setLine(1, "§b" + SignLayoutFactory.truncateForSign(
            offeringName.substring(offeringSplit).trim(), SignLayoutFactory.MAX_LINE_LENGTH));

        // Lines 2-3: Payment (wrapped)
        String paymentPrefix = "for: " + payment.getAmount() + "x ";
        int paymentSplit = computeNameSplit(paymentName, paymentPrefix.length());

        side.setLine(2, "§e" + paymentPrefix + paymentName.substring(0, paymentSplit).trim());
        side.setLine(3, "§e" + SignLayoutFactory.truncateForSign(
            paymentName.substring(paymentSplit).trim(), SignLayoutFactory.MAX_LINE_LENGTH));
    }

    private static void displayTypeMode(SignSide side, BarterSign barterSign) {
        side.setLine(0, "§e[Shop Type]");
        SignType currentType = barterSign.getType();

        if (barterSign.isTypeDetected()) {
            // Inventory type locked, but can still change shop type
            side.setLine(1, "§eL-Click to cycle");
            side.setLine(2, "§bType: " + currentType.name());
            side.setLine(3, "§6(Inv: " + (barterSign.getShopStackableMode() ? "STACK" : "UNIQ") + ")");
        } else {
            side.setLine(1, "§eL-Click to cycle");
            side.setLine(2, "§bType: " + currentType.name());
            side.setLine(3, "");
        }
    }

    private static void displayHelpMode(SignSide side) {
        // DELEGATION: Use SignLayoutFactory (Phase 2 refactoring)
        applyLayoutToSign(side, SignLayoutFactory.createHelpLayout());
    }

    private static void displayDeleteMode(SignSide side) {
        // DELEGATION: Use SignLayoutFactory (Phase 2 refactoring)
        applyLayoutToSign(side, SignLayoutFactory.createDeleteLayout());
    }

    public static void displayDeleteConfirmation(Sign sign) {
        SignSide frontSide = sign.getSide(Side.FRONT);
        frontSide.setLine(0, "§c[CONFIRM?]");
        frontSide.setLine(1, "§cL-Click AGAIN");
        frontSide.setLine(2, "§cto confirm");
        frontSide.setLine(3, "§e(5s timeout)");
        sign.update();
    }

    /**
     * Computes the word-break split index for an item name that follows a prefix on a sign line.
     * Finds the last space within the characters available after the prefix, falling back to a
     * hard split only when no word boundary exists.
     *
     * @param name         Item name to split
     * @param prefixLength Characters already consumed by the prefix (e.g. "70x " = 4)
     * @return Index into name: [0..index) on line 1, [index..) on line 2
     */
    private static int computeNameSplit(String name, int prefixLength) {
        int available = SignLayoutFactory.MAX_LINE_LENGTH - prefixLength;
        if (available <= 0) return 0;
        int splitIndex = name.lastIndexOf(' ', available);
        return splitIndex <= 0 ? Math.min(available, name.length()) : splitIndex;
    }

    /**
     * Applies a 4-line layout array to a sign side.
     * Helper method for delegating to SignLayoutFactory layouts.
     *
     * @param side The SignSide to apply the layout to
     * @param layout The 4-line layout array
     */
    private static void applyLayoutToSign(SignSide side, String[] layout) {
        for (int i = 0; i < 4 && i < layout.length; i++) {
            side.setLine(i, layout[i] != null ? layout[i] : "");
        }
    }

    static String formatItemName(ItemStack item) {
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
}
