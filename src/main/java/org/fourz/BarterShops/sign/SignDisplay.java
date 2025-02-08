package org.fourz.BarterShops.sign;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Sign;

public class SignDisplay {
    public static void updateSign(Sign sign, BarterSign barterSign) {
        switch (barterSign.getMode()) {
            case SETUP -> displaySetupMode(sign, barterSign);
            case MAIN -> displayMainMode(sign, barterSign);
            case TYPE -> displayTypeMode(sign, barterSign);
            case HELP -> displayHelpMode(sign, barterSign);
            case DELETE -> displayDeleteMode(sign, barterSign);
        }
        sign.update();
    }

    private static void displaySetupMode(Sign sign, BarterSign barterSign) {
        sign.setLine(0, "[Barter Setup]");
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
