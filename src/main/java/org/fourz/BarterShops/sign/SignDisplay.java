package org.fourz.BarterShops.sign;

import org.bukkit.block.Sign;

public class SignDisplay {
    
    public static void updateSign(Sign sign, BarterSign barterSign) {
        switch (barterSign.getMode()) {
            case SETUP:
                displaySetupMode(sign, barterSign);
                break;
            case MAIN:
                displayMainMode(sign, barterSign);
                break;
            case TYPE:
                displayTypeMode(sign, barterSign);
                break;
            case HELP:
                displayHelpMode(sign, barterSign);
                break;
            case DELETE:
                displayDeleteMode(sign, barterSign);
                break;
        }
        sign.update();
    }

    private static void displaySetupMode(Sign sign, BarterSign barterSign) {
        sign.setLine(0, "§1[Barter Setup]");
        sign.setLine(1, "Click to select");
        sign.setLine(2, "items to trade");
        sign.setLine(3, "<Click>");
    }

    private static void displayMainMode(Sign sign, BarterSign barterSign) {
        sign.setLine(0, "§2[Barter Shop]");
        sign.setLine(1, "Trading:");
        sign.setLine(2, "Click to view");
        sign.setLine(3, "§3[Active]");
    }

    private static void displayTypeMode(Sign sign, BarterSign barterSign) {
        sign.setLine(0, "§3[Select Type]");
        sign.setLine(1, "< " + barterSign.getType().toString() + " >");
        sign.setLine(2, "Click to change");
        sign.setLine(3, "§7[Save & Exit]");
    }

    private static void displayHelpMode(Sign sign, BarterSign barterSign) {
        sign.setLine(0, "§6[Help]");
        sign.setLine(1, "Left: Select");
        sign.setLine(2, "Right: Change");
        sign.setLine(3, "Shift: Exit");
    }

    private static void displayDeleteMode(Sign sign, BarterSign barterSign) {
        sign.setLine(0, "§4[Delete Shop]");
        sign.setLine(1, "Are you sure?");
        sign.setLine(2, "Click again to");
        sign.setLine(3, "§cCONFIRM");
    }
}
