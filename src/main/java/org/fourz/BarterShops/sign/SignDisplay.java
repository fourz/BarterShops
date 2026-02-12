package org.fourz.BarterShops.sign;

import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.shop.ShopMode;

import java.util.List;

public class SignDisplay {

    public static void updateSign(Sign sign, BarterSign barterSign) {
        SignSide frontSide = sign.getSide(Side.FRONT);
        // Store the current sign side in the BarterSign
        barterSign.setSignSideDisplayFront(frontSide);

        switch (barterSign.getMode()) {
            case SETUP -> displaySetupMode(frontSide, barterSign);
            case BOARD -> displayBoardMode(frontSide, barterSign);
            case TYPE -> displayTypeMode(frontSide, barterSign);
            case HELP -> displayHelpMode(frontSide);
            case DELETE -> displayDeleteMode(frontSide);
        }
        sign.update();
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
     * Displays the sign in BOARD mode, which is meant for customer display.
     * Shows shop type, offering, and price/payment information.
     */
    private static void displayBoardMode(SignSide side, BarterSign barterSign) {
        SignType type = barterSign.getType();
        ItemStack offering = barterSign.getItemOffering();

        // Line 0: Shop type header
        String header = switch(type) {
            case BARTER -> "§2[Barter]";
            case BUY -> "§e[We Buy]";
            case SELL -> "§a[We Sell]";
        };
        side.setLine(0, header);

        if (!barterSign.isConfigured()) {
            side.setLine(1, "§7Not configured");
            side.setLine(2, "§7Ask owner");
            side.setLine(3, "");
            return;
        }

        // Show offering and pricing
        if (offering != null) {
            side.setLine(1, "§b" + offering.getAmount() + "x " + formatItemName(offering));
        }

        if (type == SignType.BARTER) {
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
