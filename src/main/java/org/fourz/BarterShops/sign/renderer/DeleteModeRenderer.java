package org.fourz.BarterShops.sign.renderer;

import org.bukkit.block.sign.SignSide;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.BarterShops.sign.factory.SignLayoutFactory;

/** Renders the DELETE mode sign layout. Delegates to {@link SignLayoutFactory}. */
public class DeleteModeRenderer implements ISignModeRenderer {

    @Override
    public void render(SignSide side, BarterSign barterSign, boolean isCustomerView) {
        SignRenderUtil.applyLayoutToSign(side, SignLayoutFactory.createDeleteLayout());
    }
}
