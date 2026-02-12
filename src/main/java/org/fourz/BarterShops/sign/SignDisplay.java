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
     * Shows current payment option with pagination indicator if multiple options.
     */
    private static void displayCustomerPaymentPage(SignSide side, BarterSign barterSign) {
        SignType type = barterSign.getType();
        ItemStack offering = barterSign.getItemOffering();

        // Line 0: Shop type header
        String header = switch(type) {
            case BARTER -> "§2[Barter]";
            case BUY -> "§e[We Buy]";
            case SELL -> "§a[We Sell]";
        };
        side.setLine(0, header);

        // Line 1: Offering
        if (offering != null) {
            side.setLine(1, "§b" + offering.getAmount() + "x " + formatItemName(offering));
        }

        // Lines 2-3: Payment info (type-dependent)
        if (type == SignType.BARTER) {
            List<ItemStack> payments = barterSign.getAcceptedPayments();
            if (payments.isEmpty()) {
                side.setLine(2, "§7No payment");
                side.setLine(3, "§7options");
            } else {
                int currentPage = barterSign.getCurrentPaymentPage();
                ItemStack currentPayment = payments.get(currentPage);

                // Line 2: Payment amount and item name
                String paymentLine = "§7for: " + currentPayment.getAmount() + "x /";
                side.setLine(2, paymentLine);

                // Line 3: Item name with optional pagination indicator
                String itemLine = "§7" + formatItemName(currentPayment);
                if (payments.size() > 1) {
                    itemLine += " §8(" + (currentPage + 1) + "/" + payments.size() + ")";
                }
                side.setLine(3, itemLine);
            }
        } else {
            // BUY/SELL mode
            ItemStack priceItem = barterSign.getPriceItem();
            int priceAmount = barterSign.getPriceAmount();
            side.setLine(2, "§7" + priceAmount + "x");
            side.setLine(3, "§7" + formatItemName(priceItem));
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

        // Show offering
        if (offering != null) {
            side.setLine(1, "§b" + offering.getAmount() + "x " + formatItemName(offering));
        }

        // Show preview mode indicator or payment summary
        if (barterSign.isOwnerPreviewMode()) {
            side.setLine(2, "§7[Customer View]");
            side.setLine(3, "§7Sneak+R: exit");
        } else if (type == SignType.BARTER) {
            List<ItemStack> payments = barterSign.getAcceptedPayments();
            if (payments.size() == 1) {
                ItemStack payment = payments.get(0);
                side.setLine(2, "§7for: " + payment.getAmount() + "x");
                side.setLine(3, "§7" + formatItemName(payment));
            } else {
                side.setLine(2, "§7" + payments.size() + " payment");
                side.setLine(3, "§7options");
            }
        } else {
            ItemStack priceItem = barterSign.getPriceItem();
            int priceAmount = barterSign.getPriceAmount();
            side.setLine(2, "§7" + priceAmount + "x");
            side.setLine(3, "§7" + formatItemName(priceItem));
        }
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
