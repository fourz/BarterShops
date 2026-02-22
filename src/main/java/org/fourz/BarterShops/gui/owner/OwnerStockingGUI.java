package org.fourz.BarterShops.gui.owner;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.fourz.BarterShops.sign.BarterSign;

import java.util.*;

/**
 * Owner inventory stocking GUI for managing shop items directly.
 * Allows owners to add/remove items from shop containers without console.
 * Phase 4 feature: Will integrate with customer preview button in future.
 */
public class OwnerStockingGUI implements Listener {
    private final Map<UUID, StockingSession> activeSessions = new HashMap<>();

    /**
     * Session data for an owner stocking inventory.
     */
    private static class StockingSession {
        final UUID playerId;
        final UUID shopId;
        final Location containerLocation;
        final long createdAt;

        StockingSession(UUID playerId, UUID shopId, Location containerLocation) {
            this.playerId = playerId;
            this.shopId = shopId;
            this.containerLocation = containerLocation;
            this.createdAt = System.currentTimeMillis();
        }

        long getAgeMillis() {
            return System.currentTimeMillis() - createdAt;
        }
    }

    /**
     * Opens the stocking GUI for an owner.
     *
     * @param player The player opening the GUI
     * @param barterSign The barter sign/shop to stock
     * @return true if GUI opened successfully, false if validation failed
     */
    public boolean openGui(Player player, BarterSign barterSign) {
        // Validation would go here in Phase 4
        // - Check player is shop owner
        // - Check container still exists
        // - Check permissions
        return false; // Placeholder for Phase 4
    }

    /**
     * Closes an active stocking session.
     *
     * @param playerId The player to close session for
     */
    public void closeSession(UUID playerId) {
        activeSessions.remove(playerId);
    }

    /**
     * Handles inventory clicks in stocking GUI.
     * This will validate items and prevent type violations in Phase 4.
     */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // Phase 4: Implement item validation and transfer logic
        // For now, this is a placeholder
    }

    /**
     * Handles inventory close to clean up sessions.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            StockingSession session = activeSessions.get(player.getUniqueId());
            if (session != null) {
                closeSession(player.getUniqueId());
            }
        }
    }

    /**
     * Gets the number of active stocking sessions.
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Cleanup on plugin disable.
     */
    public void cleanup() {
        activeSessions.clear();
    }

    /**
     * Gets a debugging string with session count.
     */
    @Override
    public String toString() {
        return String.format("OwnerStockingGUI[sessions=%d]", activeSessions.size());
    }
}
