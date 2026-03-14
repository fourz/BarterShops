package org.fourz.BarterShops.sign.renderer;

import org.bukkit.block.sign.SignSide;
import org.fourz.BarterShops.sign.BarterSign;

/**
 * OCP-compliant renderer for a single ShopMode's sign display.
 *
 * Each implementation owns the rendering logic for exactly one mode,
 * keeping SignDisplay as a thin dispatcher and making it trivial to add
 * new modes or test each mode's rendering in isolation.
 *
 * BarterShops implementations: {@link SetupModeRenderer}, {@link BoardModeRenderer},
 * {@link TypeModeRenderer}, {@link HelpModeRenderer}, {@link DeleteModeRenderer}.
 */
public interface ISignModeRenderer {

    /**
     * Writes the sign text for this mode onto the given side.
     *
     * @param side           The sign side to populate (lines 0–3)
     * @param barterSign     The sign data model
     * @param isCustomerView True for customer perspective, false for owner
     */
    void render(SignSide side, BarterSign barterSign, boolean isCustomerView);
}
