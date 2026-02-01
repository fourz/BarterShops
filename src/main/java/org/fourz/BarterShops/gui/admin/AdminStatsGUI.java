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
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;

/**
 * Admin GUI for viewing analytics and server-wide statistics.
 * Displays shop counts, trade metrics, player activity, and system health.
 *
 * Permission: bartershops.admin.gui
 */
public class AdminStatsGUI implements Listener {

    private final BarterShops plugin;
    private final LogManager logger;

    /** Active GUI sessions by player UUID */
    private final Set<UUID> activeSessions = new HashSet<>();

    private static final int GUI_SIZE = 27; // 3 rows

    // Stats display slots
    private static final int SLOT_SHOP_STATS = 10;
    private static final int SLOT_TRADE_STATS = 12;
    private static final int SLOT_PLAYER_STATS = 14;
    private static final int SLOT_SYSTEM_STATS = 16;

    // Navigation slots
    private static final int SLOT_REFRESH = 22;
    private static final int SLOT_BACK = 24;

    public AdminStatsGUI(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "AdminStatsGUI");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Opens the statistics dashboard for a player.
     *
     * @param player The admin player viewing stats
     */
    public void openStatsView(Player player) {
        // Close any existing GUI
        if (activeSessions.contains(player.getUniqueId())) {
            player.closeInventory();
        }

        // Build the GUI
        String title = ChatColor.DARK_PURPLE + "BarterShops Analytics";
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, title);

