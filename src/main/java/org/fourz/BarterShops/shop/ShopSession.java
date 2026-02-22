package org.fourz.BarterShops.shop;

import org.bukkit.entity.Player;
import org.bukkit.block.Sign;

/**
 * Represents a player's shop interaction session.
 * This class maintains the state and context of a player's current shop-related activities,
 * tracking various aspects such as the mode they're in, which sign they're interacting with,
 * and other relevant temporary data.
 */
public class ShopSession {

    private final Player player;
    private ShopMode currentMode;
    private Sign activeSign;
    private int stackSize;
    private String itemType;

    /**
     * Creates a new shop session for the specified player.
     * Initially, the session has no mode set and no active interactions.
     *
     * @param player The player who owns this session
     */
    public ShopSession(Player player) {
        this.player = player;
        this.currentMode = null;
    }

    /**
     * @return The player associated with this session
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * @return The current shop interaction mode
     */
    public ShopMode getCurrentMode() {
        return currentMode;
    }

    /**
     * Sets the current shop interaction mode.
     * This method can be extended to include mode-specific initialization logic.
     *
     * @param newMode The new mode to set
     */
    public void setCurrentMode(ShopMode newMode) {
        this.currentMode = newMode;
    }

    /**
     * @return The sign currently being interacted with
     */
    public Sign getActiveSign() {
        return activeSign;
    }

    /**
     * Sets the sign that the player is currently interacting with.
     * This is typically used during shop setup or modification.
     *
     * @param activeSign The sign to set as active
     */
    public void setActiveSign(Sign activeSign) {
        this.activeSign = activeSign;
    }

    /**
     * @return The current stack size being configured
     */
    public int getStackSize() {
        return stackSize;
    }

    /**
     * Sets the stack size for shop configuration.
     * Used primarily during SETUP_STACK mode to define item quantities.
     *
     * @param stackSize The stack size to set
     */
    public void setStackSize(int stackSize) {
        this.stackSize = stackSize;
    }

    /**
     * @return The current item type being configured
     */
    public String getItemType() {
        return itemType;
    }

    /**
     * Sets the item type for shop configuration.
     * Used during TYPE mode to specify what items the shop will handle.
     *
     * @param itemType The item type to set
     */
    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    /**
     * Performs cleanup operations for this session.
     * Called when the session is being terminated or reset.
     * This method can be expanded to include additional cleanup logic
     * such as saving state, clearing caches, or notifying other systems.
     */
    public void cleanup() {
        this.currentMode = null;
        this.activeSign = null;
        this.itemType = null;
        this.stackSize = 0;
    }
}
