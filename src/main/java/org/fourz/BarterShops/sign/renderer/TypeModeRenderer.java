package org.fourz.BarterShops.sign.renderer;

import org.bukkit.block.sign.SignSide;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.BarterShops.sign.SignType;

/** Renders the TYPE mode sign layout (shop type selection). */
public class TypeModeRenderer implements ISignModeRenderer {

    @Override
    public void render(SignSide side, BarterSign barterSign, boolean isCustomerView) {
        SignType currentType = barterSign.getType();
        side.setLine(0, "§e[Shop Type]");
        side.setLine(1, "§eL-Click to cycle");
        side.setLine(2, "§bType: " + currentType.name());
        if (barterSign.isTypeDetected()) {
            side.setLine(3, "§6(Inv: " + (barterSign.getShopStackableMode() ? "STACK" : "UNIQ") + ")");
        } else {
            side.setLine(3, "");
        }
    }
}
