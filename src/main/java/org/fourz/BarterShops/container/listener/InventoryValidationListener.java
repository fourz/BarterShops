package org.fourz.BarterShops.container.listener;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.container.ShopContainer;
import org.fourz.BarterShops.container.validation.ValidationResult;
import org.fourz.BarterShops.notification.NotificationManager;
import org.fourz.BarterShops.notification.NotificationType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Real-time inventory validation listener for shop containers.
 * Prevents invalid items from being placed in shop containers across ALL modes.
 * Fixes bug-36: Type locking validation broken.
 *
 * Listens for:
 * - InventoryClickEvent: Player clicks in inventory
 * - InventoryMoveItemEvent: Hoppers, droppers, etc move items
 * - InventoryDragEvent: Players drag items across slots
 */
public class InventoryValidationListener implements Listener {
    private final Map<UUID, ShopContainer> shopContainers = new HashMap<>();
    private final long creationTime = System.currentTimeMillis();
    private final LogManager logger;

    public InventoryValidationListener() {
        try {
            this.logger = LogManager.getInstance(org.bukkit.Bukkit.getPluginManager().getPlugin("BarterShops"), "InventoryValidation");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize LogManager", e);
        }
    }

    /**
     * Registers a shop container for real-time validation.
     *
     * @param shopContainer The container to monitor
     */
    public void registerContainer(ShopContainer shopContainer) {
        shopContainers.put(shopContainer.getShopId(), shopContainer);
        logger.debug("Registered shop container: " + shopContainer.getShopId() +
                   " at " + shopContainer.getLocation() +
                   " with " + shopContainer.getValidationRules().size() + " validation rules");
    }

    /**
     * Unregisters a shop container from validation.
     *
     * @param shopId The ID of the shop to stop monitoring
     */
    public void unregisterContainer(UUID shopId) {
        shopContainers.remove(shopId);
    }

    /**
     * Gets the number of monitored containers.
     */
    public int getMonitoredContainerCount() {
        return shopContainers.size();
    }

