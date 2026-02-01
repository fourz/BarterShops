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
import org.fourz.BarterShops.data.dto.TradeRecordDTO;
import org.fourz.rvnkcore.util.log.LogManager;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Admin GUI for viewing trade history with filtering and pagination.
 * Shows all completed trades across the server with detailed transaction info.
 *
 * Permission: bartershops.admin.gui
 */
public class AdminTradeHistoryGUI implements Listener {

    private final BarterShops plugin;
    private final LogManager logger;

    /** Active GUI sessions by player UUID */
    private final Map<UUID, HistorySession> activeSessions = new HashMap<>();

    private static final int GUI_SIZE = 54; // 6 rows
    private static final int ITEMS_PER_PAGE = 45; // 5 rows for trades

    // Navigation slots (bottom row)
    private static final int SLOT_PREV_PAGE = 45;
    private static final int SLOT_FILTER = 48;
    private static final int SLOT_REFRESH = 49;
    private static final int SLOT_NEXT_PAGE = 53;
    private static final int SLOT_CLOSE = 50;

    // Date formatter
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy HH:mm");

    public AdminTradeHistoryGUI(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "AdminTradeHistoryGUI");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Opens the trade history viewer for a player.
     *
     * @param player The admin player viewing history
     */
    public void openTradeHistory(Player player) {
        openTradeHistory(player, 0, TradeFilter.ALL);
    }

    /**
     * Opens the trade history viewer at a specific page with filter.
     *
     * @param player The admin player viewing history
     * @param page The page number (0-indexed)
     * @param filter The filter to apply
     */
    public void openTradeHistory(Player player, int page, TradeFilter filter) {
        // Close any existing GUI
        if (activeSessions.containsKey(player.getUniqueId())) {
            player.closeInventory();
        }

        // Get all trades (async would be better)
        List<TradeRecordDTO> allTrades = getAllTrades();

        // Apply filter
        List<TradeRecordDTO> filteredTrades = filterTrades(allTrades, filter);

        // Calculate pagination
        int totalPages = (int) Math.ceil((double) filteredTrades.size() / ITEMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;

        // Clamp page number
        page = Math.max(0, Math.min(page, totalPages - 1));

        // Build the GUI
        String title = ChatColor.DARK_PURPLE + "Trade History " +
                       ChatColor.GRAY + "(Page " + (page + 1) + "/" + totalPages + ")";
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, title);

        // Fill bottom navigation bar
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) {
            gui.setItem(i, filler);
        }

        // Add trades for current page
        int startIdx = page * ITEMS_PER_PAGE;
        int endIdx = Math.min(startIdx + ITEMS_PER_PAGE, filteredTrades.size());

        for (int i = startIdx; i < endIdx; i++) {
            TradeRecordDTO trade = filteredTrades.get(i);
            ItemStack tradeItem = createTradeIcon(trade);
            gui.setItem(i - startIdx, tradeItem);
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

        // Filter button
        gui.setItem(SLOT_FILTER, createItem(Material.HOPPER,
                ChatColor.GOLD + "Filter: " + filter.name(),
                ChatColor.GRAY + "Click to cycle filters"));

        // Refresh button
        gui.setItem(SLOT_REFRESH, createItem(Material.COMPASS,
                ChatColor.GREEN + "Refresh",
                ChatColor.GRAY + "Reload trade data"));

        // Close button
        gui.setItem(SLOT_CLOSE, createItem(Material.BARRIER,
                ChatColor.RED + "Close"));

        // Store session
        HistorySession session = new HistorySession(filteredTrades, page, filter);
        activeSessions.put(player.getUniqueId(), session);

        // Open GUI
        player.openInventory(gui);
        logger.debug("Opened trade history for " + player.getName() +
                " (page " + page + ", filter " + filter + ")");
    }

    /**
     * Creates an icon representing a trade record.
     */
    private ItemStack createTradeIcon(TradeRecordDTO trade) {
        Material material = switch (trade.status()) {
            case COMPLETED -> Material.EMERALD;
            case CANCELLED -> Material.REDSTONE;
            case FAILED -> Material.BARRIER;
            case PENDING -> Material.CLOCK;
            case REFUNDED -> Material.GOLD_INGOT;
        };

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Transaction: " + ChatColor.WHITE +
                trade.transactionId().substring(0, 8) + "...");
        lore.add(ChatColor.GRAY + "Shop ID: " + ChatColor.WHITE + trade.shopId());
        lore.add(ChatColor.GRAY + "Buyer: " + ChatColor.WHITE +
                Bukkit.getOfflinePlayer(trade.buyerUuid()).getName());
        lore.add(ChatColor.GRAY + "Seller: " + ChatColor.WHITE +
                Bukkit.getOfflinePlayer(trade.sellerUuid()).getName());
        lore.add(ChatColor.GRAY + "Quantity: " + ChatColor.WHITE + trade.quantity());
        lore.add(ChatColor.GRAY + "Price: " + ChatColor.WHITE +
                trade.pricePaid() + " " + trade.currencyMaterial());
        lore.add(ChatColor.GRAY + "Status: " + getStatusColor(trade.status()) + trade.status());
        lore.add(ChatColor.GRAY + "Date: " + ChatColor.WHITE +
                DATE_FORMAT.format(trade.completedAt()));
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click for full details");

