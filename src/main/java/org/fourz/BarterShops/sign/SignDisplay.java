package org.fourz.BarterShops.sign;

import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.shop.ShopMode;

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
        updateSign(sign, barterSign, false);
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
        side.setLine(0, "§5[Setup]");

        // Step 1: Set offering item
        if (barterSign.getItemOffering() == null) {
            side.setLine(1, "§7L-Click with");
            side.setLine(2, "§7item to set");
            side.setLine(3, "§7offering");
            return;
        }

        // Step 2: Configure payment (type-dependent)
        SignType type = barterSign.getType();
        switch (type) {
            case BARTER -> {
                List<ItemStack> payments = barterSign.getAcceptedPayments();
                if (payments.isEmpty()) {
                    side.setLine(1, "§7L-Click with item");
                    side.setLine(2, "§7to add payment");
                    side.setLine(3, "§7option");
                } else {
                    side.setLine(1, "§7Payments: " + payments.size());
                    side.setLine(2, "§7L-Click: add");
                    side.setLine(3, "§7Shift+L: remove");
                }
            }
            case BUY, SELL -> {
                ItemStack priceItem = barterSign.getPriceItem();
                int priceAmount = barterSign.getPriceAmount();

                if (priceItem == null) {
                    side.setLine(1, "§7L-Click to set");
                    side.setLine(2, "§7price currency");
                    side.setLine(3, "§7(item in hand)");
                } else {
                    side.setLine(1, "§7Price: " + priceAmount);
                    side.setLine(2, "§7" + formatItemName(priceItem));
                    side.setLine(3, "§7L-Click ±1, Shift+R +16");
                }
            }
        }
    }

    /**
     * Router for BOARD mode display: customer view or owner view based on context.
     */
    private static void displayBoardMode(SignSide side, BarterSign barterSign, boolean isCustomerView) {
        if (!barterSign.isConfigured()) {
            displayNotConfigured(side, barterSign);
            return;
        }

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
        side.setLine(1, "§7Not configured");
        side.setLine(2, "§7Ask owner");
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
                    side.setLine(3, "§7No pay options");
                } else {
                    side.setLine(2, "§7No payment");
                    side.setLine(3, "§7options");
                }
            } else if (payments.size() == 1) {
                // Single payment: Show payment details (no pagination)
                ItemStack payment = payments.get(0);

                // Check if payment needs wrapping
                String paymentName = formatItemName(payment);
                boolean paymentNeedsWrapping = paymentName != null && paymentName.length() > 15;

                if (offeringWrapped && paymentNeedsWrapping) {
                    // DUAL-WRAP MODE: Both offering and payment have long names
                    // Remove header, use all 4 lines (0-3)
                    displayDualWrapMode(side, offering, payment);
                } else if (offeringWrapped) {
                    // Offering wrapped, payment short: condensed on line 3
                    side.setLine(3, "§7for: " + payment.getAmount() + "x " + formatItemName(payment));
                } else if (paymentNeedsWrapping) {
                    // Offering short, payment long: payment wraps on lines 2-3
                    displayPaymentWithWrapping(side, payment, 2);
                } else {
                    // Both short: standard 2-line display
                    side.setLine(2, "§7for: " + payment.getAmount() + "x /");
                    side.setLine(3, "§7" + formatItemName(payment));
                }
            } else {
                // Multiple payments: Show summary (page 0) or single payment per page (pages 1+)
                int currentPaymentIndex = barterSign.getCurrentPaymentPage();

                if (currentPaymentIndex == 0) {
                    // Summary view (page 1)
                    if (offeringWrapped) {
                        side.setLine(3, "§7" + payments.size() + " pay options");
                    } else {
                        side.setLine(2, "§7" + payments.size() + " payment");
                        side.setLine(3, "§7options");
                    }
                } else {
                    // Single payment per page (page 2+)
                    int paymentIndex = currentPaymentIndex - 1;  // Convert to 0-based payment array index
                    ItemStack payment = payments.get(paymentIndex);

                    int displayPageNumber = currentPaymentIndex + 1;  // Page 1 is summary, page 2+ are payments
                    int totalPages = payments.size() + 1;  // 1 summary + N payments

                    String itemName = formatItemName(payment);

                    // Line 0: "for Qx"
                    side.setLine(0, "§7for " + payment.getAmount() + "x");

                    // Lines 1-2: Item name (wrap if > 15 chars)
                    if (itemName.length() > 15) {
                        // Split at last space before 15 chars, or at 15 if no space
                        int splitIndex = itemName.lastIndexOf(' ', 15);
                        if (splitIndex == -1) splitIndex = 15;

                        side.setLine(1, "§7" + itemName.substring(0, splitIndex).trim());
                        side.setLine(2, "§7" + itemName.substring(splitIndex).trim());
                    } else {
                        // Short name: Line 1 only, line 2 blank
                        side.setLine(1, "§7" + itemName);
                        side.setLine(2, "");
                    }

                    // Line 3: "page X of Y"
                    side.setLine(3, "§8page " + displayPageNumber + " of " + totalPages);
                }
            }
        } else {
            // BUY/SELL mode
            ItemStack priceItem = barterSign.getPriceItem();
            int priceAmount = barterSign.getPriceAmount();

            if (offeringWrapped) {
                // Offering uses lines 1-2, condense price to line 3
                side.setLine(3, "§7" + priceAmount + "x " + formatItemName(priceItem));
            } else {
                // Offering uses line 1, price uses lines 2-3 as before
                side.setLine(2, "§7" + priceAmount + "x");
                side.setLine(3, "§7" + formatItemName(priceItem));
            }
        }
    }

    /**
     * Displays owner-facing BOARD view with shop summary.
     * Shows payment count and may display owner preview mode indicator.
     */
    private static void displayOwnerBoardView(SignSide side, BarterSign barterSign) {
        SignType type = barterSign.getType();
        ItemStack offering = barterSign.getItemOffering();

        // Line 0: Shop type header
        String header = switch(type) {
            case BARTER -> "§2[Barter]";
            case BUY -> "§e[We Buy]";
            case SELL -> "§a[We Sell]";
        };
        side.setLine(0, header);

        // Show offering (with wrapping support)
        boolean offeringWrapped = false;
        if (offering != null) {
            offeringWrapped = displayOfferingWithWrapping(side, offering);
        }

        // Show preview mode indicator or payment summary
        if (barterSign.isOwnerPreviewMode()) {
            if (offeringWrapped) {
                side.setLine(3, "§7Customer View");
            } else {
                side.setLine(2, "§7[Customer View]");
                side.setLine(3, "§7Sneak+R: exit");
            }
        } else if (type == SignType.BARTER) {
            List<ItemStack> payments = barterSign.getAcceptedPayments();
            if (payments.size() == 1) {
                ItemStack payment = payments.get(0);
                if (offeringWrapped) {
                    // Condensed format
                    side.setLine(3, "§7for: " + payment.getAmount() + "x " + formatItemName(payment));
                } else {
                    side.setLine(2, "§7for: " + payment.getAmount() + "x");
                    side.setLine(3, "§7" + formatItemName(payment));
                }
            } else {
                if (offeringWrapped) {
                    side.setLine(3, "§7" + payments.size() + " pay options");
                } else {
                    side.setLine(2, "§7" + payments.size() + " payment");
                    side.setLine(3, "§7options");
                }
            }
        } else {
            ItemStack priceItem = barterSign.getPriceItem();
            int priceAmount = barterSign.getPriceAmount();

            if (offeringWrapped) {
                // Offering uses lines 1-2, condense price to line 3
                side.setLine(3, "§7" + priceAmount + "x " + formatItemName(priceItem));
            } else {
                // Offering uses line 1, price uses lines 2-3 as before
                side.setLine(2, "§7" + priceAmount + "x");
                side.setLine(3, "§7" + formatItemName(priceItem));
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

        // Check if name needs wrapping
        if (itemName.length() > 15) {
            // Split at last space before 15 chars, or at 15 if no space
            int splitIndex = itemName.lastIndexOf(' ', 15);
            if (splitIndex == -1) splitIndex = 15;

            // First line: amount + first part of name
            side.setLine(startLine, "§b" + amount + "x " + itemName.substring(0, splitIndex).trim());

            // Second line: remainder of name
            side.setLine(startLine + 1, "§b" + itemName.substring(splitIndex).trim());

            return true; // Wrapped
        } else {
            // Short name: single line
            side.setLine(startLine, "§b" + amount + "x " + itemName);
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

        // Check if name needs wrapping
        if (itemName.length() > 15) {
            // Split at last space before 15 chars, or at 15 if no space
            int splitIndex = itemName.lastIndexOf(' ', 15);
            if (splitIndex == -1) splitIndex = 15;

            // Line N: "for: Qx FirstPart"
            side.setLine(startLine, "§7for: " + amount + "x " + itemName.substring(0, splitIndex).trim());

            // Line N+1: "SecondPart"
            side.setLine(startLine + 1, "§7" + itemName.substring(splitIndex).trim());

            return true; // Wrapped
        } else {
            // Short name: single line
            side.setLine(startLine, "§7for: " + amount + "x " + itemName);
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
        int offeringSplit = offeringName.lastIndexOf(' ', 15);
        if (offeringSplit == -1) offeringSplit = 15;

        side.setLine(0, "§b" + offering.getAmount() + "x " + offeringName.substring(0, offeringSplit).trim());
        side.setLine(1, "§b" + offeringName.substring(offeringSplit).trim());

        // Lines 2-3: Payment (wrapped)
        int paymentSplit = paymentName.lastIndexOf(' ', 15);
        if (paymentSplit == -1) paymentSplit = 15;

        side.setLine(2, "§7for: " + payment.getAmount() + "x " + paymentName.substring(0, paymentSplit).trim());
        side.setLine(3, "§7" + paymentName.substring(paymentSplit).trim());
    }

    private static void displayTypeMode(SignSide side, BarterSign barterSign) {
        side.setLine(0, "§e[Shop Type]");
        SignType currentType = barterSign.getType();

        if (barterSign.isTypeDetected()) {
            // Inventory type locked, but can still change shop type
            side.setLine(1, "§7L-Click to cycle");
            side.setLine(2, "§bType: " + currentType.name());
            side.setLine(3, "§8(Inv: " + (barterSign.getShopStackableMode() ? "STACK" : "UNIQ") + ")");
        } else {
            side.setLine(1, "§7L-Click to cycle");
            side.setLine(2, "§bType: " + currentType.name());
            side.setLine(3, "");
        }
    }

    private static void displayHelpMode(SignSide side) {
        side.setLine(0, "§b[Help]");
        side.setLine(1, "§7Owner:");
        side.setLine(2, "§7L-Click = mode");
        side.setLine(3, "§7R-Click = act");
    }

    private static void displayDeleteMode(SignSide side) {
        side.setLine(0, "§c[DELETE?]");
        side.setLine(1, "§7L-Click to");
        side.setLine(2, "§7delete shop");
        side.setLine(3, "§7R-Click cancel");
    }

    public static void displayDeleteConfirmation(Sign sign) {
        SignSide frontSide = sign.getSide(Side.FRONT);
        frontSide.setLine(0, "§c[CONFIRM?]");
        frontSide.setLine(1, "§cL-Click AGAIN");
        frontSide.setLine(2, "§cto confirm");
        frontSide.setLine(3, "§7(5s timeout)");
        sign.update();
    }

    private static String formatItemName(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "None";
        }
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        String name = item.getType().name().toLowerCase().replace('_', ' ');
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}
