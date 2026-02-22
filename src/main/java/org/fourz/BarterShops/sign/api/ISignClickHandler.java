package org.fourz.BarterShops.sign.api;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Callback interface for sign click interactions.
 *
 * Implementors provide the application-specific behaviour when a player
 * left-clicks or right-clicks a sign that implements {@link ISignData}.
 *
 * {@code SignManager} (or any generic sign router) only depends on this
 * interface — it has no knowledge of shops, lore entries, or dynmap pins.
 *
 * BarterShops: implemented by {@code SignInteraction}.
 * Future uses: LoreSignInteraction, CitySignInteraction, DynmapPinInteraction.
 */
public interface ISignClickHandler {

    /**
     * Called when a player left-clicks the sign.
     *
     * @param player  The interacting player.
     * @param data    The sign data model.
     * @param event   The originating Bukkit event (may be cancelled by callee).
     */
    void onLeftClick(Player player, ISignData data, PlayerInteractEvent event);

    /**
     * Called when a player right-clicks the sign.
     *
     * @param player The interacting player.
     * @param data   The sign data model.
     */
    void onRightClick(Player player, ISignData data);
}
