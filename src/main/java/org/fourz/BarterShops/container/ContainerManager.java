package org.fourz.BarterShops.container;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.event.HandlerList;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.container.listener.InventoryValidationListener;
import org.fourz.BarterShops.sign.BarterSign;

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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onContainerBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof Container)) {
            return;
        }

        // Find if this container belongs to a shop
        BarterSign barterSign = plugin.getSignManager().findSignByContainerLocation(block.getLocation());

        // If no sign exists, allow normal vanilla chest break
        if (barterSign == null) {
            return;
        }

        // Sign exists - chest is protected
        // Chest can ONLY be broken after sign is removed via DELETE mode
        Player player = event.getPlayer();
        event.setCancelled(true);

        if (barterSign.getOwner().equals(player.getUniqueId())
                || player.hasPermission("bartershops.admin")) {
            player.sendMessage(ChatColor.YELLOW + "! Remove the shop sign first to break this chest.");
            player.sendMessage(ChatColor.GRAY + "Right-click sign → cycle to DELETE mode → break sign.");
        } else {
            player.sendMessage(ChatColor.RED + "✗ This chest belongs to a shop.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onContainerExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> {
            if (!(block.getState() instanceof Container)) return false;
            return plugin.getSignManager().findSignByContainerLocation(block.getLocation()) != null;
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onContainerEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            if (!(block.getState() instanceof Container)) return false;
            return plugin.getSignManager().findSignByContainerLocation(block.getLocation()) != null;
        });
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
