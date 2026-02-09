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
        if (barterSign.getType() == SignType.STACKABLE) {
            if (barterSign.getItemOffering() == null) {
                side.setLine(1, "§7L-Click with");
                side.setLine(2, "§7item to sell");
                side.setLine(3, "");
            } else {
                side.setLine(1, "§7R-Click with");
                side.setLine(2, "§7payment item");
                side.setLine(3, "§7to set price");
            }
        } else {
            side.setLine(1, "§7Fill chest");
            side.setLine(2, "§7Set price");
            side.setLine(3, "§7R-Click ready");
        }
    }

    /**
     * Displays the sign in BOARD mode, which is meant for customer display.
     * Shows offering and price information.
     */
    private static void displayBoardMode(SignSide side, BarterSign barterSign) {
        side.setLine(0, "§2[Barter Shop]");
        ItemStack offering = barterSign.getItemOffering();
        ItemStack payment = barterSign.getPriceItem();

        if (barterSign.getType() == SignType.STACKABLE && offering != null) {
            side.setLine(1, offering.getAmount() + "x " + formatItemName(offering));
            side.setLine(2, "§9for");
            side.setLine(3, barterSign.getPriceAmount() + "x " + formatItemName(payment));
        } else if (barterSign.getType() == SignType.UNSTACKABLE) {
            side.setLine(1, "§7Items in chest");
            side.setLine(2, "§9Price:");
            side.setLine(3, barterSign.getPriceAmount() + "x " + formatItemName(payment));
        } else {
            // Default display if not fully configured
            side.setLine(1, "§7Trading Items");
            side.setLine(2, "§7Click to view");
            side.setLine(3, "§3[Active]");
        }
    }

    private static void displayTypeMode(SignSide side, BarterSign barterSign) {
        side.setLine(0, "§e[Type Mode]");
        side.setLine(1, "§7R-Click to");
        side.setLine(2, "§7cycle type:");
        side.setLine(3, "§b" + barterSign.getType().name());
    }

    private static void displayHelpMode(SignSide side) {
        side.setLine(0, "§b[Help]");
        side.setLine(1, "§7Owner:");
        side.setLine(2, "§7L-Click = mode");
        side.setLine(3, "§7R-Click = act");
    }

    private static void displayDeleteMode(SignSide side) {
        side.setLine(0, "§c[DELETE?]");
        side.setLine(1, "§7Break sign");
        side.setLine(2, "§7to confirm");
        side.setLine(3, "§7R-Click cancel");
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
