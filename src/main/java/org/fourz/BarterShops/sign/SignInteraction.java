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
                // Left-click in SETUP: Set stackable/unstackable based on held item
                ItemStack itemInHand = event.getItem();

                if (itemInHand != null && !itemInHand.getType().isAir()) {
                    // Detect if held item is stackable and set shop mode
                    boolean isStackable = BarterSign.isItemStackable(itemInHand);
                    barterSign.setStackable(isStackable);

                    String modeText = isStackable ? "stackable" : "unstackable";
                    player.sendMessage(ChatColor.GREEN + "+ Shop type locked to " + modeText);
                    player.sendMessage(ChatColor.GRAY + "Right-click to set price");
                    logger.debug("Shop mode set to " + modeText + " for " + player.getName());
                } else {
                    player.sendMessage(ChatColor.YELLOW + "L-Click with an item to setup shop");
                    player.sendMessage(ChatColor.GRAY + "(stackable items = stackable shop)");
                }
            }

            case TYPE -> {
                // Left-click in TYPE: Cycle through SignTypes (only if not yet detected/locked)
                if (barterSign.isTypeDetected()) {
                    // Type is locked - cannot change
                    player.sendMessage(ChatColor.RED + "✗ Shop type is locked!");
                    player.sendMessage(ChatColor.GRAY + "Locked type: " + ChatColor.YELLOW + barterSign.getType().name());
                    player.sendMessage(ChatColor.GRAY + "Delete and recreate shop to change type");
                    return;
                }

                SignType currentType = barterSign.getType();
                SignType nextType = plugin.getTypeAvailabilityManager().getNextSignType(currentType);
                barterSign.setType(nextType);

                // Don't announce type changes to reduce spam - type is shown on sign
                logger.debug("Owner: TYPE cycling - " + currentType + " -> " + nextType);
            }

            case BOARD -> {
                // Left-click in BOARD: Show shop info
                player.sendMessage(ChatColor.GREEN + "Shop is active!");
                scheduleRevert(sign, barterSign);
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

    private void handleOwnerRightClick(Player player, Sign sign, BarterSign barterSign) {
        logger.debug("Processing owner interaction");
        cancelRevert(sign.getLocation());

        ShopMode currentMode = barterSign.getMode();

        // Right-click: Always cycle to next mode (consistent behavior)
        ShopMode nextMode = barterSign.getMode().getNextMode();
        barterSign.setMode(nextMode);
        logger.debug("Mode advanced: " + currentMode + " -> " + nextMode);

        // Mode messages moved to sign display - no chat spam

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
