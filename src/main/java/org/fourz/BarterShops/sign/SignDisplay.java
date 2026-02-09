package org.fourz.BarterShops.sign;

import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;

public class SignDisplay {

    public static void updateSign(Sign sign, BarterSign barterSign) {
        SignSide frontSide = sign.getSide(Side.FRONT);
        // Store the current sign side in the BarterSign
        barterSign.setSignSideDisplayFront(frontSide);

        switch (barterSign.getMode()) {
            case SETUP -> displaySetupMode(frontSide);
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

    private static void displaySetupMode(SignSide side) {
        side.setLine(0, "\u00A71[Barter Setup]");
        side.setLine(1, "Punch to");
        side.setLine(2, "configure");
        side.setLine(3, "");
    }

    /**
     * Displays the sign in BOARD mode, which is meant for customer display.
     * This is auto-generated during creation and allows the shop owner to later customize.
     */
    private static void displayBoardMode(SignSide side, BarterSign barterSign) {
        // Get the stored display state or use current side if not set
        SignSide displaySide = barterSign.getSignSideDisplayFront();
        if (displaySide != null) {
            // Copy stored state to current side
            for (int i = 0; i < 4; i++) {
                side.setLine(i, displaySide.getLine(i));
            }
        } else {
            // Default display if no stored state
            side.setLine(0, "\u00A72[Barter Shop]");
            side.setLine(1, "Trading Items");
            side.setLine(2, "Click to view");
            side.setLine(3, "\u00A73[Active]");
        }
    }

    private static void displayTypeMode(SignSide side, BarterSign barterSign) {
        side.setLine(0, "\u00A73[Select Type]");
        side.setLine(1, "< " + barterSign.getType().toString() + " >");
        side.setLine(2, "Click to change");
        side.setLine(3, "\u00A77[Save & Exit]");
    }

    private static void displayHelpMode(SignSide side) {
        side.setLine(0, "\u00A76[Help]");
        side.setLine(1, "Left: Select");
        side.setLine(2, "Right: Change");
        side.setLine(3, "Shift: Exit");
    }

    private static void displayDeleteMode(SignSide side) {
        side.setLine(0, "\u00A74[Delete Shop]");
        side.setLine(1, "Are you sure?");
        side.setLine(2, "Click again to");
        side.setLine(3, "\u00A7cCONFIRM");
    }
}