    /**
     * Handles player clicking in shop inventory.
     * Prevents placing invalid items.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        ShopContainer shopContainer = getShopContainerFromEvent(event);
        if (shopContainer == null) {
            logger.debug("InventoryClickEvent: No shop container found");
            return;
        }

        Player player = (event.getWhoClicked() instanceof Player) ? (Player) event.getWhoClicked() : null;
        logger.debug("InventoryClickEvent: Shop container found at " + shopContainer.getLocation());

        // Only check items being placed into the container
        if (event.getRawSlot() >= event.getInventory().getSize()) {
            // Click is in player inventory, not shop container
            logger.debug("InventoryClickEvent: Click is in player inventory (slot " + event.getRawSlot() + "), not shop container");
            return;
        }

        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType() == Material.AIR) {
            logger.debug("InventoryClickEvent: Cursor is empty");
            return; // Empty cursor
        }

        logger.info("InventoryClickEvent: Player " + (player != null ? player.getName() : "?") +
                  " clicking with " + cursor.getType() + " (qty=" + cursor.getAmount() + ") at slot " + event.getRawSlot());

        // Validate the item being placed
        ValidationResult result = shopContainer.validateItem(cursor);
        if (!result.isValid()) {
            logger.warning("InventoryClickEvent: Validation FAILED - " + result.reason() +
                          ". Item: " + cursor.getType() + ", Shop rules: " + shopContainer.getValidationRules().size());
            event.setCancelled(true);

            // Eject invalid item from container
            ItemStack itemToEject = cursor.clone();
            Location containerLoc = event.getInventory().getLocation();
            if (containerLoc != null && containerLoc.getWorld() != null) {
                containerLoc.getWorld().dropItem(containerLoc, itemToEject);
                logger.debug("InventoryClickEvent: Ejected invalid item at " + containerLoc);
            }

            // Send error notification to player
            if (player != null) {
                sendValidationError(player, result.reason());
            }
        } else {
            logger.debug("InventoryClickEvent: Validation PASSED for " + cursor.getType());
        }
    }

    /**
     * Handles hoppers and droppers moving items.
     * Prevents automated systems from bypassing type locking.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        // Check if destination is a shop container
        ShopContainer shopContainer = getShopContainerFromInventory(event.getDestination());
        if (shopContainer == null) {
            logger.debug("InventoryMoveItemEvent: Destination is not a shop container");
            return;
        }

        ItemStack item = event.getItem();
        logger.info("InventoryMoveItemEvent: Automated item move detected: " + item.getType() +
                   " (qty=" + item.getAmount() + ") to shop at " + shopContainer.getLocation());

        ValidationResult result = shopContainer.validateItem(item);
        if (!result.isValid()) {
            logger.warning("InventoryMoveItemEvent: Validation FAILED - " + result.reason() +
                          ". Item: " + item.getType());
            event.setCancelled(true);

            // Eject invalid item from container
            Location containerLoc = event.getDestination().getLocation();
            if (containerLoc != null && containerLoc.getWorld() != null) {
                containerLoc.getWorld().dropItem(containerLoc, item.clone());
                logger.debug("InventoryMoveItemEvent: Ejected invalid item at " + containerLoc);
            }
        } else {
            logger.debug("InventoryMoveItemEvent: Validation PASSED for " + item.getType());
        }
    }

    /**
     * Handles players dragging items.
     * Validates each item being dragged into the container.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        ShopContainer shopContainer = getShopContainerFromEvent(event);
        if (shopContainer == null) {
            logger.debug("InventoryDragEvent: No shop container found");
            return;
        }

        Player player = (event.getWhoClicked() instanceof Player) ? (Player) event.getWhoClicked() : null;
        logger.debug("InventoryDragEvent: Shop container found at " + shopContainer.getLocation());

        ItemStack cursor = event.getOldCursor();
        if (cursor == null || cursor.getType() == Material.AIR) {
            logger.debug("InventoryDragEvent: Cursor is empty");
            return;
        }

        logger.info("InventoryDragEvent: Player " + (player != null ? player.getName() : "?") +
                  " dragging " + cursor.getType() + " (qty=" + cursor.getAmount() + ") into " + event.getRawSlots().size() + " slots");

        // Check if any drag slots are in the shop container
        for (int slot : event.getRawSlots()) {
            if (slot < event.getInventory().getSize()) {
                // Slot is in shop container, validate
                logger.debug("InventoryDragEvent: Checking slot " + slot + " in container");
                ValidationResult result = shopContainer.validateItem(cursor);
                if (!result.isValid()) {
                    logger.warning("InventoryDragEvent: Validation FAILED - " + result.reason() +
                                  ". Item: " + cursor.getType() + ", Slot: " + slot);
                    event.setCancelled(true);

                    // Eject invalid item from container
                    ItemStack itemToEject = cursor.clone();
                    Location containerLoc = event.getInventory().getLocation();
                    if (containerLoc != null && containerLoc.getWorld() != null) {
                        containerLoc.getWorld().dropItem(containerLoc, itemToEject);
                        logger.debug("InventoryDragEvent: Ejected invalid item at " + containerLoc);
                    }

                    // Send error notification to player
                    if (player != null) {
                        sendValidationError(player, result.reason());
                    }

                    return;
                } else {
                    logger.debug("InventoryDragEvent: Validation PASSED for slot " + slot);
                }
            }
        }
    }

    /**
     * Sends validation error notification to player.
     * Uses NotificationManager if available, falls back to chat message.
     */
    private void sendValidationError(Player player, String reason) {
        try {
            // Try to get the BarterShops plugin instance
            Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("BarterShops");
            if (plugin instanceof BarterShops barterShops) {
                NotificationManager notificationManager = barterShops.getNotificationManager();
                if (notificationManager != null) {
                    notificationManager.sendNotification(
                        player.getUniqueId(),
                        NotificationType.SYSTEM,
                        ChatColor.RED + "✗ " + reason
                    );
                    return;
                }
            }
        } catch (Exception ignored) {
            // Fall through to chat message
        }

        // Fallback: send direct chat message
        player.sendMessage(ChatColor.RED + "✗ " + reason);
    }

    /**
     * Gets the shop container for an inventory event, or null if not a registered shop.
     */
    private ShopContainer getShopContainerFromEvent(InventoryEvent event) {
        return getShopContainerFromInventory(event.getInventory());
    }

    /**
     * Gets the shop container for an inventory, or null if not registered.
     * CRITICAL FIX: Compare by Location instead of object reference.
     * Bukkit creates new Container object instances for each event, so comparing == fails.
     */
    private ShopContainer getShopContainerFromInventory(org.bukkit.inventory.Inventory inventory) {
        if (inventory.getHolder() instanceof Container container) {
            Location containerLoc = container.getLocation();
            logger.debug("getShopContainerFromInventory: Container holder found at " + containerLoc +
                        ", searching " + shopContainers.size() + " registered containers");

            // Try to find this container by LOCATION (not object reference)
            for (ShopContainer shopContainer : shopContainers.values()) {
                if (shopContainer.getLocation().equals(containerLoc)) {
                    logger.debug("getShopContainerFromInventory: Found matching shop container by location!");
                    return shopContainer;
                }
            }
            logger.debug("getShopContainerFromInventory: No matching shop container found in registered map");
        } else {
            logger.debug("getShopContainerFromInventory: Inventory holder is not a Container (type: " +
                        (inventory.getHolder() != null ? inventory.getHolder().getClass().getName() : "null") + ")");
        }
        return null;
    }

    /**
     * Gets listener uptime for debugging.
     */
    public long getUptimeMillis() {
        return System.currentTimeMillis() - creationTime;
    }

    /**
     * Cleanup on plugin disable.
     */
    public void cleanup() {
        shopContainers.clear();
    }
}
