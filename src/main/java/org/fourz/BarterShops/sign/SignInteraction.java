package org.fourz.BarterShops.sign;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.inspection.ShopInfoDisplayHelper;
import org.fourz.BarterShops.preferences.ShopPreferenceManager;
import org.fourz.BarterShops.shop.ShopMode;
import org.fourz.BarterShops.sign.SignType;
import org.fourz.BarterShops.trade.TradeEngine;
import org.fourz.BarterShops.trade.TradeSession;
import org.fourz.BarterShops.trade.TradeConfirmationGUI;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
    private final Map<String, Long> pendingTypeChanges = new ConcurrentHashMap<>(); // signId -> timestamp
    private final Map<Location, Long> lastPreviewToggleTime = new ConcurrentHashMap<>(); // Debounce preview toggle
    private final SignSessionManager sessionManager = new SignSessionManager();
    private static final long PREVIEW_TOGGLE_DEBOUNCE_MS = 150; // Ignore toggles within 150ms
    private static final long PURCHASE_DEBOUNCE_MS = STATUS_DISPLAY_TICKS * 50L; // Match feedback window (3s)

    public SignInteraction(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, CLASS_NAME);
    }

    public void handleLeftClick(Player player, Sign sign, BarterSign barterSign, PlayerInteractEvent event) {
        if (barterSign == null) return;

        // CUSTOMER LEFT-CLICK: Direct trade initiation
        if (barterSign.isCustomer(player)) {
            handleCustomerLeftClick(player, sign, barterSign, event);
            return;
        }

        // OWNER LEFT-CLICK: Configuration (requires permission)
        if (!player.hasPermission("bartershops.configure")) {
            logger.debug("Left-click ignored - player lacks configure permission");
            return;
        }

        if (!barterSign.getOwner().equals(player.getUniqueId())) {
            return; // Not owner
        }

        logger.debug("Owner left-click detected - mode: " + barterSign.getMode());
        // Don't cancel revert in DELETE or TYPE mode - let 10s countdown from right-click continue
        // Confirmation display is temporary (5s auto-clear), not a configuration interaction
        if (barterSign.getMode() != ShopMode.DELETE && barterSign.getMode() != ShopMode.TYPE) {
            cancelRevert(sign.getLocation());
        }

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
                    logger.info("[OFFERING] Setting to " + itemInHand.getType() + " (qty=" + itemInHand.getAmount() + ")");
                    barterSign.configureStackableShop(itemInHand, itemInHand.getAmount());

                    // UNIFIED: Refresh sign state (updates rules + displays sign immediately)
                    refreshSignState(sign, barterSign);

                    int ruleCount = barterSign.getShopContainerWrapper() != null ? barterSign.getShopContainerWrapper().getValidationRules().size() : 0;
                    logger.info("[VALIDATION] Sign state refreshed - Container has " + ruleCount + " RULES");
                    player.sendMessage(ChatColor.GREEN + "+ Offering set: " + itemInHand.getAmount() + "x " + itemInHand.getType().name());
                    player.sendMessage(ChatColor.GRAY + "Now set payment/price");

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
                            // Shift+L-Click: Decrement payment amount, or remove if at/below 0
                            int currentAmount = barterSign.getPaymentAmount(itemInHand.getType());
                            if (currentAmount <= 0) {
                                player.sendMessage(ChatColor.YELLOW + "Not in payment list");
                            } else {
                                int step = itemInHand.getAmount();
                                int newAmount = currentAmount - step;
                                if (newAmount <= 0) {
                                    // Decrement to zero: remove entirely
                                    barterSign.removePaymentOption(itemInHand.getType());
                                    refreshSignState(sign, barterSign);
                                    player.sendMessage(ChatColor.RED + "- Removed: " + itemInHand.getType().name());
                                } else {
                                    // Decrement: update with reduced amount
                                    barterSign.addPaymentOption(itemInHand, newAmount);
                                    refreshSignState(sign, barterSign);
                                    player.sendMessage(ChatColor.AQUA + "Payment: " + newAmount + "x " + itemInHand.getType().name() +
                                        ChatColor.GRAY + " (-" + step + ")");
                                }
                                barterSign.resetCustomerViewState(); // Reset pagination
                                // Save configuration to database
                                if (barterSign.getShopId() > 0) {
                                    plugin.getSignManager().saveSignConfiguration(barterSign);
                                }
                            }
                        } else {
                            // L-Click: Increment payment option (add if new, accumulate if existing)
                            int currentAmount = barterSign.getPaymentAmount(itemInHand.getType());
                            int step = itemInHand.getAmount();
                            int newAmount = currentAmount + step;
                            logger.info("[PAYMENT] " + (currentAmount > 0 ? "Updating" : "Adding") + " option " + itemInHand.getType() + " (" + currentAmount + " -> " + newAmount + ", step=" + step + ")");
                            barterSign.addPaymentOption(itemInHand, newAmount);

                            // UNIFIED: Refresh sign state (updates rules + displays sign immediately)
                            refreshSignState(sign, barterSign);

                            int ruleCount = barterSign.getShopContainerWrapper() != null ? barterSign.getShopContainerWrapper().getValidationRules().size() : 0;
                            logger.info("[VALIDATION] Sign state refreshed - Container has " + ruleCount + " RULES");
                            if (currentAmount > 0) {
                                player.sendMessage(ChatColor.AQUA + "Payment: " + newAmount + "x " + itemInHand.getType().name() +
                                    ChatColor.GRAY + " (+" + step + ")");
                            } else {
                                player.sendMessage(ChatColor.GREEN + "+ Payment added: " + newAmount + "x " + itemInHand.getType().name());
                            }
                            barterSign.resetCustomerViewState(); // Reset pagination

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

                            // UNIFIED: Refresh sign state (updates rules + displays sign immediately)
                            refreshSignState(sign, barterSign);

                            player.sendMessage(ChatColor.GREEN + "+ Currency set: " + itemInHand.getType().name());
                            player.sendMessage(ChatColor.GRAY + "L-Click to adjust (step = held amount)");

                            // Save configuration to database
                            if (barterSign.getShopId() > 0) {
                                plugin.getSignManager().saveSignConfiguration(barterSign);
                            }
                        } else if (currentPrice.getType() == itemInHand.getType()) {
                            // Adjust price amount with same currency; step = held stack size
                            int currentAmount = barterSign.getPriceAmount();
                            int step = itemInHand.getAmount();
                            int newAmount;

                            if (player.isSneaking()) {
                                // Shift+L-Click: decrement by hand amount
                                newAmount = Math.max(0, currentAmount - step);
                            } else {
                                // L-Click: increment by hand amount
                                newAmount = currentAmount + step;
                            }

                            barterSign.configurePrice(itemInHand, newAmount);

                            // UNIFIED: Refresh sign state (updates rules + displays sign immediately)
                            refreshSignState(sign, barterSign);

                            player.sendMessage(ChatColor.AQUA + "Price: " + newAmount + "x " + itemInHand.getType().name());

                            // Save configuration to database
                            if (barterSign.getShopId() > 0) {
                                plugin.getSignManager().saveSignConfiguration(barterSign);
                            }
                        } else {
                            // Different currency - confirm change
                            player.sendMessage(ChatColor.YELLOW + "! Currency change: " + currentPrice.getType().name() + " → " + itemInHand.getType().name());
                            barterSign.configurePrice(itemInHand, 1);

                            // UNIFIED: Refresh sign state (updates rules + displays sign immediately)
                            refreshSignState(sign, barterSign);

                            // Save configuration to database
                            if (barterSign.getShopId() > 0) {
                                plugin.getSignManager().saveSignConfiguration(barterSign);
                            }
                        }
                    }
                }

                // Reschedule auto-revert after interaction completes
                scheduleRevert(sign, barterSign);
            }

            case TYPE -> {
                // Left-click in TYPE: Two-step confirmation before cycling shop type (BARTER, BUY, SELL)
                // First click shows confirmation prompt; second click applies the change.
                // Mirrors the DELETE two-step pattern - 10s revert timer from right-click continues.
                SignType currentType = barterSign.getType();
                SignType nextType = plugin.getTypeAvailabilityManager().getNextSignType(currentType);

                if (currentType != nextType) {
                    String signId = barterSign.getId();

                    if (pendingTypeChanges.containsKey(signId)) {
                        // Second click - TYPE change confirmed
                        pendingTypeChanges.remove(signId);

                        barterSign.clearPaymentOptions();
                        barterSign.configurePrice(null, 0);
                        barterSign.resetCustomerViewState();
                        barterSign.setType(nextType);
                        player.sendMessage(ChatColor.YELLOW + "! Type changed: " + currentType + " → " + nextType);
                        player.sendMessage(ChatColor.GRAY + "Payment configuration cleared");

                        if (barterSign.getShopId() > 0) {
                            plugin.getSignManager().saveSignConfiguration(barterSign);
                        }
                        logger.debug("Owner: TYPE confirmed - " + currentType + " -> " + nextType);

                        SignDisplay.updateSign(sign, barterSign, false);
                        event.setCancelled(true);
                        return;
                    } else {
                        // First click - initiate confirmation prompt
                        pendingTypeChanges.put(signId, System.currentTimeMillis());
                        player.sendMessage(ChatColor.YELLOW + "⚠ Click AGAIN to confirm type change: " + currentType + " → " + nextType);
                        player.sendMessage(ChatColor.GRAY + "This will reset payment configuration");

                        SignDisplay.displayTypeConfirmation(sign, nextType);

                        // Schedule auto-clear of confirmation display (5s timeout)
                        final UUID confirmingPlayerUuid = player.getUniqueId();
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (pendingTypeChanges.containsKey(signId)) {
                                pendingTypeChanges.remove(signId);
                                Player confirmingPlayer = plugin.getServer().getPlayer(confirmingPlayerUuid);
                                if (confirmingPlayer != null) {
                                    confirmingPlayer.sendMessage(ChatColor.GRAY + "Type change confirmation expired");
                                }
                                // Revert sign display back to normal TYPE prompt, keep 10s timer running
                                if (barterSign.getMode() == ShopMode.TYPE) {
                                    SignDisplay.updateSign(sign, barterSign, false);
                                }
                            }
                        }, DELETE_CONFIRMATION_TIMEOUT_TICKS);
                        // NOTE: Do NOT reschedule the 10s auto-revert - original timer from right-click continues
                        event.setCancelled(true);
                        return;
                    }
                }
                // No-op if only one type available
            }

            case BOARD -> {
                // Left-click in BOARD: Owner quantity adjustment
                handleOwnerBoardClick(player, sign, barterSign, event);
                SignDisplay.updateSign(sign, barterSign);
                event.setCancelled(true);
                return;
            }

            case DELETE -> {
                // Left-click in DELETE: Two-step confirmation with integrated auto-revert
                String signId = barterSign.getId();

                // Check if this is a confirmation click (pending deletion exists)
                if (pendingDeletions.containsKey(signId)) {
                    // Second click - DELETE confirmed
                    player.sendMessage(ChatColor.RED + "§c✓ Shop deleted");

                    // Cancel auto-revert timer since we're deleting
                    cancelRevert(sign.getLocation());

                    // Proceed with deletion
                    deleteShopAndSign(player, sign, barterSign);
                    pendingDeletions.remove(signId);
                    return;
                }

                // First click - initiate confirmation prompt
                pendingDeletions.put(signId, System.currentTimeMillis());
                player.sendMessage(ChatColor.RED + "§c⚠ Click AGAIN to confirm deletion");
                player.sendMessage(ChatColor.GRAY + "Chest items will be preserved");

                // Show confirmation prompt on sign
                SignDisplay.displayDeleteConfirmation(sign);

                // Schedule auto-clear of confirmation display (but NOT the DELETE mode)
                // After 5 seconds, revert to normal DELETE display if not confirmed
                final UUID deletingPlayerUuid = player.getUniqueId();
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (pendingDeletions.containsKey(signId)) {
                        // Confirmation timed out - user didn't click twice in 5 seconds
                        pendingDeletions.remove(signId);
                        Player deletingPlayer = plugin.getServer().getPlayer(deletingPlayerUuid);
                        if (deletingPlayer != null) {
                            deletingPlayer.sendMessage(ChatColor.GRAY + "Deletion confirmation expired");
                        }
                        // Revert sign display back to normal DELETE prompt, keep 10s timer running
                        if (barterSign.getMode() == ShopMode.DELETE) {
                            SignDisplay.updateSign(sign, barterSign, false);
                        }
                    }
                }, DELETE_CONFIRMATION_TIMEOUT_TICKS);
                // NOTE: Do NOT reschedule the 10s auto-revert here - the original timer
                // from right-click continues in background. This 5s timer is ONLY for
                // clearing the confirmation overlay, not resetting the inactivity counter.
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

    /**
     * Handles customer left-click: Initiates trade with current payment page item.
     * Only works in BOARD mode. Validates held item matches current payment option.
     */
    private void handleCustomerLeftClick(Player player, Sign sign, BarterSign barterSign, PlayerInteractEvent event) {
        if (barterSign.getMode() != ShopMode.BOARD) {
            logger.debug("Customer tried to interact with non-BOARD mode shop");
            return;
        }

        logger.debug("Customer left-click detected - mode: BOARD");

        // Debounce check before any feedback: prevents "Hold payment item" from overwriting
        // the "Purchased" message during the feedback window after payment is consumed.
        if (isPurchaseDebounceActive(player.getUniqueId(), barterSign)) {
            return; // Silent — success message still visible
        }

        ItemStack itemInHand = event.getItem();
        if (itemInHand == null || itemInHand.getType().isAir()) {
            showTemporaryStatus(sign, barterSign, "\u00A7eHold payment", "\u00A7eitem");
            return;
        }

        SignType shopType = barterSign.getType();

        if (shopType == SignType.BARTER) {
            // BARTER: Validate held item is an accepted payment
            List<ItemStack> payments = barterSign.getAcceptedPayments();
            if (payments.isEmpty()) {
                showTemporaryStatus(sign, barterSign, "\u00A7cNo payment", "\u00A7coptions");
                return;
            }

            if (!barterSign.isPaymentAccepted(itemInHand)) {
                showTemporaryStatus(sign, barterSign, "\u00A7cHold payment", "\u00A7cR-click to cycle");
                return;
            }

            recordPurchaseTime(player.getUniqueId(), barterSign);
            processTrade(player, sign, barterSign);
        } else {
            // BUY/SELL: Validate held item matches price item
            ItemStack priceItem = barterSign.getPriceItem();
            if (priceItem == null || priceItem.getType() != itemInHand.getType()) {
                showTemporaryStatus(sign, barterSign, "\u00A7cHold payment", "\u00A7citem");
                return;
            }

            recordPurchaseTime(player.getUniqueId(), barterSign);
            processTrade(player, sign, barterSign);
        }

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
        int step = itemInHand.getAmount();
        int newQty;

        if (player.isSneaking()) {
            // Shift+L-Click: decrement by hand amount
            newQty = Math.max(1, currentQty - step);
        } else {
            // L-Click: increment by hand amount
            newQty = currentQty + step;
        }

        offering.setAmount(newQty);
        barterSign.configureStackableShop(offering, newQty);
        barterSign.updateValidationRules(); // CRITICAL: Update validation rules after quantity change

        player.sendMessage(ChatColor.AQUA + "Quantity: " + newQty + "x " + offering.getType().name() +
            ChatColor.GRAY + " (±" + step + ")");

        // Save configuration to database
        if (barterSign.getShopId() > 0) {
            plugin.getSignManager().saveSignConfiguration(barterSign);
        }

        // Update display preserving owner preview mode if active
        SignDisplay.updateSign(sign, barterSign, barterSign.isOwnerPreviewMode());
    }

    private void handleOwnerRightClick(Player player, Sign sign, BarterSign barterSign) {
        logger.debug("Processing owner interaction");
        cancelRevert(sign.getLocation());

        ShopMode currentMode = barterSign.getMode();

        // Sneak+Right-click: Cycle through 3 display panels (Panel 1→2→3→1)
        if (player.isSneaking()) {
            handleOwnerShiftClick(player, sign, barterSign);
            return; // Don't cycle modes
        }

        // If in customer preview mode, simulate customer page cycling instead of mode changes
        if (barterSign.isOwnerPreviewMode()) {
            if (barterSign.getType() == SignType.BARTER) {
                List<ItemStack> payments = barterSign.getAcceptedPayments();
                if (payments.size() > 1) {
                    barterSign.incrementPaymentPage();
                    SignDisplay.updateSign(sign, barterSign, true); // isCustomerView = true
                }
            }
            // Single-payment or non-BARTER: nothing to cycle — stay in preview
            return;
        }

        // Right-click: Always cycle to next mode (consistent behavior)
        ShopMode nextMode = barterSign.getMode().getNextMode();
        barterSign.setMode(nextMode);
        logger.debug("Mode advanced: " + currentMode + " -> " + nextMode);

        // Mode messages moved to sign display - no chat spam

        // Schedule auto-revert for all non-BOARD modes (10 second timeout)
        // After 10 seconds of inactivity, sign reverts to BOARD view
        if (nextMode != ShopMode.BOARD) {
            scheduleRevert(sign, barterSign);
            logger.debug("Auto-revert scheduled for mode: " + nextMode);
        }

        // Update display: if owner is in preview mode, show customer view; otherwise owner view
        SignDisplay.updateSign(sign, barterSign, barterSign.isOwnerPreviewMode());
    }

    /**
     * Handles shift+right-click panel cycling.
     * Cycles: Panel 1 (Normal) → Panel 2 (Customer Preview) → Panel 3 (Shop Info) → Panel 1
     */
    private void handleOwnerShiftClick(Player player, Sign sign, BarterSign barterSign) {
        ShopPreferenceManager prefs = plugin.getPreferenceManager();
        SignSession session = sessionManager.getOrCreate(player.getUniqueId(), sign.getLocation().toString());

        int currentPanel = session.getShiftClickPanel();
        int nextPanel = currentPanel;

        if (currentPanel == 1) {
            // Panel 1 → 2: Show customer preview mode
            nextPanel = 2;
            barterSign.setOwnerPreviewMode(true);
            barterSign.setCurrentPaymentPage(0); // Always start from first customer page
            SignDisplay.updateSign(sign, barterSign, true);
            player.sendMessage("§7[Preview] §fViewing as customer. Sneak+R-click to exit.");
            logger.debug("Shifted to Panel 2: Customer preview for " + player.getName());
        }
        else if (currentPanel == 2) {
            // Panel 2 → 3: Check if player can view info (permission + preference)
            boolean canViewInfo = prefs.isInfoDisplayEnabled(player.getUniqueId()) &&
                (barterSign.getOwner().equals(player.getUniqueId()) ||
                 player.hasPermission("bartershops.admin") ||
                 player.hasPermission("bartershops.command.info"));

            if (canViewInfo) {
                // Show shop info and advance to Panel 3 (info display shows messages)
                nextPanel = 3;
                barterSign.setOwnerPreviewMode(false); // Exit preview mode when viewing info
                ShopInfoDisplayHelper helper = plugin.getShopInfoDisplayHelper();
                helper.displayShopInfo(player, barterSign, sign.getLocation(), ShopInfoDisplayHelper.InfoDisplayContext.SHIFT_CLICK);
                logger.debug("Shifted to Panel 3: Shop info for " + player.getName());
            } else {
                // Skip Panel 3, go back to Panel 1
                nextPanel = 1;
                barterSign.setOwnerPreviewMode(false);
                SignDisplay.updateSign(sign, barterSign, false);
                player.sendMessage("§7[Preview] §fExited customer view.");
                logger.debug("Shifted to Panel 1: Normal (Panel 3 skipped - no permission or disabled)");
            }
        }
        else if (currentPanel == 3) {
            // Panel 3 → 1: Back to normal
            nextPanel = 1;
            barterSign.setOwnerPreviewMode(false);
            SignDisplay.updateSign(sign, barterSign, false);
            // No message needed - Panel 3 (shop info) already shows messages
            logger.debug("Shifted to Panel 1: Normal for " + player.getName());
        }

        // Store next panel state
        session.setShiftClickPanel(nextPanel);
    }

    /**
     * Handles customer shift+right-click: Toggles shop info display in chat.
     * Toggle is per-session (reset on server restart).
     */
    private void handleCustomerShiftClick(Player player, Sign sign, BarterSign barterSign) {
        SignSession session = sessionManager.getOrCreate(player.getUniqueId(), sign.getLocation().toString());
        if (session.isCustomerInfoToggled()) {
            session.setCustomerInfoToggled(false);
            player.sendMessage(ChatColor.GRAY + "[BarterShops] Shop info view off.");
            return;
        }
        session.setCustomerInfoToggled(true);
        ShopInfoDisplayHelper helper = plugin.getShopInfoDisplayHelper();
        helper.displayShopInfoForCustomer(player, barterSign, sign.getLocation());
    }

    private void handleCustomerRightClick(Player player, Sign sign, BarterSign barterSign) {
        logger.debug("Processing customer interaction");

        if (barterSign.getMode() != ShopMode.BOARD) {
            logger.debug("Customer tried to interact with non-BOARD mode shop");
            return;
        }

        // Shift+Right-click: Toggle shop info display
        if (player.isSneaking()) {
            handleCustomerShiftClick(player, sign, barterSign);
            return;
        }

        // BARTER shops with multiple payments: always cycle — purchase is left-click only
        SignType shopType = barterSign.getType();
        if (shopType == SignType.BARTER) {
            List<ItemStack> payments = barterSign.getAcceptedPayments();
            if (payments.size() > 1) {
                barterSign.incrementPaymentPage();
                SignDisplay.updateSign(sign, barterSign, true); // isCustomerView = true
                scheduleCustomerPageRevert(sign, barterSign);   // auto-revert after 10s idle
                return;
            }
        }

        // Single-payment or non-BARTER: right-click does nothing — purchase is left-click only
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

        // Get shop container for stock verification (wrapper-aware)
        Inventory stockInv = null;
        if (barterSign.getShopContainerWrapper() != null) {
            stockInv = barterSign.getShopContainerWrapper().getInventory();
        } else {
            Container shopContainer = barterSign.getShopContainer();
            if (shopContainer == null) shopContainer = barterSign.getContainer();
            if (shopContainer != null) stockInv = shopContainer.getInventory();
        }

        if (stockInv == null) {
            showTemporaryStatus(sign, barterSign, "\u00A7cNo container", "\u00A7cfound");
            return;
        }

        // Verify stock availability
        int availableStock = 0;
        for (ItemStack item : stockInv.getContents()) {
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

        // Both validations passed - check auto-exchange preference
        if (plugin.getAutoExchangeHandler() != null &&
            plugin.getAutoExchangeHandler().isAutoExchangeEnabled(player)) {
            // Auto-exchange ON: Execute instant purchase
            executeDirectPurchase(player, sign, barterSign, offering, paymentItem, paymentAmount);
        } else {
            // Auto-exchange OFF: Open GUI confirmation (traditional flow)
            executeConfiguredTrade(player, sign, barterSign, tradeEngine, offering, paymentItem, paymentAmount);
        }
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

        // Get shop container (wrapper-aware)
        Inventory nsStockInv = null;
        if (barterSign.getShopContainerWrapper() != null) {
            nsStockInv = barterSign.getShopContainerWrapper().getInventory();
        } else {
            Container shopContainer = barterSign.getShopContainer();
            if (shopContainer == null) shopContainer = barterSign.getContainer();
            if (shopContainer != null) nsStockInv = shopContainer.getInventory();
        }

        if (nsStockInv == null) {
            showTemporaryStatus(sign, barterSign, "\u00A7cNo container", "\u00A7cfound");
            logger.debug("Trade rejected - no shop container");
            return;
        }

        // Get first non-air item from chest (any item)
        ItemStack offering = null;
        for (ItemStack item : nsStockInv.getContents()) {
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
                                // Trade complete feedback removed per user request
                                // (GUI confirmation already shows result)
                                logger.info("Trade completed: " + result.transactionId());
                            } else {
                                // Trade failed feedback removed per user request
                                // (GUI confirmation already shows error)
                                logger.debug("Trade failed: " + result.message());
                            }
                        });
                    });
            },
            // On cancel
            (cancelledSession) -> {
                logger.debug("Trade cancelled for session: " + cancelledSession.getSessionId());
                tradeEngine.cancelSession(cancelledSession.getSessionId());
                // Trade cancelled feedback removed per user request
                // (GUI confirmation already shows cancellation)
            }
        );
    }

    /**
     * Executes a direct purchase without GUI confirmation (auto-exchange enabled).
     * Triggered by left-click with payment in hand when auto-exchange preference is ON.
     *
     * @param player The customer purchasing
     * @param sign The shop sign
     * @param barterSign Shop sign data
     * @param offering Item being offered
     * @param payment Payment item
     * @param paymentAmount Payment quantity
     */
    private void executeDirectPurchase(Player player, Sign sign, BarterSign barterSign,
                                      ItemStack offering, ItemStack payment, int paymentAmount) {
        logger.debug("Executing direct purchase for " + player.getName());

        TradeEngine tradeEngine = plugin.getTradeEngine();
        if (tradeEngine == null) {
            showTemporaryStatus(sign, barterSign, "\u00A7cTrade", "\u00A7cunavailable");
            return;
        }

        // Execute direct trade via AutoExchangeHandler pattern
        tradeEngine.executeDirectTrade(
            player,
            barterSign,
            offering,
            offering.getAmount(),
            payment,
            paymentAmount,
            org.fourz.BarterShops.trade.TradeSource.INSTANT_PURCHASE
        ).thenAccept(result -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (result.success()) {
                    // Brief success feedback (no spam)
                    showTemporaryStatus(sign, barterSign, "\u00A7aPurchased", "\u00A7a" + offering.getAmount() + "x");
                    logger.debug("Direct purchase completed: " + result.transactionId());
                } else {
                    // Show error
                    showTemporaryStatus(sign, barterSign, "\u00A7cFailed", "\u00A7c" + result.message());
                    logger.debug("Direct purchase failed: " + result.message());
                }
            });
        }).exceptionally(ex -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                showTemporaryStatus(sign, barterSign, "\u00A7cError", "\u00A7cTrade failed");
                logger.error("Direct purchase error: " + ex.getMessage());
            });
            return null;
        });
    }

    // ========== Temporary Status Display ==========

    private void showTemporaryStatus(Sign sign, BarterSign barterSign, String line1, String line2) {
        SignDisplay.displayTemporaryMessage(sign, line1, line2);
        scheduleRevert(sign, barterSign, STATUS_DISPLAY_TICKS);
    }

    // ========== Sign State Refresh (Unified) ==========

    /**
     * UNIFIED METHOD: Refresh sign state immediately after configuration changes.
     * Ensures consistent state refresh across all configuration paths (SETUP, TYPE, etc).
     *
     * Always does:
     * 1. Update validation rules based on current shop configuration
     * 2. Refresh sign display immediately (no delay)
     *
     * @param sign The sign to refresh
     * @param barterSign The sign data
     */
    private void refreshSignState(Sign sign, BarterSign barterSign) {
        // Step 1: Update validation rules based on current shop configuration
        barterSign.updateValidationRules();

        // Step 2: Refresh sign display immediately based on current mode
        // This ensures sign reflects the updated state right away
        if (barterSign.getMode() == ShopMode.SETUP) {
            // In SETUP: Always show owner view (no preview mode in SETUP)
            SignDisplay.updateSign(sign, barterSign, false);
        } else if (barterSign.getMode() == ShopMode.BOARD) {
            // In BOARD: Respect owner's preview mode preference
            SignDisplay.updateSign(sign, barterSign, barterSign.isOwnerPreviewMode());
        } else {
            // In TYPE/HELP/DELETE: Show owner view
            SignDisplay.updateSign(sign, barterSign, false);
        }

        logger.debug("Sign state refreshed: Rules updated + Display synchronized");
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
                // Auto-revert: Transitioning from non-BOARD mode (SETUP/TYPE/DELETE/HELP) back to BOARD

                // Clean up pending confirmation state if reverting from DELETE or TYPE mode
                String signId = barterSign.getId();
                if (barterSign.getMode() == ShopMode.DELETE && pendingDeletions.containsKey(signId)) {
                    pendingDeletions.remove(signId);
                    logger.debug("Auto-revert from DELETE: Cleared pending confirmation");
                }
                if (barterSign.getMode() == ShopMode.TYPE && pendingTypeChanges.containsKey(signId)) {
                    pendingTypeChanges.remove(signId);
                    logger.debug("Auto-revert from TYPE: Cleared pending type change");
                }

                // Reset mode and UI state
                barterSign.setMode(ShopMode.BOARD);
                barterSign.resetCustomerViewState(); // Clear preview mode and pagination state

                // FIX: Get fresh sign state from location (captured sign reference may be stale)
                if (loc.getBlock().getState() instanceof Sign freshSign) {
                    SignDisplay.updateSign(freshSign, barterSign, false); // Explicit: show owner view
                    logger.debug("Auto-revert completed: Returned to BOARD mode, sign refreshed");
                } else {
                    logger.debug("Auto-revert skipped display update: Sign block no longer exists at " + loc);
                }

                activeRevertTasks.remove(loc);
            }
        }.runTaskLater(plugin, delayTicks);

        activeRevertTasks.put(loc, task);
    }

    /**
     * Schedules revert of customer payment page back to page 0 after idle timeout.
     * Each right-click resets the 10s idle timer via cancelRevert() + reschedule.
     */
    private void scheduleCustomerPageRevert(Sign sign, BarterSign barterSign) {
        Location loc = sign.getLocation();
        if (loc == null) return;
        cancelRevert(loc); // Reset idle timer on each click

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                barterSign.setCurrentPaymentPage(0);
                if (loc.getBlock().getState() instanceof Sign freshSign) {
                    SignDisplay.updateSign(freshSign, barterSign, true); // customer view
                    logger.debug("Customer page auto-revert: reset to page 0");
                }
                activeRevertTasks.remove(loc);
            }
        }.runTaskLater(plugin, REVERT_DELAY_TICKS);

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
                                // Remove from SignManager cache FIRST (prevents orphaned shops)
                                plugin.getSignManager().removeBarterSign(signLocation);

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

    private static String formatItemName(ItemStack item) {
        return SignDisplay.formatItemName(item);
    }

    private boolean isPurchaseDebounceActive(UUID playerId, BarterSign barterSign) {
        SignSession session = sessionManager.get(playerId, barterSign.getSignLocation().toString());
        if (session == null) return false;
        long last = session.getLastPurchaseTime();
        return last > 0 && (System.currentTimeMillis() - last) < PURCHASE_DEBOUNCE_MS;
    }

    private void recordPurchaseTime(UUID playerId, BarterSign barterSign) {
        sessionManager.getOrCreate(playerId, barterSign.getSignLocation().toString())
            .setLastPurchaseTime(System.currentTimeMillis());
    }

    /**
     * Removes all per-player session state for a player who has disconnected.
     * Called by SignManager.onPlayerQuit() to prevent unbounded map growth.
     */
    public void cleanupPlayer(UUID playerUuid) {
        sessionManager.cleanupPlayer(playerUuid);
    }

    public void cleanup() {
        activeRevertTasks.values().forEach(BukkitTask::cancel);
        activeRevertTasks.clear();
        pendingDeletions.clear();
        pendingTypeChanges.clear();
        sessionManager.cleanup();
    }
}
