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

    /**
     * Registers a shop container for real-time validation.
     *
     * @param shopContainer The container to monitor
     */
    public void registerContainer(ShopContainer shopContainer) {
        shopContainers.put(shopContainer.getShopId(), shopContainer);
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
            return;
        }

        // Only check items being placed into the container
        if (event.getRawSlot() >= event.getInventory().getSize()) {
            // Click is in player inventory, not shop container
            return;
        }

        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType() == Material.AIR) {
            return; // Empty cursor
        }

        // Validate the item being placed
        ValidationResult result = shopContainer.validateItem(cursor);
        if (!result.isValid()) {
            event.setCancelled(true);

            // Eject invalid item from container
            ItemStack itemToEject = cursor.clone();
            Location containerLoc = event.getInventory().getLocation();
            if (containerLoc != null && containerLoc.getWorld() != null) {
                containerLoc.getWorld().dropItem(containerLoc, itemToEject);
            }

            // Send error notification to player
            if (event.getWhoClicked() instanceof Player player) {
                sendValidationError(player, result.reason());
            }
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
            return;
        }

        ItemStack item = event.getItem();
        ValidationResult result = shopContainer.validateItem(item);
        if (!result.isValid()) {
            event.setCancelled(true);

            // Eject invalid item from container
            Location containerLoc = event.getDestination().getLocation();
            if (containerLoc != null && containerLoc.getWorld() != null) {
                containerLoc.getWorld().dropItem(containerLoc, item.clone());
            }
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
            return;
        }

        ItemStack cursor = event.getOldCursor();
        if (cursor == null || cursor.getType() == Material.AIR) {
            return;
        }

        // Check if any drag slots are in the shop container
        for (int slot : event.getRawSlots()) {
            if (slot < event.getInventory().getSize()) {
                // Slot is in shop container, validate
                ValidationResult result = shopContainer.validateItem(cursor);
                if (!result.isValid()) {
                    event.setCancelled(true);

                    // Eject invalid item from container
                    ItemStack itemToEject = cursor.clone();
                    Location containerLoc = event.getInventory().getLocation();
                    if (containerLoc != null && containerLoc.getWorld() != null) {
                        containerLoc.getWorld().dropItem(containerLoc, itemToEject);
                    }

                    // Send error notification to player
                    if (event.getWhoClicked() instanceof Player player) {
                        sendValidationError(player, result.reason());
                    }

                    return;
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
     */
    private ShopContainer getShopContainerFromInventory(org.bukkit.inventory.Inventory inventory) {
        if (inventory.getHolder() instanceof Container container) {
            // Try to find this container in our registered map
            for (ShopContainer shopContainer : shopContainers.values()) {
                if (shopContainer.getContainer() == container) {
                    return shopContainer;
                }
            }
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
