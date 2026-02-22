package org.fourz.BarterShops.shop;

import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;

/**
 * Manages shop sessions for players.
 * This class acts as the central point for tracking and managing player interactions
 * with shops, maintaining the state of each player's shop-related activities.
 */
public class ShopManager {
    private static final String CLASS_NAME = "ShopManager";
    private final BarterShops plugin;
    private final LogManager logger;
    private final Map<UUID, ShopSession> activeSessions;

    public ShopManager(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, CLASS_NAME);
        this.activeSessions = new HashMap<>();
        logger.debug("ShopManager initialized");
    }

    /**
     * Gets or creates a shop session for the specified player.
     * 
     * @param player The player to get/create a session for
     * @return The player's shop session
     */
    public ShopSession getSession(Player player) {
        logger.debug("Getting session for player: " + player.getName());
        return activeSessions.computeIfAbsent(player.getUniqueId(), uuid -> {
            ShopSession session = new ShopSession(player);
            logger.debug("Created new session for player: " + player.getName());
            return session;
        });
    }

    /**
     * Removes and cleans up a player's shop session.
     * 
     * @param player The player whose session should be removed
     */
    public void removeSession(Player player) {
        logger.debug("Removing session for player: " + player.getName());
        ShopSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            session.cleanup();
            logger.debug("Cleaned up session for player: " + player.getName());
        }
    }

    /**
     * Updates a player's shop session mode.
     * 
     * @param player The player whose session mode should be updated
     * @param newMode The new mode to set
     */
    public void setSessionMode(Player player, ShopMode newMode) {
        logger.debug("Setting mode " + newMode + " for player: " + player.getName());
        ShopSession session = getSession(player);
        session.setCurrentMode(newMode);
        sendModeInstructions(player, newMode);
    }

    /**
     * Handles shop interaction transitions.
     * 
     * @param player The player who triggered the interaction
     * @param newMode The mode to transition to
     */
    public void handleSignInteraction(Player player, ShopMode newMode) {
        logger.debug("Handling sign interaction for player: " + player.getName() + " with mode: " + newMode);
        ShopSession session = getSession(player);
        session.setCurrentMode(newMode);
        sendModeInstructions(player, newMode);
    }

    /**
     * Sends mode-specific instructions to the player.
     *
     * @param player The player to send instructions to
     * @param mode The mode to get instructions for
     */
    private void sendModeInstructions(Player player, ShopMode mode) {
        String instructions = switch (mode) {
            case SETUP -> "Left-click with item to set offering, right-click with payment item to set price.";
            case TYPE -> "Right-click to cycle through shop types (STACKABLE/UNSTACKABLE/BARTER).";
            case BOARD -> "Shop is active and ready for customers.";
            case DELETE -> "Right-click again to exit delete mode, or break sign to delete.";
            case HELP -> "Use /shop help for command information.";
        };
        player.sendMessage(instructions);
    }

    /**
     * Gets a read-only view of all active sessions.
     *
     * @return Unmodifiable map of all active sessions
     */
    public Map<UUID, ShopSession> getActiveSessions() {
        return Collections.unmodifiableMap(activeSessions);
    }

    /**
     * Invalidate owner's active session for a shop.
     * Prevents old owner from continuing configuration after ownership change.
     *
     * @param shop The shop that changed ownership
     */
    public void invalidateSessionsForShop(org.fourz.BarterShops.sign.BarterSign shop) {
        if (shop == null) {
            return;
        }

        UUID oldOwner = shop.getOwner();
        ShopSession session = activeSessions.get(oldOwner);

        if (session == null) {
            return; // No active session for old owner
        }

        // Remove old owner's session to force them out of configuration mode
        org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(oldOwner);
        if (player != null) {
            removeSession(player);
            logger.debug("Invalidated shop session for old owner: " + oldOwner);
        }
    }

    /**
     * Cleans up all active sessions.
     */
    public void cleanup() {
        logger.debug("Cleaning up all sessions (" + activeSessions.size() + " active)");
        activeSessions.values().forEach(ShopSession::cleanup);
        activeSessions.clear();
        logger.info("ShopManager cleanup completed");
    }
}
