package org.fourz.BarterShops.container;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.event.HandlerList;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.container.listener.InventoryValidationListener;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.BarterShops.shop.ShopMode;

public class ContainerManager implements Listener {
    private final BarterShops plugin;
    private final InventoryValidationListener validationListener;

    public ContainerManager(BarterShops plugin) {
        this.plugin = plugin;
        this.validationListener = new InventoryValidationListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getPluginManager().registerEvents(validationListener, plugin);
    }

    /**
     * Gets the validation listener for registering shop containers.
     */
    public InventoryValidationListener getValidationListener() {
        return validationListener;
    }

    @EventHandler
    public void onContainerOpen(InventoryOpenEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(event.getPlayer() instanceof Player)) return;
        
        if (holder instanceof Container || holder instanceof DoubleChest) {
            if (isShopContainer(holder)) {
                handleShopContainerAccess((Player) event.getPlayer(), holder);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onContainerBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof Container)) {
            return;
        }

        Container container = (Container) block.getState();
        if (!isShopContainer(container)) {
            return;
        }

        Player player = event.getPlayer();

        // Find the associated shop sign
        BarterSign barterSign = plugin.getSignManager().findSignByContainerLocation(block.getLocation());
        if (barterSign == null) {
            // Container is marked as shop container but no sign found
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "✗ This shop container is corrupted. Contact an admin.");
            return;
        }

        // Only owner or admin can break
        if (!barterSign.getOwner().equals(player.getUniqueId())
                && !player.hasPermission("bartershops.admin")) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "✗ You cannot break this shop chest.");
            player.sendMessage(ChatColor.GRAY + "Only the shop owner can remove it.");
            return;
        }

        // Must be in DELETE mode to break (or admin with override)
        if (barterSign.getMode() != ShopMode.DELETE
                && !player.hasPermission("bartershops.admin")) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.YELLOW + "! Right-click the sign to enter DELETE mode.");
            player.sendMessage(ChatColor.GRAY + "Then you can remove the shop.");
            return;
        }

        // Authorized break - proceed with removal via sign deletion
        // The sign break event will handle cleanup
        event.setCancelled(false);
    }

    private boolean isShopContainer(InventoryHolder holder) {
        if (holder instanceof Container) {
            BarterContainer barterContainer = new BarterContainer((Container) holder, plugin);
            return barterContainer.isBarterContainer();
        }
        return false;
    }

    private boolean isShopContainer(Object containerState) {
        if (containerState instanceof Container) {
            BarterContainer barterContainer = new BarterContainer((Container) containerState, plugin);
            return barterContainer.isBarterContainer();
        }
        return false;
    }

    private void handleShopContainerAccess(Player player, InventoryHolder container) {
        // Handle custom shop interface opening
    }

    public void registerShopContainer(Block container) {
        if (container.getState() instanceof Container) {
            BarterContainer barterContainer = new BarterContainer((Container) container.getState(), plugin);
            barterContainer.setBarterContainerId(java.util.UUID.randomUUID().toString());
        }
    }

    public void unregisterShopContainer(Block container) {
        if (container.getState() instanceof Container) {
            Container state = (Container) container.getState();
            state.getPersistentDataContainer().remove(new NamespacedKey(plugin, "barter_container_id"));
            state.getPersistentDataContainer().remove(new NamespacedKey(plugin, "payment_container_id"));
            state.update();
        }
    }
    
    public void cleanup() {
        HandlerList.unregisterAll(this);
        validationListener.cleanup();
        HandlerList.unregisterAll(validationListener);
    }
}