        String name = ChatColor.AQUA + "Trade #" + trade.transactionId().substring(0, 8);

        return createItem(material, name, lore.toArray(new String[0]));
    }

    /**
     * Gets color for trade status.
     */
    private ChatColor getStatusColor(TradeRecordDTO.TradeStatus status) {
        return switch (status) {
            case COMPLETED -> ChatColor.GREEN;
            case CANCELLED -> ChatColor.YELLOW;
            case FAILED -> ChatColor.RED;
            case PENDING -> ChatColor.GOLD;
            case REFUNDED -> ChatColor.AQUA;
        };
    }

    /**
     * Filters trades based on the selected filter.
     */
    private List<TradeRecordDTO> filterTrades(List<TradeRecordDTO> trades, TradeFilter filter) {
        return switch (filter) {
            case ALL -> trades;
            case COMPLETED -> trades.stream()
                    .filter(t -> t.status() == TradeRecordDTO.TradeStatus.COMPLETED)
                    .toList();
            case FAILED -> trades.stream()
                    .filter(t -> t.status() == TradeRecordDTO.TradeStatus.FAILED ||
                                 t.status() == TradeRecordDTO.TradeStatus.CANCELLED)
                    .toList();
            case RECENT -> trades.stream()
                    .filter(t -> System.currentTimeMillis() - t.getTimestamp() < 3600000) // 1 hour
                    .toList();
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        HistorySession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        // Check if clicking in our GUI
        String title = event.getView().getTitle();
        if (!title.contains("Trade History")) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();

        // Navigation buttons
        if (slot == SLOT_PREV_PAGE && session.page > 0) {
            openTradeHistory(player, session.page - 1, session.filter);
            return;
        }

        if (slot == SLOT_NEXT_PAGE) {
            int totalPages = (int) Math.ceil((double) session.trades.size() / ITEMS_PER_PAGE);
            if (session.page < totalPages - 1) {
                openTradeHistory(player, session.page + 1, session.filter);
            }
            return;
        }

        if (slot == SLOT_FILTER) {
            // Cycle through filters
            TradeFilter nextFilter = TradeFilter.values()[(session.filter.ordinal() + 1) % TradeFilter.values().length];
            openTradeHistory(player, 0, nextFilter);
            return;
        }

        if (slot == SLOT_REFRESH) {
            openTradeHistory(player, session.page, session.filter);
            player.sendMessage(ChatColor.GREEN + "Trade history refreshed!");
            return;
        }

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        // Trade item clicked
        if (slot >= 0 && slot < ITEMS_PER_PAGE) {
            int tradeIndex = (session.page * ITEMS_PER_PAGE) + slot;
            if (tradeIndex < session.trades.size()) {
                TradeRecordDTO trade = session.trades.get(tradeIndex);
                showTradeDetails(player, trade);
            }
        }
    }

    /**
     * Shows detailed trade information in chat.
     */
    private void showTradeDetails(Player player, TradeRecordDTO trade) {
        player.sendMessage(ChatColor.GOLD + "=== Trade Details ===");
        player.sendMessage(ChatColor.YELLOW + "Transaction ID: " + ChatColor.WHITE + trade.transactionId());
        player.sendMessage(ChatColor.YELLOW + "Shop ID: " + ChatColor.WHITE + trade.shopId());
        player.sendMessage(ChatColor.YELLOW + "Buyer: " + ChatColor.WHITE +
                Bukkit.getOfflinePlayer(trade.buyerUuid()).getName());
        player.sendMessage(ChatColor.YELLOW + "Seller: " + ChatColor.WHITE +
                Bukkit.getOfflinePlayer(trade.sellerUuid()).getName());
        player.sendMessage(ChatColor.YELLOW + "Item: " + ChatColor.WHITE + trade.itemStackData());
        player.sendMessage(ChatColor.YELLOW + "Quantity: " + ChatColor.WHITE + trade.quantity());
        player.sendMessage(ChatColor.YELLOW + "Currency: " + ChatColor.WHITE + trade.currencyMaterial());
        player.sendMessage(ChatColor.YELLOW + "Price Paid: " + ChatColor.WHITE + trade.pricePaid());
        player.sendMessage(ChatColor.YELLOW + "Status: " + getStatusColor(trade.status()) + trade.status());
        player.sendMessage(ChatColor.YELLOW + "Completed: " + ChatColor.WHITE +
                DATE_FORMAT.format(trade.completedAt()));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        HistorySession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            logger.debug("Trade history closed by " + player.getName());
        }
    }

    /**
     * Gets all trades from the plugin.
     * TODO: Replace with async repository call
     */
    private List<TradeRecordDTO> getAllTrades() {
        // For now, return empty list - would integrate with ITradeRepository
        // when repository implementation is complete
        return new ArrayList<>();
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
     * Checks if a player has an active history session.
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
     * Trade filter options.
     */
    public enum TradeFilter {
        ALL,
        COMPLETED,
        FAILED,
        RECENT
    }

    /**
     * Internal session tracking for history state.
     */
    private record HistorySession(
            List<TradeRecordDTO> trades,
            int page,
            TradeFilter filter
    ) {}
}
