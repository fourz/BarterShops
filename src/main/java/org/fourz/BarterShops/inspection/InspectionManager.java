package org.fourz.BarterShops.inspection;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.fourz.BarterShops.BarterShops;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;

/**
 * Manages inspection mode state and creates inspection tools.
 * Tracks which players are in inspection mode and provides inspection tool creation.
 */
public class InspectionManager {

    private final BarterShops plugin;
    private final LogManager logger;
    private final NamespacedKey inspectionToolKey;
    private final Set<UUID> activeInspectors = new HashSet<>();

    public InspectionManager(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "InspectionManager");
        this.inspectionToolKey = new NamespacedKey(plugin, "inspection_tool");
        logger.info("InspectionManager initialized");
    }

    /**
     * Toggles inspection mode for a player.
     * Returns true if the player was already in inspection mode (being turned off).
     *
     * @param playerUuid Player UUID to toggle
     * @return true if mode is being turned OFF, false if being turned ON
     */
    public boolean toggleInspectionMode(UUID playerUuid) {
        if (activeInspectors.contains(playerUuid)) {
            activeInspectors.remove(playerUuid);
            return true; // Was in inspection mode, now off
        } else {
            activeInspectors.add(playerUuid);
            return false; // Wasn't in inspection mode, now on
        }
    }

    /**
     * Checks if a player is in inspection mode.
     *
     * @param playerId Player UUID to check
     * @return true if player is actively inspecting shops
     */
    public boolean isInspectionMode(UUID playerId) {
        return activeInspectors.contains(playerId);
    }

    /**
     * Creates a BLAZE_ROD inspection tool with PDC marker.
     *
     * @return ItemStack with inspection tool properties
     */
    public ItemStack createInspectionTool() {
        ItemStack tool = new ItemStack(Material.BLAZE_ROD, 1);
        ItemMeta meta = tool.getItemMeta();

        if (meta != null) {
            // Set display name and lore
            meta.setDisplayName("" + ChatColor.GOLD + ChatColor.BOLD + "Shop Inspector");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Right-click shop signs to inspect");
            lore.add(ChatColor.GRAY + "Use /shop inspect to toggle");
            lore.add(ChatColor.DARK_GRAY + "Admin Tool");
            meta.setLore(lore);

            // Add PDC marker
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(inspectionToolKey, PersistentDataType.BYTE, (byte) 1);

            tool.setItemMeta(meta);
        }

        return tool;
    }

    /**
     * Validates that an item is an inspection tool.
     * Checks for the PDC marker to verify authenticity.
     *
     * @param item ItemStack to validate
     * @return true if the item is a valid inspection tool
     */
    public boolean isInspectionTool(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(inspectionToolKey, PersistentDataType.BYTE);
    }

    /**
     * Clears a player from inspection mode (e.g., on logout).
     *
     * @param playerUuid Player UUID to clear
     */
    public void clearPlayer(UUID playerUuid) {
        activeInspectors.remove(playerUuid);
    }

    /**
     * Cleanup method for plugin shutdown.
     */
    public void cleanup() {
        activeInspectors.clear();
        logger.info("InspectionManager cleaned up");
    }
}
