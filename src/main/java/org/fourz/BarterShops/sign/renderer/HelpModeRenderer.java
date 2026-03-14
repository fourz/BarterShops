package org.fourz.BarterShops.sign.renderer;

import org.bukkit.block.sign.SignSide;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.BarterShops.sign.factory.SignLayoutFactory;

/** Renders the HELP mode sign layout. Delegates to {@link SignLayoutFactory}. */
public class HelpModeRenderer implements ISignModeRenderer {

    @Override
    public void render(SignSide side, BarterSign barterSign, boolean isCustomerView) {
        SignRenderUtil.applyLayoutToSign(side, SignLayoutFactory.createHelpLayout());
    }
}
