package org.fourz.BarterShops.sign;

import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.shop.ShopMode;

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
        if (barterSign.isTypeDetected()) {
            // Type already locked - show type-specific setup instructions
            SignType type = barterSign.getType();
            switch (type) {
                case BARTER -> {
                    side.setLine(1, "§7L-Click to set");
                    side.setLine(2, "§7item for trade");
                    side.setLine(3, "");
                }
                case BUY -> {
                    side.setLine(1, "§7L-Click to");
                    side.setLine(2, "§7set buy price");
                    side.setLine(3, "");
                }
                case SELL -> {
                    side.setLine(1, "§7L-Click to");
                    side.setLine(2, "§7set sell price");
                    side.setLine(3, "");
                }
            }
        } else {
            // Type not yet set - show item setup instructions
            side.setLine(1, "§7L-Click with");
            side.setLine(2, "§7item to");
            side.setLine(3, "§7setup shop");
        }
    }

    /**
     * Displays the sign in BOARD mode, which is meant for customer display.
     * Shows items in chest and price information.
     */
    private static void displayBoardMode(SignSide side, BarterSign barterSign) {
        side.setLine(0, "§2[Barter Shop]");
        ItemStack payment = barterSign.getPriceItem();

        if (payment != null && barterSign.getPriceAmount() > 0) {
            side.setLine(1, "§7Items in chest");
            side.setLine(2, "§9Price:");
            side.setLine(3, barterSign.getPriceAmount() + "x " + formatItemName(payment));
        } else {
            // Not configured
            side.setLine(1, "§7Not configured");
            side.setLine(2, "§7Ask owner");
            side.setLine(3, "");
        }
    }

    private static void displayTypeMode(SignSide side, BarterSign barterSign) {
        side.setLine(0, "§e[Type Mode]");

        if (barterSign.isTypeDetected()) {
            // Inventory type is locked - show lock status
            String inventoryType = barterSign.getShopStackableMode() ? "STACKABLE" : "UNSTACKABLE";
            side.setLine(1, "§c✗ Type LOCKED");
            side.setLine(2, "§e" + inventoryType);
            side.setLine(3, "§7(delete shop to change)");
        } else {
            // Inventory type not yet detected
            side.setLine(1, "§7L-Click to");
            side.setLine(2, "§7cycle type:");
            side.setLine(3, "§b" + barterSign.getType().name());
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
