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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


public class SignInteraction {
    private static final String CLASS_NAME = "SignInteraction";
    private static final long REVERT_DELAY_TICKS = 200L; // 10 seconds
    private static final long STATUS_DISPLAY_TICKS = 60L; // 3 seconds
    private static final long DELETE_CONFIRMATION_TIMEOUT_TICKS = 100L; // 5 seconds

    private final BarterShops plugin;
    private final LogManager logger;
    private final Map<Location, BukkitTask> activeRevertTasks = new ConcurrentHashMap<>();
    private final Map<String, Long> pendingDeletions = new ConcurrentHashMap<>(); // signId -> timestamp

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

        switch (barterSign.getMode()) {
            case SETUP -> {
                // Multi-step configuration flow
                ItemStack itemInHand = event.getItem();
                if (itemInHand == null || itemInHand.getType().isAir()) {
                    player.sendMessage(ChatColor.YELLOW + "Hold an item when clicking");
                    break;
                }

                // Step 1: Set offering item
                if (barterSign.getItemOffering() == null) {
                    barterSign.configureStackableShop(itemInHand, itemInHand.getAmount());
                    player.sendMessage(ChatColor.GREEN + "+ Offering set: " + itemInHand.getAmount() + "x " + itemInHand.getType().name());
                    player.sendMessage(ChatColor.GRAY + "Now set payment/price");
                    logger.debug("Offering configured: " + itemInHand.getType());

                    // Save configuration to database
                    if (barterSign.getShopId() > 0) {
                        plugin.getSignManager().saveSignConfiguration(barterSign);
                    }
                }
                // Step 2: Configure payment (type-dependent)
                else {
                    SignType type = barterSign.getType();

                    if (type == SignType.BARTER) {
                        // BARTER: Add/update payment option
                        if (player.isSneaking()) {
                            // Shift+L-Click: Remove payment option
                            if (barterSign.removePaymentOption(itemInHand.getType())) {
                                player.sendMessage(ChatColor.RED + "- Removed: " + itemInHand.getType().name());
                                // Save configuration to database
                                if (barterSign.getShopId() > 0) {
                                    plugin.getSignManager().saveSignConfiguration(barterSign);
                                }
                            } else {
                                player.sendMessage(ChatColor.YELLOW + "Not in payment list");
                            }
                        } else {
                            // L-Click: Add payment option
                            barterSign.addPaymentOption(itemInHand, itemInHand.getAmount());
                            player.sendMessage(ChatColor.GREEN + "+ Payment added: " + itemInHand.getAmount() + "x " + itemInHand.getType().name());

                            // Save configuration to database
                            if (barterSign.getShopId() > 0) {
                                plugin.getSignManager().saveSignConfiguration(barterSign);
                            }
                        }
                    } else {
                        // BUY/SELL: Configure price
                        ItemStack currentPrice = barterSign.getPriceItem();

                        if (currentPrice == null) {
                            // First click: Set currency item
                            barterSign.configurePrice(itemInHand, 1);
                            player.sendMessage(ChatColor.GREEN + "+ Currency set: " + itemInHand.getType().name());
                            player.sendMessage(ChatColor.GRAY + "L-Click ±1, Shift+R +16");

                            // Save configuration to database
                            if (barterSign.getShopId() > 0) {
                                plugin.getSignManager().saveSignConfiguration(barterSign);
                            }
                        } else if (currentPrice.getType() == itemInHand.getType()) {
                            // Adjust price amount with same currency
                            int currentAmount = barterSign.getPriceAmount();
                            int newAmount = currentAmount;

                            if (player.isSneaking()) {
                                // Shift+L-Click: -1
                                newAmount = Math.max(0, currentAmount - 1);
                            } else {
                                // L-Click: +1
                                newAmount = currentAmount + 1;
                            }

                            barterSign.configurePrice(itemInHand, newAmount);
                            player.sendMessage(ChatColor.AQUA + "Price: " + newAmount + "x " + itemInHand.getType().name());

                            // Save configuration to database
                            if (barterSign.getShopId() > 0) {
                                plugin.getSignManager().saveSignConfiguration(barterSign);
                            }
                        } else {
                            // Different currency - confirm change
                            player.sendMessage(ChatColor.YELLOW + "! Currency change: " + currentPrice.getType().name() + " → " + itemInHand.getType().name());
                            barterSign.configurePrice(itemInHand, 1);

                            // Save configuration to database
                            if (barterSign.getShopId() > 0) {
                                plugin.getSignManager().saveSignConfiguration(barterSign);
                            }
                        }
                    }
                }
            }

            case TYPE -> {
                // Left-click in TYPE: Cycle through shop types (BARTER, BUY, SELL)
                // Inventory type (stackable/unstackable) is auto-detected separately
                SignType currentType = barterSign.getType();
                SignType nextType = plugin.getTypeAvailabilityManager().getNextSignType(currentType);

                // Clear payment configuration when changing type
                if (currentType != nextType) {
                    barterSign.clearPaymentOptions();
                    barterSign.configurePrice(null, 0);
                    player.sendMessage(ChatColor.YELLOW + "! Type changed: " + currentType + " → " + nextType);
                    player.sendMessage(ChatColor.GRAY + "Payment configuration cleared");

                    // Save configuration to database
                    if (barterSign.getShopId() > 0) {
                        plugin.getSignManager().saveSignConfiguration(barterSign);
                    }
                }

                barterSign.setType(nextType);
                logger.debug("Owner: TYPE cycling - " + currentType + " -> " + nextType);
            }

            case BOARD -> {
                // Left-click in BOARD: Owner quantity adjustment
                handleOwnerBoardClick(player, sign, barterSign, event);
                SignDisplay.updateSign(sign, barterSign);
                event.setCancelled(true);
                return;
            }

            case DELETE -> {
                // Left-click in DELETE: Two-step confirmation
                String signId = barterSign.getId();
                long currentTime = System.currentTimeMillis();

                // Check if this is a confirmation click (within timeout window)
                if (pendingDeletions.containsKey(signId)) {
                    long pendingTime = pendingDeletions.get(signId);
                    if (currentTime - pendingTime < (DELETE_CONFIRMATION_TIMEOUT_TICKS * 50)) {
                        // Confirmation clicked - proceed with deletion
                        deleteShopAndSign(player, sign, barterSign);
                        pendingDeletions.remove(signId);
                        return;
                    }
                }

                // First click - set pending confirmation
                pendingDeletions.put(signId, currentTime);
                player.sendMessage(ChatColor.RED + "§cCLICK AGAIN TO CONFIRM DELETION");
                player.sendMessage(ChatColor.GRAY + "Chest items will NOT be deleted");

                // Show confirmation message on sign
                SignDisplay.displayDeleteConfirmation(sign);

                // Auto-clear after timeout
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (pendingDeletions.containsKey(signId)) {
                        pendingDeletions.remove(signId);
                        SignDisplay.updateSign(sign, barterSign); // Revert to normal DELETE display
                    }
                }, DELETE_CONFIRMATION_TIMEOUT_TICKS);
            }

            case HELP -> {
                // Left-click in HELP: Return to BOARD
                barterSign.setMode(ShopMode.BOARD);
                scheduleRevert(sign, barterSign);
            }
        }

        SignDisplay.updateSign(sign, barterSign);
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

    private void handleOwnerBoardClick(Player player, Sign sign, BarterSign barterSign, PlayerInteractEvent event) {
        ItemStack itemInHand = event.getItem();
        if (itemInHand == null || itemInHand.getType().isAir()) {
            player.sendMessage(ChatColor.YELLOW + "Hold offering item to adjust quantity");
            return;
        }

        ItemStack offering = barterSign.getItemOffering();
        if (offering == null || offering.getType() != itemInHand.getType()) {
            player.sendMessage(ChatColor.RED + "Hold the offering item (" +
                (offering != null ? offering.getType().name() : "not set") + ")");
            return;
        }

        int currentQty = offering.getAmount();
        int newQty = currentQty;

        if (player.isSneaking()) {
            // Shift+L-Click: -1
            newQty = Math.max(1, currentQty - 1);
        } else {
            // L-Click: +1
            newQty = currentQty + 1;
        }

        offering.setAmount(newQty);
        barterSign.configureStackableShop(offering, newQty);

        player.sendMessage(ChatColor.AQUA + "Quantity: " + newQty + "x " + offering.getType().name());

        // Save configuration to database
        if (barterSign.getShopId() > 0) {
            plugin.getSignManager().saveSignConfiguration(barterSign);
        }
    }

    private void handleOwnerRightClick(Player player, Sign sign, BarterSign barterSign) {
        logger.debug("Processing owner interaction");
        cancelRevert(sign.getLocation());

        ShopMode currentMode = barterSign.getMode();

        // Right-click: Always cycle to next mode (consistent behavior)
        ShopMode nextMode = barterSign.getMode().getNextMode();
        barterSign.setMode(nextMode);
        logger.debug("Mode advanced: " + currentMode + " -> " + nextMode);

        // Mode messages moved to sign display - no chat spam

        // Schedule auto-revert for DELETE and HELP modes (10 second timeout)
        if (nextMode == ShopMode.DELETE || nextMode == ShopMode.HELP) {
            scheduleRevert(sign, barterSign);
            logger.debug("Auto-revert scheduled for mode: " + nextMode);
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

        // Use the stored stackable/unstackable flag (set by left-click or chest detection)
        boolean isStackable = barterSign.getShopStackableMode();

        if (isStackable) {
            // Stackable shop: Trade configured items
            processStackableTrade(player, sign, barterSign, tradeEngine);
        } else {
            // Non-stackable shop: Trade any item from chest
            processNonStackableTrade(player, sign, barterSign, tradeEngine);
        }
    }

    /**
     * Processes trade for STACKABLE shops.
     * Uses the configured itemOffering and payment (type-dependent).
     * For BARTER mode, supports multiple payment options.
     */
    private void processStackableTrade(Player player, Sign sign, BarterSign barterSign, TradeEngine tradeEngine) {
        logger.debug("Processing stackable trade for " + player.getName());

        ItemStack offering = barterSign.getItemOffering();
        SignType shopType = barterSign.getType();

        // Validate offering configured
        if (offering == null) {
            showTemporaryStatus(sign, barterSign, "\u00A7cNot", "\u00A7cconfigured");
            return;
        }

        // Get payment item based on shop type
        ItemStack paymentItem = null;
        int paymentAmount = 0;

        if (shopType == SignType.BARTER) {
            // BARTER: Check what customer is holding
            ItemStack customerHand = player.getInventory().getItemInMainHand();
            if (customerHand == null || customerHand.getType().isAir()) {
                showTemporaryStatus(sign, barterSign, "\u00A7eHold payment", "\u00A7eitem");
                return;
            }

            // Check if held item is accepted payment
            if (!barterSign.isPaymentAccepted(customerHand)) {
                List<ItemStack> accepted = barterSign.getAcceptedPayments();
                if (!accepted.isEmpty()) {
                    showTemporaryStatus(sign, barterSign, "\u00A7cNot accepted",
                        "\u00A7c" + customerHand.getType().name());
                }
                return;
            }

            // Get payment amount for this material
            paymentAmount = barterSign.getPaymentAmount(customerHand.getType());
            paymentItem = customerHand.clone();
            paymentItem.setAmount(1);

        } else {
            // BUY/SELL: Use configured price
            paymentItem = barterSign.getPriceItem();
            paymentAmount = barterSign.getPriceAmount();

            if (paymentItem == null || paymentAmount <= 0) {
                showTemporaryStatus(sign, barterSign, "\u00A7cNot", "\u00A7cconfigured");
                return;
            }
        }

        // Get shop container for stock verification
        Container shopContainer = barterSign.getShopContainer();
        if (shopContainer == null) {
            shopContainer = barterSign.getContainer();
        }

        if (shopContainer == null) {
            showTemporaryStatus(sign, barterSign, "\u00A7cNo container", "\u00A7cfound");
            return;
        }

        // Verify stock availability
        int availableStock = 0;
        for (ItemStack item : shopContainer.getInventory().getContents()) {
            if (item != null && item.isSimilar(offering)) {
                availableStock += item.getAmount();
            }
        }

        if (availableStock < offering.getAmount()) {
            showTemporaryStatus(sign, barterSign, "\u00A7cOut of", "\u00A7cstock");
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

    // ========== Shop Deletion ==========

    private void deleteShopAndSign(Player player, Sign sign, BarterSign barterSign) {
        try {
            Location signLocation = sign.getLocation();
            logger.debug("Attempting to delete shop at: " + signLocation);

            // Reset type detection so shop can be reconfigured if recreated
            barterSign.resetTypeDetection();

            // Delete from database asynchronously
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    // Look up shop by location to get the integer shop ID
                    String world = signLocation.getWorld() != null ? signLocation.getWorld().getName() : "unknown";
                    var shopOptional = plugin.getShopRepository()
                        .findBySignLocation(world, signLocation.getX(), signLocation.getY(), signLocation.getZ())
                        .get();

                    if (shopOptional.isPresent()) {
                        int shopId = shopOptional.get().shopId();
                        logger.debug("Found shop in database with ID: " + shopId);

                        // Remove shop from database (items in chest remain untouched)
                        boolean deleted = plugin.getShopRepository().deleteById(shopId).get();

                        if (deleted) {
                            logger.info("Shop deleted from database: " + shopId);

                            // Sync back to main thread to modify sign
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                // Destroy the sign block (only the sign, not the chest)
                                sign.getBlock().setType(Material.AIR);

                                // Notify player
                                player.sendMessage(ChatColor.GREEN + "✓ Shop deleted successfully!");
                                player.sendMessage(ChatColor.GRAY + "Chest items were NOT deleted");

                                // Cancel any pending reverts
                                cancelRevert(signLocation);

                                logger.info("Shop sign destroyed at: " + signLocation);
                            });
                        } else {
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                player.sendMessage(ChatColor.RED + "✗ Failed to delete shop from database");
                            });
                        }
                    } else {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            player.sendMessage(ChatColor.RED + "✗ Shop not found in database");
                        });
                    }

                } catch (Exception e) {
                    logger.error("Failed to delete shop at location: " + signLocation, e);
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(ChatColor.RED + "✗ Failed to delete shop: " + e.getMessage());
                    });
                }
            });

        } catch (Exception e) {
            logger.error("Error in deleteShopAndSign", e);
            player.sendMessage(ChatColor.RED + "✗ An error occurred during deletion");
        }
    }

    public void cleanup() {
        activeRevertTasks.values().forEach(BukkitTask::cancel);
        activeRevertTasks.clear();
        pendingDeletions.clear();
    }
}
