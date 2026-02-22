package org.fourz.BarterShops.sign;

import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.shop.ShopMode;
import org.fourz.BarterShops.sign.renderer.BoardModeRenderer;
import org.fourz.BarterShops.sign.renderer.DeleteModeRenderer;
import org.fourz.BarterShops.sign.renderer.HelpModeRenderer;
import org.fourz.BarterShops.sign.renderer.ISignModeRenderer;
import org.fourz.BarterShops.sign.renderer.SetupModeRenderer;
import org.fourz.BarterShops.sign.renderer.SignRenderUtil;
import org.fourz.BarterShops.sign.renderer.TypeModeRenderer;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Thin dispatcher that routes sign-update requests to the appropriate
 * {@link ISignModeRenderer} based on the shop's current mode.
 *
 * All rendering logic lives in the renderer implementations inside the
 * {@code sign.renderer} package; this class owns only the dispatch table
 * and the two ad-hoc helpers ({@link #displayTemporaryMessage} and
 * {@link #displayDeleteConfirmation}) that operate on a full {@link Sign}.
 */
public class SignDisplay {

    private static final Map<ShopMode, ISignModeRenderer> RENDERERS;

    static {
        Map<ShopMode, ISignModeRenderer> map = new EnumMap<>(ShopMode.class);
        map.put(ShopMode.SETUP,  new SetupModeRenderer());
        map.put(ShopMode.BOARD,  new BoardModeRenderer());
        map.put(ShopMode.TYPE,   new TypeModeRenderer());
        map.put(ShopMode.HELP,   new HelpModeRenderer());
        map.put(ShopMode.DELETE, new DeleteModeRenderer());
        RENDERERS = Collections.unmodifiableMap(map);
    }

    /**
     * Updates the sign display with customer or owner view.
     *
     * @param sign           The sign to update
     * @param barterSign     The sign data model
     * @param isCustomerView True to show customer view, false for owner view
     */
    public static void updateSign(Sign sign, BarterSign barterSign, boolean isCustomerView) {
        SignSide frontSide = sign.getSide(Side.FRONT);
        barterSign.setSignSideDisplayFront(frontSide);

        // DEBUG: Log preview mode state for bug-35 investigation
        if (barterSign.getMode().equals(ShopMode.BOARD)) {
            org.bukkit.Bukkit.getLogger().fine(String.format(
                "[BarterShops-DEBUG] updateSign BOARD: isCustomerView=%b, ownerPreviewMode=%b, configMode=%s",
                isCustomerView, barterSign.isOwnerPreviewMode(), barterSign.getType()
            ));
        }

        ISignModeRenderer renderer = RENDERERS.get(barterSign.getMode());
        if (renderer != null) {
            renderer.render(frontSide, barterSign, isCustomerView);
        }
        sign.update();
    }

    /**
     * Backward-compatible overload: shows customer view when owner is in preview mode,
     * otherwise shows owner view.
     */
    public static void updateSign(Sign sign, BarterSign barterSign) {
        boolean shouldShowCustomerView = barterSign.isOwnerPreviewMode();
        org.bukkit.Bukkit.getLogger().fine(String.format(
            "[BarterShops-DEBUG] updateSign overload: isOwnerPreviewMode=%b -> passing isCustomerView=%b",
            barterSign.isOwnerPreviewMode(), shouldShowCustomerView
        ));
        updateSign(sign, barterSign, shouldShowCustomerView);
    }

    public static void displayTemporaryMessage(Sign sign, String line1, String line2) {
        SignSide frontSide = sign.getSide(Side.FRONT);
        frontSide.setLine(0, "\u00A72[Barter Shop]");
        frontSide.setLine(1, line1);
        frontSide.setLine(2, line2 != null ? line2 : "");
        frontSide.setLine(3, "");
        sign.update();
    }

    public static void displayDeleteConfirmation(Sign sign) {
        SignSide frontSide = sign.getSide(Side.FRONT);
        frontSide.setLine(0, "§c[CONFIRM?]");
        frontSide.setLine(1, "§cL-Click AGAIN");
        frontSide.setLine(2, "§cto confirm");
        frontSide.setLine(3, "§e(5s timeout)");
        sign.update();
    }

    /**
     * Formats an ItemStack display name for use on a sign.
     * Delegates to {@link SignRenderUtil}; kept here for backward compatibility
     * with callers that imported this class (e.g. {@code SignInteraction}).
     */
    static String formatItemName(ItemStack item) {
        return SignRenderUtil.formatItemName(item);
    }
}
