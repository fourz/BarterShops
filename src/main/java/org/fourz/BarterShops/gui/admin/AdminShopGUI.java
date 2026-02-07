package org.fourz.BarterShops.gui.admin;

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
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;

/**
 * Admin GUI for browsing and managing all shops in the system.
 * Provides shop list with pagination, quick actions, and detailed views.
 *
 * Permission: bartershops.admin.gui
 */
public class AdminShopGUI implements Listener {

    private final BarterShops plugin;
    private final LogManager logger;

    /** Active GUI sessions by player UUID */
    private final Map<UUID, BrowserSession> activeSessions = new HashMap<>();

    private static final int GUI_SIZE = 54; // 6 rows
    private static final int ITEMS_PER_PAGE = 45; // 5 rows for shops

    // Navigation slots (bottom row)
    private static final int SLOT_PREV_PAGE = 45;
    private static final int SLOT_STATS = 49;
    private static final int SLOT_NEXT_PAGE = 53;
    private static final int SLOT_CLOSE = 48;

    public AdminShopGUI(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "AdminShopGUI");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Opens the admin shop browser for a player.
     *
     * @param player The admin player viewing shops
     */
    public void openShopBrowser(Player player) {
        openShopBrowser(player, 0);
    }

    /**
     * Opens the admin shop browser at a specific page.
     *
     * @param player The admin player viewing shops
     * @param page The page number (0-indexed)
     */
    public void openShopBrowser(Player player, int page) {
        // Close any existing GUI
        if (activeSessions.containsKey(player.getUniqueId())) {
            player.closeInventory();
        }

        // Load shops asynchronously
        getAllShops().thenAccept(allShops -> {
            // Sync back to main thread for GUI operations
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                buildAndOpenGui(player, allShops, page);
            });
        }).exceptionally(ex -> {
            // Handle error on main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.RED + "Failed to load shops: " + ex.getMessage());
                logger.error("Failed to load shops for admin GUI: " + ex.getMessage());
            });
            return null;
        });
    }

    /**
     * Builds and opens the GUI with loaded shop data.
     * Must be called on main thread.
     */
    private void buildAndOpenGui(Player player, List<ShopDataDTO> allShops, int page) {
        // Calculate pagination
        int totalPages = (int) Math.ceil((double) allShops.size() / ITEMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;

        // Clamp page number
        page = Math.max(0, Math.min(page, totalPages - 1));

        // Build the GUI
        String title = ChatColor.DARK_PURPLE + "Shop Manager " +
                       ChatColor.GRAY + "(Page " + (page + 1) + "/" + totalPages + ")";
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, title);

        // Fill bottom navigation bar
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) {
            gui.setItem(i, filler);
        }

        // Add shops for current page
        int startIdx = page * ITEMS_PER_PAGE;
        int endIdx = Math.min(startIdx + ITEMS_PER_PAGE, allShops.size());

        for (int i = startIdx; i < endIdx; i++) {
            ShopDataDTO shop = allShops.get(i);
            ItemStack shopItem = createShopIcon(shop);
            gui.setItem(i - startIdx, shopItem);
        }

        // Navigation buttons
        if (page > 0) {
            gui.setItem(SLOT_PREV_PAGE, createItem(Material.ARROW,
                    ChatColor.YELLOW + "Previous Page",
                    ChatColor.GRAY + "Page " + page + "/" + totalPages));
        }

        if (page < totalPages - 1) {
            gui.setItem(SLOT_NEXT_PAGE, createItem(Material.ARROW,
                    ChatColor.YELLOW + "Next Page",
                    ChatColor.GRAY + "Page " + (page + 2) + "/" + totalPages));
        }

        // Stats button
        gui.setItem(SLOT_STATS, createItem(Material.BOOK,
                ChatColor.GOLD + "Statistics",
                ChatColor.GRAY + "View server-wide stats"));

        // Close button
        gui.setItem(SLOT_CLOSE, createItem(Material.BARRIER,
                ChatColor.RED + "Close"));

        // Store session
        BrowserSession session = new BrowserSession(allShops, page);
        activeSessions.put(player.getUniqueId(), session);

        // Open GUI
        player.openInventory(gui);
        logger.debug("Opened admin shop browser for " + player.getName() + " (page " + page + ")");
    }

    /**
     * Creates an icon representing a shop.
     */
    private ItemStack createShopIcon(ShopDataDTO shop) {
        Material material = shop.isActive() ? Material.EMERALD : Material.REDSTONE;

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "ID: " + ChatColor.WHITE + shop.shopId());
        lore.add(ChatColor.GRAY + "Owner: " + ChatColor.WHITE +
                Bukkit.getOfflinePlayer(shop.ownerUuid()).getName());
        lore.add(ChatColor.GRAY + "Type: " + ChatColor.WHITE + shop.shopType());
        lore.add(ChatColor.GRAY + "Location: " + ChatColor.WHITE +
                shop.locationWorld() + " (" +
                (int)shop.locationX() + ", " +
                (int)shop.locationY() + ", " +
                (int)shop.locationZ() + ")");
        lore.add(ChatColor.GRAY + "Status: " +
                (shop.isActive() ? ChatColor.GREEN + "Active" : ChatColor.RED + "Inactive"));
        lore.add("");
        lore.add(ChatColor.YELLOW + "Left-click to view details");
        lore.add(ChatColor.YELLOW + "Right-click to toggle status");
        lore.add(ChatColor.YELLOW + "Shift+Right-click to delete");

        String name = ChatColor.AQUA + (shop.shopName() != null ? shop.shopName() : "Shop #" + shop.shopId());

        return createItem(material, name, lore.toArray(new String[0]));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        BrowserSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        // Check if clicking in our GUI
        String title = event.getView().getTitle();
        if (!title.contains("Shop Manager")) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();

        // Navigation buttons
        if (slot == SLOT_PREV_PAGE && session.page > 0) {
            openShopBrowser(player, session.page - 1);
            return;
        }

        if (slot == SLOT_NEXT_PAGE) {
            int totalPages = (int) Math.ceil((double) session.shops.size() / ITEMS_PER_PAGE);
            if (session.page < totalPages - 1) {
                openShopBrowser(player, session.page + 1);
            }
            return;
        }

        if (slot == SLOT_STATS) {
            player.closeInventory();
            new AdminStatsGUI(plugin).openStatsView(player);
            return;
        }

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        // Shop item clicked
        if (slot >= 0 && slot < ITEMS_PER_PAGE) {
            int shopIndex = (session.page * ITEMS_PER_PAGE) + slot;
            if (shopIndex < session.shops.size()) {
                ShopDataDTO shop = session.shops.get(shopIndex);
                handleShopClick(player, shop, event.isLeftClick(), event.isShiftClick());
            }
        }
    }

    /**
     * Handles clicks on shop items.
     */
    private void handleShopClick(Player player, ShopDataDTO shop, boolean leftClick, boolean shift) {
        if (leftClick && !shift) {
            // View shop details
            player.sendMessage(ChatColor.GOLD + "=== Shop #" + shop.shopId() + " Details ===");
            player.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE +
                    Bukkit.getOfflinePlayer(shop.ownerUuid()).getName());
            player.sendMessage(ChatColor.YELLOW + "Name: " + ChatColor.WHITE + shop.shopName());
            player.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + shop.shopType());
            player.sendMessage(ChatColor.YELLOW + "Status: " +
                    (shop.isActive() ? ChatColor.GREEN + "Active" : ChatColor.RED + "Inactive"));
            player.sendMessage(ChatColor.YELLOW + "Created: " + ChatColor.WHITE + shop.createdAt());
            player.sendMessage(ChatColor.YELLOW + "Modified: " + ChatColor.WHITE + shop.lastModified());

        } else if (!leftClick && shift) {
            // Delete shop (would need implementation)
            player.sendMessage(ChatColor.RED + "Delete functionality not yet implemented");
            player.sendMessage(ChatColor.GRAY + "Use /shop admin delete " + shop.shopId());

        } else if (!leftClick) {
            // Toggle status (would need implementation)
            player.sendMessage(ChatColor.YELLOW + "Toggle functionality not yet implemented");
            player.sendMessage(ChatColor.GRAY + "Status: " +
                    (shop.isActive() ? ChatColor.GREEN + "Active" : ChatColor.RED + "Inactive"));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        BrowserSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            logger.debug("Admin shop browser closed by " + player.getName());
        }
    }

    /**
     * Gets all shops from the repository asynchronously.
     */
    private java.util.concurrent.CompletableFuture<List<ShopDataDTO>> getAllShops() {
        if (plugin.getShopRepository() == null) {
            logger.warning("ShopRepository is null, returning empty list");
            return java.util.concurrent.CompletableFuture.completedFuture(new ArrayList<>());
        }

        return plugin.getShopRepository().findAll()
            .exceptionally(ex -> {
                logger.error("Failed to fetch shops from repository: " + ex.getMessage());
                return new ArrayList<>();
            });
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
     * Checks if a player has an active browser session.
     */
    public boolean hasActiveGui(UUID playerUuid) {
        return activeSessions.containsKey(playerUuid);
    }

    /**
     * Closes any active GUI for a player.
     */
    public void closeGui(UUID playerUuid) {
        activeSessions.remove(playerUuid);
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
        activeSessions.clear();
    }

    /**
     * Internal session tracking for browser state.
     */
    private record BrowserSession(
            List<ShopDataDTO> shops,
            int page
    ) {}
}