        // Fill with background
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < GUI_SIZE; i++) {
            gui.setItem(i, filler);
        }

        // Get statistics
        ShopStats shopStats = getShopStatistics();
        TradeStats tradeStats = getTradeStatistics();
        PlayerStats playerStats = getPlayerStatistics();
        SystemStats systemStats = getSystemStatistics();

        // Shop statistics
        gui.setItem(SLOT_SHOP_STATS, createItem(Material.CHEST,
                ChatColor.GOLD + "Shop Statistics",
                ChatColor.GRAY + "Total Shops: " + ChatColor.WHITE + shopStats.totalShops,
                ChatColor.GRAY + "Active Shops: " + ChatColor.WHITE + shopStats.activeShops,
                ChatColor.GRAY + "Inactive Shops: " + ChatColor.WHITE + shopStats.inactiveShops,
                ChatColor.GRAY + "Admin Shops: " + ChatColor.WHITE + shopStats.adminShops,
                ChatColor.GRAY + "Avg Items/Shop: " + ChatColor.WHITE + shopStats.avgItemsPerShop,
                "",
                ChatColor.YELLOW + "Click for detailed view"));

        // Trade statistics
        gui.setItem(SLOT_TRADE_STATS, createItem(Material.EMERALD,
                ChatColor.GOLD + "Trade Statistics",
                ChatColor.GRAY + "Total Trades: " + ChatColor.WHITE + tradeStats.totalTrades,
                ChatColor.GRAY + "Completed: " + ChatColor.GREEN + tradeStats.completedTrades,
                ChatColor.GRAY + "Failed: " + ChatColor.RED + tradeStats.failedTrades,
                ChatColor.GRAY + "Today: " + ChatColor.WHITE + tradeStats.tradesToday,
                ChatColor.GRAY + "This Week: " + ChatColor.WHITE + tradeStats.tradesThisWeek,
                ChatColor.GRAY + "Avg/Day: " + ChatColor.WHITE + tradeStats.avgTradesPerDay,
                "",
                ChatColor.YELLOW + "Click to view history"));

        // Player statistics
        gui.setItem(SLOT_PLAYER_STATS, createItem(Material.PLAYER_HEAD,
                ChatColor.GOLD + "Player Statistics",
                ChatColor.GRAY + "Unique Sellers: " + ChatColor.WHITE + playerStats.uniqueSellers,
                ChatColor.GRAY + "Unique Buyers: " + ChatColor.WHITE + playerStats.uniqueBuyers,
                ChatColor.GRAY + "Active Sessions: " + ChatColor.WHITE + playerStats.activeSessions,
                ChatColor.GRAY + "Top Seller: " + ChatColor.WHITE + playerStats.topSeller,
                ChatColor.GRAY + "Top Buyer: " + ChatColor.WHITE + playerStats.topBuyer,
                "",
                ChatColor.YELLOW + "Click for player rankings"));

        // System statistics
        gui.setItem(SLOT_SYSTEM_STATS, createItem(Material.COMPARATOR,
                ChatColor.GOLD + "System Health",
                ChatColor.GRAY + "Plugin Version: " + ChatColor.WHITE +
                        plugin.getDescription().getVersion(),
                ChatColor.GRAY + "Database: " + ChatColor.WHITE + systemStats.databaseType,
                ChatColor.GRAY + "DB Connection: " + systemStats.dbStatusColor + systemStats.dbStatus,
                ChatColor.GRAY + "Active Listeners: " + ChatColor.WHITE + systemStats.activeListeners,
                ChatColor.GRAY + "Memory: " + ChatColor.WHITE + systemStats.memoryUsage,
                ChatColor.GRAY + "Uptime: " + ChatColor.WHITE + systemStats.uptime,
                "",
                ChatColor.YELLOW + "Click for diagnostics"));

        // Refresh button
        gui.setItem(SLOT_REFRESH, createItem(Material.COMPASS,
                ChatColor.GREEN + "Refresh",
                ChatColor.GRAY + "Reload all statistics"));

        // Back button
        gui.setItem(SLOT_BACK, createItem(Material.ARROW,
                ChatColor.YELLOW + "Back to Shop Browser"));

        // Store session
        activeSessions.add(player.getUniqueId());

        // Open GUI
        player.openInventory(gui);
        logger.debug("Opened stats view for " + player.getName());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!activeSessions.contains(player.getUniqueId())) return;

        // Check if clicking in our GUI
        String title = event.getView().getTitle();
        if (!title.contains("Analytics")) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();

        switch (slot) {
            case SLOT_SHOP_STATS -> {
                player.closeInventory();
                new AdminShopGUI(plugin).openShopBrowser(player);
            }
            case SLOT_TRADE_STATS -> {
                player.closeInventory();
                new AdminTradeHistoryGUI(plugin).openTradeHistory(player);
            }
            case SLOT_PLAYER_STATS -> {
                player.sendMessage(ChatColor.YELLOW + "Player rankings feature coming soon!");
            }
            case SLOT_SYSTEM_STATS -> {
                player.sendMessage(ChatColor.YELLOW + "System diagnostics feature coming soon!");
            }
            case SLOT_REFRESH -> {
                openStatsView(player);
                player.sendMessage(ChatColor.GREEN + "Statistics refreshed!");
            }
            case SLOT_BACK -> {
                player.closeInventory();
                new AdminShopGUI(plugin).openShopBrowser(player);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        if (activeSessions.remove(player.getUniqueId())) {
            logger.debug("Stats view closed by " + player.getName());
        }
    }

    /**
     * Gets shop statistics.
     */
    private ShopStats getShopStatistics() {
        // TODO: Integrate with IShopRepository when available
        int totalShops = plugin.getSignManager().getBarterSigns().size();
        return new ShopStats(totalShops, totalShops, 0, 0, 0);
    }

    /**
     * Gets trade statistics.
     */
    private TradeStats getTradeStatistics() {
        // TODO: Integrate with ITradeRepository when available
        return new TradeStats(0, 0, 0, 0, 0, 0);
    }

    /**
     * Gets player statistics.
     */
    private PlayerStats getPlayerStatistics() {
        // TODO: Integrate with repositories when available
        int activeSessions = plugin.getShopManager().getActiveSessions().size();
        return new PlayerStats(0, 0, activeSessions, "N/A", "N/A");
    }

    /**
     * Gets system statistics.
     */
    private SystemStats getSystemStatistics() {
        String dbType = plugin.getDatabaseManager() != null ? "Connected" : "Not Initialized";
        String dbStatus = plugin.getDatabaseManager() != null ? "Online" : "Offline";
        ChatColor dbColor = plugin.getDatabaseManager() != null ? ChatColor.GREEN : ChatColor.RED;

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        String memoryUsage = usedMemory + "MB / " + maxMemory + "MB";

        long uptime = (System.currentTimeMillis() - plugin.getStartTime()) / 1000;
        String uptimeStr = formatUptime(uptime);

        return new SystemStats(dbType, dbStatus, dbColor, 3, memoryUsage, uptimeStr);
    }

    /**
     * Formats uptime in human-readable format.
     */
    private String formatUptime(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;

        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        } else if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
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
     * Checks if a player has an active stats view.
     */
    public boolean hasActiveGui(UUID playerUuid) {
        return activeSessions.contains(playerUuid);
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

    // Statistics data classes
    private record ShopStats(int totalShops, int activeShops, int inactiveShops,
                             int adminShops, int avgItemsPerShop) {}

    private record TradeStats(int totalTrades, int completedTrades, int failedTrades,
                              int tradesToday, int tradesThisWeek, int avgTradesPerDay) {}

    private record PlayerStats(int uniqueSellers, int uniqueBuyers, int activeSessions,
                               String topSeller, String topBuyer) {}

    private record SystemStats(String databaseType, String dbStatus, ChatColor dbStatusColor,
                               int activeListeners, String memoryUsage, String uptime) {}
}
