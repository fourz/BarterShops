package org.fourz.BarterShops.sign.renderer;

import org.bukkit.block.sign.SignSide;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.BarterShops.sign.factory.SignLayoutFactory;

/** Renders the TYPE mode sign layout. Delegates to {@link SignLayoutFactory}. */
public class TypeModeRenderer implements ISignModeRenderer {

    @Override
    public void render(SignSide side, BarterSign barterSign, boolean isCustomerView) {
        SignRenderUtil.applyLayoutToSign(side, SignLayoutFactory.createTypeLayout(barterSign));
    }
}
