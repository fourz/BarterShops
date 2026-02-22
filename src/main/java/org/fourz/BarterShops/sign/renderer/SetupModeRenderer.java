package org.fourz.BarterShops.sign.renderer;

import org.bukkit.block.sign.SignSide;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.BarterShops.sign.factory.SignLayoutFactory;

/** Renders the SETUP mode sign layout. Delegates to {@link SignLayoutFactory}. */
public class SetupModeRenderer implements ISignModeRenderer {

    @Override
    public void render(SignSide side, BarterSign barterSign, boolean isCustomerView) {
        SignRenderUtil.applyLayoutToSign(side, SignLayoutFactory.createSetupLayout(barterSign));
    }
}
