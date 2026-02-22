package org.fourz.BarterShops.trade;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.BarterShops.BarterShops;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * GUI for trade confirmation dialogs.
 * Shows the items being exchanged and confirm/cancel buttons.
 */
public class TradeConfirmationGUI implements Listener {

    private final BarterShops plugin;
    private final LogManager logger;

    /** Active GUIs by player UUID */
    private final Map<UUID, ConfirmationSession> activeGuis = new HashMap<>();

    private static final int GUI_SIZE = 27; // 3 rows
    private static final int SLOT_OFFERED_ITEM = 11;
    private static final int SLOT_ARROW = 13;
    private static final int SLOT_REQUESTED_ITEM = 15;
    private static final int SLOT_CONFIRM = 21;
    private static final int SLOT_CANCEL = 23;

    public TradeConfirmationGUI(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "TradeConfirmationGUI");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Opens a trade confirmation GUI for a player.
     *
     * @param player The player to show the GUI
     * @param session The trade session to confirm
     * @param onConfirm Callback when trade is confirmed
     * @param onCancel Callback when trade is cancelled
     */
    public void openConfirmation(Player player, TradeSession session,
            Consumer<TradeSession> onConfirm, Consumer<TradeSession> onCancel) {

        // Close any existing GUI
        if (activeGuis.containsKey(player.getUniqueId())) {
            player.closeInventory();
        }

        // Build the GUI
        String title = ChatColor.DARK_GREEN + "Confirm Trade";
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, title);

        // Fill with glass panes
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < GUI_SIZE; i++) {
            gui.setItem(i, filler);
        }

        // What buyer receives (from shop)
        ItemStack offered = session.getOfferedItem();
        if (offered != null) {
            ItemStack display = offered.clone();
            display.setAmount(session.getOfferedQuantity());
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                meta.setLore(Arrays.asList(
                        ChatColor.GREEN + "You will receive:",
                        ChatColor.WHITE + "" + session.getOfferedQuantity() + "x " +
                                getItemName(offered)
                ));
                display.setItemMeta(meta);
            }
            gui.setItem(SLOT_OFFERED_ITEM, display);
        }

        // Arrow indicator
        gui.setItem(SLOT_ARROW, createItem(Material.ARROW, ChatColor.YELLOW + "Trade"));

        // What buyer pays (to shop)
        ItemStack requested = session.getRequestedItem();
        if (requested != null && session.getRequestedQuantity() > 0) {
            ItemStack display = requested.clone();
            display.setAmount(session.getRequestedQuantity());
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                meta.setLore(Arrays.asList(
                        ChatColor.RED + "You will pay:",
                        ChatColor.WHITE + "" + session.getRequestedQuantity() + "x " +
                                getItemName(requested)
                ));
                display.setItemMeta(meta);
            }
            gui.setItem(SLOT_REQUESTED_ITEM, display);
        } else {
            gui.setItem(SLOT_REQUESTED_ITEM, createItem(Material.BARRIER,
                    ChatColor.GREEN + "Free!"));
        }

        // Confirm button
        gui.setItem(SLOT_CONFIRM, createItem(Material.LIME_WOOL,
                ChatColor.GREEN + "" + ChatColor.BOLD + "CONFIRM TRADE",
                ChatColor.GRAY + "Click to complete the trade"));

        // Cancel button
        gui.setItem(SLOT_CANCEL, createItem(Material.RED_WOOL,
                ChatColor.RED + "" + ChatColor.BOLD + "CANCEL",
                ChatColor.GRAY + "Click to cancel the trade"));

        // Store session
        ConfirmationSession confSession = new ConfirmationSession(
                session, onConfirm, onCancel);
        activeGuis.put(player.getUniqueId(), confSession);

        // Open GUI
        player.openInventory(gui);
        logger.debug("Opened trade confirmation GUI for " + player.getName());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ConfirmationSession confSession = activeGuis.get(player.getUniqueId());
        if (confSession == null) return;

        // Check if clicking in our GUI
        String title = event.getView().getTitle();
        if (!title.contains("Confirm Trade")) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();

        if (slot == SLOT_CONFIRM) {
            // Confirmed
            logger.debug("Trade confirmed by " + player.getName());
            activeGuis.remove(player.getUniqueId());
            player.closeInventory();

            if (confSession.onConfirm != null) {
                confSession.onConfirm.accept(confSession.session);
            }

        } else if (slot == SLOT_CANCEL) {
            // Cancelled
            logger.debug("Trade cancelled by " + player.getName());
            activeGuis.remove(player.getUniqueId());
            player.closeInventory();

            if (confSession.onCancel != null) {
                confSession.onCancel.accept(confSession.session);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        ConfirmationSession confSession = activeGuis.remove(player.getUniqueId());
        if (confSession != null) {
            // Treat close as cancel
            logger.debug("Trade GUI closed by " + player.getName() + " - treating as cancel");
            if (confSession.onCancel != null) {
                // Schedule callback to avoid concurrent modification
                Bukkit.getScheduler().runTask(plugin, () ->
                        confSession.onCancel.accept(confSession.session));
            }
        }
    }

    /**
     * Creates a display item with name and lore.
     */
    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Gets a display name for an item.
     */
    private String getItemName(ItemStack item) {
        if (item == null) return "Unknown";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name().toLowerCase().replace('_', ' ');
    }

    /**
     * Checks if a player has an active confirmation GUI.
     */
    public boolean hasActiveGui(UUID playerUuid) {
        return activeGuis.containsKey(playerUuid);
    }

    /**
     * Closes any active GUI for a player.
     */
    public void closeGui(UUID playerUuid) {
        activeGuis.remove(playerUuid);
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            player.closeInventory();
        }
    }

    /**
     * Shuts down the GUI manager.
     */
    public void shutdown() {
        HandlerList.unregisterAll(this);
        activeGuis.clear();
    }

    /**
     * Internal session tracking.
     */
    private record ConfirmationSession(
            TradeSession session,
            Consumer<TradeSession> onConfirm,
            Consumer<TradeSession> onCancel
    ) {}
}
