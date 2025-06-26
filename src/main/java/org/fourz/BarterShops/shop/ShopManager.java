package org.fourz.BarterShops.shop;

import org.bukkit.entity.Player;
import org.fourz.BarterShops.Main;
import org.fourz.BarterShops.util.Debug;

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
    private final Main plugin;
    private final Debug debug;
    private final Map<UUID, ShopSession> activeSessions;

    public ShopManager(Main plugin) {
        this.plugin = plugin;
        this.debug = new Debug(plugin, CLASS_NAME, plugin.getDebugger().getLogLevel()) {};
        this.activeSessions = new HashMap<>();
        debug.debug("ShopManager initialized");
    }

    /**
     * Gets or creates a shop session for the specified player.
     * 
     * @param player The player to get/create a session for
     * @return The player's shop session
     */
    public ShopSession getSession(Player player) {
        debug.debug("Getting session for player: " + player.getName());
        return activeSessions.computeIfAbsent(player.getUniqueId(), uuid -> {
            ShopSession session = new ShopSession(player);
            debug.debug("Created new session for player: " + player.getName());
            return session;
        });
    }

    /**
     * Removes and cleans up a player's shop session.
     * 
     * @param player The player whose session should be removed
     */
    public void removeSession(Player player) {
        debug.debug("Removing session for player: " + player.getName());
        ShopSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            session.cleanup();
            debug.debug("Cleaned up session for player: " + player.getName());
        }
    }

    /**
     * Updates a player's shop session mode.
     * 
     * @param player The player whose session mode should be updated
     * @param newMode The new mode to set
     */
    public void setSessionMode(Player player, ShopMode newMode) {
        debug.debug("Setting mode " + newMode + " for player: " + player.getName());
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
        debug.debug("Handling sign interaction for player: " + player.getName() + " with mode: " + newMode);
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
            case SETUP_SELL -> "Right-click with an item to set the sell price.";
            case SETUP_STACK -> "Enter the stack size in chat.";
            case TYPE -> "Right-click with an item to set the shop type.";
            case DELETE -> "Right-click the sign again to confirm deletion.";
            case BOARD_SETUP -> "Right-click a sign to add it to the board.";
            case BOARD_DISPLAY -> "Use /shop next and /shop prev to navigate.";
            case HELP -> "Use /shop help for command information.";
            default -> "Unknown mode.";
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
     * Cleans up all active sessions.
     */
    public void cleanup() {
        debug.debug("Cleaning up all sessions (" + activeSessions.size() + " active)");
        activeSessions.values().forEach(ShopSession::cleanup);
        activeSessions.clear();
        debug.info("ShopManager cleanup completed");
    }
}
