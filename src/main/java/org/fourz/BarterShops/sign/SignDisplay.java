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
            case BOARD -> displayBoardMode(frontSide, barterSign);  // Changed method name
            case TYPE -> displayTypeMode(frontSide, barterSign);
            case HELP -> displayHelpMode(frontSide);
            case DELETE -> displayDeleteMode(frontSide);
        }
        sign.update();
    }

    private static void displaySetupMode(SignSide side) {
        side.setLine(0, "§1[Barter Setup]");
        side.setLine(1, "Use with item to");
        side.setLine(2, "accept for trade");
        side.setLine(3, "<Click>");
    }

    private static void displayBoardMode(SignSide side, BarterSign barterSign) {  // Renamed method
        // Get the stored display state or use current side if not set
        SignSide displaySide = barterSign.getSignSideDisplayFront();
        if (displaySide != null) {
            // Copy stored state to current side
            for (int i = 0; i < 4; i++) {
                side.setLine(i, displaySide.getLine(i));
            }
        } else {
            // Default display if no stored state
            side.setLine(0, "§2[Barter Shop]");
            side.setLine(1, "Trading Items");
            side.setLine(2, "Click to view");
            side.setLine(3, "§3[Active]");
        }
    }

    private static void displayTypeMode(SignSide side, BarterSign barterSign) {
        side.setLine(0, "§3[Select Type]");
        side.setLine(1, "< " + barterSign.getType().toString() + " >");
        side.setLine(2, "Click to change");
        side.setLine(3, "§7[Save & Exit]");
    }

    private static void displayHelpMode(SignSide side) {
        side.setLine(0, "§6[Help]");
        side.setLine(1, "Left: Select");
        side.setLine(2, "Right: Change");
        side.setLine(3, "Shift: Exit");
    }

    private static void displayDeleteMode(SignSide side) {
        side.setLine(0, "§4[Delete Shop]");
        side.setLine(1, "Are you sure?");
        side.setLine(2, "Click again to");
        side.setLine(3, "§cCONFIRM");
    }
}
