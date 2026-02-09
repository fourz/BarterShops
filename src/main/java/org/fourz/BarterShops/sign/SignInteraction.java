package org.fourz.BarterShops.sign;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.shop.ShopMode;
import org.fourz.BarterShops.sign.SignType;
import org.fourz.BarterShops.trade.TradeEngine;
import org.fourz.BarterShops.trade.TradeSession;
import org.fourz.BarterShops.trade.TradeConfirmationGUI;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


public class SignInteraction {
    private static final String CLASS_NAME = "SignInteraction";
    private static final long REVERT_DELAY_TICKS = 200L; // 10 seconds
    private static final long STATUS_DISPLAY_TICKS = 60L; // 3 seconds

    private final BarterShops plugin;
    private final LogManager logger;
    private final Map<Location, BukkitTask> activeRevertTasks = new ConcurrentHashMap<>();

    public SignInteraction(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, CLASS_NAME);
    }

    public void handleLeftClick(Player player, Sign sign, BarterSign barterSign, PlayerInteractEvent event) {
        if (!player.hasPermission("bartershops.configure")) {
            logger.debug("Left-click ignored - player lacks configure permission");
            return;
        }

        if (barterSign == null || !barterSign.getOwner().equals(player.getUniqueId())) {
            return; // Not owner
        }

        logger.debug("Owner left-click detected - mode: " + barterSign.getMode());
        cancelRevert(sign.getLocation());

        ShopMode currentMode = barterSign.getMode();

        // SETUP mode: Capture item in hand for stackable shops
        if (currentMode == ShopMode.SETUP) {
            ItemStack itemInHand = event.getItem();

            if (itemInHand == null || itemInHand.getType().isAir()) {
                player.sendMessage(ChatColor.YELLOW + "Hold an item to configure shop offering!");
                player.sendMessage(ChatColor.GRAY + "Or place items in chest for non-stackable shop");
                return;
            }

            // Capture the item with full NBT preservation
            barterSign.configureStackableShop(itemInHand, itemInHand.getAmount());
            barterSign.setType(SignType.STACKABLE);

            player.sendMessage(ChatColor.GREEN + "+ Stackable shop configured!");
            player.sendMessage(ChatColor.GRAY + "Selling: " + itemInHand.getAmount() + "x " +
                              itemInHand.getType().name());
            player.sendMessage(ChatColor.YELLOW + "Right-click to set price, then activate");

            event.setCancelled(true);
            SignDisplay.updateSign(sign, barterSign);
            return;
        }

        // All other modes: Advance mode (like right-click cycle)
        ShopMode nextMode = barterSign.getMode().getNextMode();
        barterSign.setMode(nextMode);
        logger.debug("Mode advanced: " + currentMode + " -> " + nextMode);

        SignDisplay.updateSign(sign, barterSign);

        // Force client-side visual refresh
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            player.sendBlockChange(sign.getLocation(), sign.getBlock().getBlockData());
        }, 1L);

        event.setCancelled(true);
    }

    public void handleRightClick(Player player, Sign sign, BarterSign barterSign) {
        logger.debug(String.format("Processing %s interaction in mode: %s",
            player.getName(), barterSign.getMode()));

        if (barterSign.getOwner().equals(player.getUniqueId())) {
            handleOwnerRightClick(player, sign, barterSign);
        } else {
            handleCustomerRightClick(player, sign, barterSign);
        }
    }

    private void handleOwnerRightClick(Player player, Sign sign, BarterSign barterSign) {
        logger.debug("Processing owner interaction");
        cancelRevert(sign.getLocation());

        switch (barterSign.getMode()) {
            case SETUP -> {
                // Right-click in SETUP: Configure price
                ItemStack paymentItem = player.getInventory().getItemInMainHand();

                if (paymentItem == null || paymentItem.getType().isAir()) {
                    player.sendMessage(ChatColor.YELLOW + "Hold payment item and right-click!");
                    player.sendMessage(ChatColor.GRAY + "Example: Hold 5 diamonds to set price");
                    return;
                }

                barterSign.configurePrice(paymentItem, paymentItem.getAmount());

                player.sendMessage(ChatColor.GREEN + "+ Price configured!");
                player.sendMessage(ChatColor.GRAY + "Payment: " + paymentItem.getAmount() + "x " +
                                  paymentItem.getType().name());

                // Check if fully configured to auto-advance
                if (barterSign.isConfigured()) {
                    player.sendMessage(ChatColor.GREEN + "Shop ready! Right-click again to activate");
                }
            }

            case TYPE -> {
                // Cycle through SignTypes
                SignType currentType = barterSign.getType();
                SignType nextType = plugin.getTypeAvailabilityManager().getNextSignType(currentType);
                barterSign.setType(nextType);

                player.sendMessage(ChatColor.YELLOW + "Type: " + nextType.name());
                logger.debug("Owner: TYPE cycling - " + currentType + " -> " + nextType);
            }

            case BOARD -> {
                // Already active, show shop info
                player.sendMessage(ChatColor.GREEN + "Shop is active!");
                scheduleRevert(sign, barterSign);
            }

            case DELETE -> {
                // Persist in DELETE mode until sign broken
                player.sendMessage(ChatColor.RED + "Break sign to delete shop");
            }

            case HELP -> {
                // Return to BOARD
                barterSign.setMode(ShopMode.BOARD);
                scheduleRevert(sign, barterSign);
            }
        }

        SignDisplay.updateSign(sign, barterSign);
    }

    private void handleCustomerRightClick(Player player, Sign sign, BarterSign barterSign) {
        logger.debug("Processing customer interaction");

        if (barterSign.getMode() != ShopMode.BOARD) {
            logger.debug("Customer tried to interact with non-BOARD mode shop");
            return;
        }

        processTrade(player, sign, barterSign);
    }

    private void processTrade(Player player, Sign sign, BarterSign barterSign) {
        logger.debug("Processing trade for player: " + player.getName());

        TradeEngine tradeEngine = plugin.getTradeEngine();

        // Check fallback mode
        if (tradeEngine.isInFallbackMode()) {
            showTemporaryStatus(sign, barterSign, "\u00A7cTemporarily", "\u00A7cunavailable");
            logger.warning("Trade rejected - system in fallback mode");
            return;
        }

        SignType shopType = barterSign.getType();

        if (shopType == SignType.STACKABLE) {
            // Stackable shop: Trade the configured itemOffering
            processStackableTrade(player, sign, barterSign, tradeEngine);
        } else {
            // Non-stackable shop: Trade any item from chest
            processNonStackableTrade(player, sign, barterSign, tradeEngine);
        }
    }

    /**
     * Processes trade for STACKABLE shops.
     * Uses the configured itemOffering and priceItem/priceAmount.
     */
    private void processStackableTrade(Player player, Sign sign, BarterSign barterSign, TradeEngine tradeEngine) {
        logger.debug("Processing stackable trade for " + player.getName());

        ItemStack offering = barterSign.getItemOffering();
        ItemStack paymentItem = barterSign.getPriceItem();
        int paymentAmount = barterSign.getPriceAmount();

        // Validate configuration
        if (offering == null || paymentItem == null || paymentAmount <= 0) {
            showTemporaryStatus(sign, barterSign, "\u00A7cNot", "\u00A7cconfigured");
            logger.warning("Stackable shop not fully configured");
            return;
        }

        // Get shop container for stock verification
        Container shopContainer = barterSign.getShopContainer();
        if (shopContainer == null) {
            shopContainer = barterSign.getContainer();
        }

        if (shopContainer == null) {
            showTemporaryStatus(sign, barterSign, "\u00A7cNo container", "\u00A7cfound");
            logger.debug("Trade rejected - no shop container");
            return;
        }

        // Verify stock: count total of offering items (using isSimilar for NBT comparison)
        int availableStock = 0;
        for (ItemStack item : shopContainer.getInventory().getContents()) {
            if (item != null && item.isSimilar(offering)) {
                availableStock += item.getAmount();
            }
        }

        if (availableStock < offering.getAmount()) {
            showTemporaryStatus(sign, barterSign, "\u00A7cOut of", "\u00A7cstock");
            logger.debug("Trade rejected - insufficient stock. Available: " + availableStock +
                        ", Required: " + offering.getAmount());
            return;
        }

        // Verify player has payment: count total of payment items in inventory
        int playerPayment = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.isSimilar(paymentItem)) {
                playerPayment += item.getAmount();
            }
        }

        if (playerPayment < paymentAmount) {
            showTemporaryStatus(sign, barterSign, "\u00A7eNeed: " + paymentAmount,
                               "\u00A7e" + paymentItem.getType().name());
            logger.debug("Trade rejected - insufficient payment. Have: " + playerPayment +
                        ", Required: " + paymentAmount);
            return;
        }

        // Both validations passed - initiate trade session
        executeConfiguredTrade(player, sign, barterSign, tradeEngine, offering, paymentItem, paymentAmount);
    }

    /**
     * Processes trade for NON-STACKABLE shops.
     * Trades any item from chest for the configured price.
     */
    private void processNonStackableTrade(Player player, Sign sign, BarterSign barterSign, TradeEngine tradeEngine) {
        logger.debug("Processing non-stackable trade for " + player.getName());

        ItemStack paymentItem = barterSign.getPriceItem();
        int paymentAmount = barterSign.getPriceAmount();

        // Validate configuration
        if (paymentItem == null || paymentAmount <= 0) {
            showTemporaryStatus(sign, barterSign, "\u00A7cNot", "\u00A7cconfigured");
            logger.warning("Non-stackable shop not fully configured");
            return;
        }

        // Get shop container
        Container shopContainer = barterSign.getShopContainer();
        if (shopContainer == null) {
            shopContainer = barterSign.getContainer();
        }

        if (shopContainer == null) {
            showTemporaryStatus(sign, barterSign, "\u00A7cNo container", "\u00A7cfound");
            logger.debug("Trade rejected - no shop container");
            return;
        }

        // Get first non-air item from chest (any item)
        ItemStack offering = null;
        for (ItemStack item : shopContainer.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                offering = item.clone();
                offering.setAmount(1); // Non-stackable: quantity 1
                break;
            }
        }

        if (offering == null) {
            showTemporaryStatus(sign, barterSign, "\u00A7cNo items", "\u00A7cin shop");
            logger.debug("Trade rejected - no items in shop container");
            return;
        }

        // Verify player has payment
        int playerPayment = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.isSimilar(paymentItem)) {
                playerPayment += item.getAmount();
            }
        }

        if (playerPayment < paymentAmount) {
            showTemporaryStatus(sign, barterSign, "\u00A7eNeed: " + paymentAmount,
                               "\u00A7e" + paymentItem.getType().name());
            logger.debug("Trade rejected - insufficient payment. Have: " + playerPayment +
                        ", Required: " + paymentAmount);
            return;
        }

        // Both validations passed - initiate trade session
        executeConfiguredTrade(player, sign, barterSign, tradeEngine, offering, paymentItem, paymentAmount);
    }

    /**
     * Executes a validated trade by initiating a trade session and opening confirmation GUI.
     * Works for both stackable and non-stackable shops.
     */
    private void executeConfiguredTrade(Player player, Sign sign, BarterSign barterSign,
                                        TradeEngine tradeEngine, ItemStack offering,
                                        ItemStack paymentItem, int paymentAmount) {
        TradeConfirmationGUI confirmationGUI = plugin.getTradeConfirmationGUI();

        // Initiate trade session
        Optional<TradeSession> sessionOpt = tradeEngine.initiateTrade(player, barterSign);
        if (sessionOpt.isEmpty()) {
            showTemporaryStatus(sign, barterSign, "\u00A7cTrade", "\u00A7cunavailable");
            logger.debug("Trade rejected - failed to create session");
            return;
        }

        TradeSession session = sessionOpt.get();

        // Configure trade items with validated offering and payment
        session.setOfferedItem(offering, offering.getAmount());
        session.setRequestedItem(paymentItem, paymentAmount);
        session.setState(TradeSession.TradeState.AWAITING_BUYER_CONFIRM);

        logger.debug("Trade session created: " + session.getSessionId() +
                    " | Offering: " + offering.getAmount() + "x " + offering.getType().name() +
                    " | Payment: " + paymentAmount + "x " + paymentItem.getType().name());

        // Open confirmation GUI
        confirmationGUI.openConfirmation(player, session,
            // On confirm
            (confirmedSession) -> {
                logger.debug("Trade confirmed for session: " + confirmedSession.getSessionId());
                confirmedSession.setState(TradeSession.TradeState.AWAITING_FINAL_CONFIRM);

                tradeEngine.executeTrade(confirmedSession.getSessionId())
                    .thenAccept(result -> {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (result.success()) {
                                player.sendMessage(ChatColor.GREEN + "Trade complete!");
                                logger.info("Trade completed: " + result.transactionId());
                            } else {
                                player.sendMessage(ChatColor.RED + "Trade failed: " + result.message());
                                logger.debug("Trade failed: " + result.message());
                            }
                        });
                    });
            },
            // On cancel
            (cancelledSession) -> {
                logger.debug("Trade cancelled for session: " + cancelledSession.getSessionId());
                tradeEngine.cancelSession(cancelledSession.getSessionId());
                player.sendMessage(ChatColor.YELLOW + "Trade cancelled.");
            }
        );
    }

    // ========== Temporary Status Display ==========

    private void showTemporaryStatus(Sign sign, BarterSign barterSign, String line1, String line2) {
        SignDisplay.displayTemporaryMessage(sign, line1, line2);
        scheduleRevert(sign, barterSign, STATUS_DISPLAY_TICKS);
    }

    // ========== Auto-Revert Scheduling ==========

    private void scheduleRevert(Sign sign, BarterSign barterSign) {
        scheduleRevert(sign, barterSign, REVERT_DELAY_TICKS);
    }

    private void scheduleRevert(Sign sign, BarterSign barterSign, long delayTicks) {
        Location loc = sign.getLocation();
        if (loc == null) return;
        cancelRevert(loc);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                barterSign.setMode(ShopMode.BOARD);
                SignDisplay.updateSign(sign, barterSign);
                activeRevertTasks.remove(loc);
            }
        }.runTaskLater(plugin, delayTicks);

        activeRevertTasks.put(loc, task);
    }

    private void cancelRevert(Location loc) {
        if (loc == null) return;
        BukkitTask existing = activeRevertTasks.remove(loc);
        if (existing != null) {
            existing.cancel();
        }
    }

    public void cleanup() {
        activeRevertTasks.values().forEach(BukkitTask::cancel);
        activeRevertTasks.clear();
    }
}
