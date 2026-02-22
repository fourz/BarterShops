package org.fourz.BarterShops.sign.api;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Generic contract for a world-attached sign with an owner.
 *
 * Implementations include BarterSign (shop signs) and may include
 * other sign types (city signs, lore markers, dynmap pins) that share
 * the same click-routing infrastructure without depending on shop logic.
 *
 * Intentionally minimal — only the fields needed by generic routing code.
 * Shop-specific data (itemOffering, priceItem, acceptedPayments) remains
 * on BarterSign and is not part of this interface.
 */
public interface ISignData {

    /** Stable identifier for this sign (e.g. shop UUID or lore entry ID). */
    String getId();

    /** UUID of the player who owns or manages this sign. */
    UUID getOwner();

    /** World location of the sign block. */
    Location getSignLocation();
}
