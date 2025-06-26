package org.fourz.BarterShops.container;

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

public class ContainerManager implements Listener {
    private final BarterShops plugin;

    public ContainerManager(BarterShops plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
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
        if (block.getState() instanceof Container) {
            if (isShopContainer(block.getState())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("You cannot break a shop container!");
            }
        }
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
    }
}
