package org.fourz.BarterShops.container.listener;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.container.ShopContainer;
import org.fourz.BarterShops.container.factory.ShopContainerFactory;
import org.fourz.BarterShops.container.validation.ValidationResult;
import org.fourz.BarterShops.notification.NotificationManager;
import org.fourz.BarterShops.notification.NotificationType;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.BarterShops.sign.SignDisplay;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Real-time inventory validation listener for shop containers.
 * Prevents invalid items from being placed in shop containers across ALL modes.
 * Fixes bug-36: Type locking validation broken.
 *
 * Listens for:
 * - InventoryClickEvent: Player clicks in inventory
 * - InventoryMoveItemEvent: Hoppers, droppers, etc move items
 * - InventoryDragEvent: Players drag items across slots
 */
public class InventoryValidationListener implements Listener {
    private final Map<UUID, ShopContainer> shopContainers = new HashMap<>();
    private final Set<String> pendingDeposits = new HashSet<>();
    private final long creationTime = System.currentTimeMillis();
    private final BarterShops plugin;
    private final LogManager logger;

    public InventoryValidationListener(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "InventoryValidation");
    }

    /**
     * Registers a shop container for real-time validation.
     *
     * @param shopContainer The container to monitor
     */
    public void registerContainer(ShopContainer shopContainer) {
        shopContainers.put(shopContainer.getShopId(), shopContainer);
        logger.debug("Registered shop container: " + shopContainer.getShopId() +
                   " at " + shopContainer.getLocation() +
                   " with " + shopContainer.getValidationRules().size() + " validation rules");
    }

    /**
     * Unregisters a shop container from validation.
     *
     * @param shopId The ID of the shop to stop monitoring
     */
    public void unregisterContainer(UUID shopId) {
        shopContainers.remove(shopId);
    }

    /**
     * Gets the number of monitored containers.
     */
    public int getMonitoredContainerCount() {
        return shopContainers.size();
    }

    // ========== Type Detection (merged from deprecated SignManager.startChestValidationTask) ==========

    /**
     * Handles auto-detection of shop type (stackable/unstackable) when the first item
     * is placed into a shop container that hasn't been typed yet.
     *
     * Merged from deprecated SignManager.startChestValidationTask() logic.
     * Triggers on first item placement, then locks the type and rebuilds validation rules.
     *
     * @param shopContainer The shop container receiving the item
     * @param item The item being placed
     * @param player The player placing the item (for notifications)
     * @return true if type was detected (caller should skip normal validation since rules just changed)
     */
    private boolean handleTypeDetection(ShopContainer shopContainer, ItemStack item, Player player) {
        BarterSign barterSign = shopContainer.getBarterSign();
        if (barterSign == null || barterSign.isTypeDetected()) {
            return false;
        }

        // Detect type from item stackability
        boolean itemIsStackable = BarterSign.isItemStackable(item);
        barterSign.setStackable(itemIsStackable);
        barterSign.setTypeDetected(true);

        logger.info("Type detection triggered: " + item.getType() +
                " → stackable=" + itemIsStackable + " for shop " + barterSign.getShopId());

        // Rebuild validation rules on the BarterSign (updates getAllowedChestTypes)
        barterSign.updateValidationRules();

        // Rebuild ShopContainer with correct validation rules
        rebuildShopContainer(shopContainer, barterSign);

        // Persist configuration to database
        plugin.getSignManager().saveSignConfiguration(barterSign);

        // Update sign display to reflect the detected type
        refreshSignDisplay(barterSign);

        // Notify owner
        if (player != null) {
            String typeLabel = itemIsStackable ? "Stackable" : "Unstackable";
            player.sendMessage(ChatColor.GREEN + "+ Shop type detected: " + ChatColor.WHITE + typeLabel);
            player.sendMessage(ChatColor.GRAY + "Type is now locked. Use DELETE mode to reset.");
        }

        return true;
    }

    /**
     * Rebuilds the ShopContainer validation rules after type detection.
     * Re-registers the container with updated rules.
     */
    private void rebuildShopContainer(ShopContainer oldContainer, BarterSign barterSign) {
        Container container = barterSign.getShopContainer();
        if (container == null) {
            container = barterSign.getContainer();
        }
        if (container == null) {
            logger.warning("Cannot rebuild ShopContainer - no container found for shop " + barterSign.getShopId());
            return;
        }

        UUID shopUuid = oldContainer.getShopId();

        // Create new container with correct rules based on detected type
        ShopContainer newContainer;
        if (barterSign.isStackable()) {
            var allowedTypes = barterSign.getAllowedChestTypes();
            if (!allowedTypes.isEmpty()) {
                newContainer = ShopContainerFactory.createStackableMultiTypeLocked(
                        container, shopUuid, allowedTypes);
            } else {
                newContainer = ShopContainerFactory.createContainer(container, shopUuid);
            }
        } else {
            newContainer = ShopContainerFactory.createUnstackableOnly(container, shopUuid);
        }

        // Set bidirectional reference
        newContainer.setBarterSign(barterSign);

        // Also update wrapper on the BarterSign so trade engine uses the right container
        barterSign.setShopContainerWrapper(newContainer);

        // Re-register in our map
        shopContainers.put(shopUuid, newContainer);

        logger.debug("Rebuilt ShopContainer for shop " + barterSign.getShopId() +
                ": rules=" + newContainer.getValidationRules().size());
    }

    /**
     * Refreshes the sign display after type detection.
     * Finds the Sign block from the BarterSign's sign location.
     */
    private void refreshSignDisplay(BarterSign barterSign) {
        try {
            org.bukkit.Location signLoc = barterSign.getSignLocation();
            if (signLoc != null && signLoc.getBlock().getState() instanceof Sign sign) {
                SignDisplay.updateSign(sign, barterSign, false);
            }
        } catch (Exception e) {
            logger.debug("Could not refresh sign display after type detection: " + e.getMessage());
        }
    }

    // ========== Event Handlers ==========

    /**
     * Handles player clicking in shop inventory.
     * Prevents placing invalid items.
     *
     * Handles TWO cases:
     * 1. Direct click: Player clicks cursor item into shop container slot
     * 2. Shift-click: Player shift-clicks item in player inventory → moves to shop container
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        ShopContainer shopContainer = getShopContainerFromEvent(event);
        if (shopContainer == null) {
            logger.debug("InventoryClickEvent: No shop container found");
            return;
        }

        // SlotType.OUTSIDE means the player clicked outside the inventory window (rawSlot = -999).
        // The existing rawSlot >= inventory.getSize() guard does NOT catch this because -999 < any
        // positive size. Ignore these clicks entirely — they drop the cursor item as normal.
        if (event.getSlotType() == InventoryType.SlotType.OUTSIDE) {
            return;
        }

        Player player = (event.getWhoClicked() instanceof Player) ? (Player) event.getWhoClicked() : null;
        logger.debug("InventoryClickEvent: Shop container found at " + shopContainer.getLocation() +
                    ", action=" + event.getAction() + ", slot=" + event.getRawSlot());

        BarterSign barterSign = shopContainer.getBarterSign();

        // SECURITY: Block customers from taking items directly from the shop chest.
        // Only the offering item may be taken, and only when auto-exchange is enabled.
        // All other removals must go through the sign trade flow.
        if (barterSign != null && player != null
                && !barterSign.getOwner().equals(player.getUniqueId())
                && isRemovingFromShopSlot(event)) {
            // SWAP_WITH_CURSOR atomically removes the clicked slot item into the cursor AND places
            // the cursor item into the slot. For customers this causes duplication: the offering
            // moves to the cursor via the swap, then onCustomerDepositPayment sees the cursor
            // (payment) and schedules another trade that gives the offering a second time.
            // Block the action entirely — customers must deposit into an empty slot.
            if (event.getAction() == InventoryAction.SWAP_WITH_CURSOR) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "x Place payment in an empty slot to trade.");
                return;
            }

            ItemStack shopItem = event.getCurrentItem();
            ItemStack offering = barterSign.getItemOffering();
            boolean isOffering = offering != null && shopItem != null
                    && !shopItem.getType().isAir() && shopItem.isSimilar(offering);
            org.fourz.BarterShops.trade.AutoExchangeHandler autoExchange = plugin.getAutoExchangeHandler();
            boolean autoExchangeActive = autoExchange != null && autoExchange.isAutoExchangeEnabled(player);

            if (!isOffering || !autoExchangeActive) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "x Use the sign to trade.");
                return;
            }
            // Offering item + auto-exchange enabled: pass through to MONITOR handler
            return;
        }

        // CASE 1: SHIFT-CLICK from player inventory → moves item TO shop container
        // Only validate when clicked slot is in PLAYER inventory (item going INTO shop)
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            // Check if click is in player inventory (moving item TO shop)
            if (event.getRawSlot() < event.getInventory().getSize()) {
                // Owner shift-clicking from shop slot (removal) - allowed
                logger.debug("Shift-click from shop container (removal) - allowed for owner");
                return;
            }

            // Clicked slot is in player inventory = moving item INTO shop (validate)
            ItemStack movingItem = event.getCurrentItem();
            if (movingItem == null || movingItem.getType() == Material.AIR) {
                logger.debug("Shift-click from player inventory but slot is empty");
                return;
            }

            // Type detection for shift-click into shop
            if (handleTypeDetection(shopContainer, movingItem, player)) {
                return; // Type just detected, allow item
            }

            // Validate the item being shift-clicked into the container
            ValidationResult result = shopContainer.validateItemForUser(movingItem, player);
            if (!result.isValid()) {
                logger.warning("Type lock validation FAILED - " + result.reason() +
                              ". Item: " + movingItem.getType());

                event.setCancelled(true);

                if (player != null) {
                    sendValidationError(player, result.reason());
                }
            } else {
                logger.debug("Type lock validation PASSED for " + movingItem.getType());
                // Stock guard: block customers from depositing payment when shop is out of stock.
                // Must run at HIGH priority - setCancelled(true) at MONITOR is unreliable in Paper 1.21.
                if (barterSign != null && player != null
                        && !barterSign.getOwner().equals(player.getUniqueId())
                        && barterSign.isPaymentAccepted(movingItem)
                        && isOutOfStock(barterSign, event.getInventory())) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "x Shop is out of stock");
                    return;
                }
            }
            return; // Handled shift-click case
        }

        // CASE 2: Direct click in container - player places cursor item into shop slot
        if (event.getRawSlot() >= event.getInventory().getSize()) {
            // Click is in player inventory, not shop container (and not a shift-click)
            logger.debug("InventoryClickEvent: Click is in player inventory (slot " + event.getRawSlot() + "), not shop container");
            return;
        }

        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType() == Material.AIR) {
            logger.debug("InventoryClickEvent: Cursor is empty");
            return; // Empty cursor
        }

        // Type detection: If shop hasn't been typed yet, detect from first item placed
        if (handleTypeDetection(shopContainer, cursor, player)) {
            // Type was just detected and rules rebuilt - allow the item through
            // (it was used to determine the type, so it's valid by definition)
            return;
        }

        logger.info("InventoryClickEvent: Player " + (player != null ? player.getName() : "?") +
                  " clicking with " + cursor.getType() + " (qty=" + cursor.getAmount() + ") at slot " + event.getRawSlot());

        // UNIFIED: Validate with user context (owner vs customer)
        ValidationResult result = shopContainer.validateItemForUser(cursor, player);
        if (!result.isValid()) {
            logger.warning("Type lock validation FAILED - " + result.reason() +
                          ". Item: " + cursor.getType());

            // FIX: Cancel event only - Bukkit keeps item in cursor (no manual drop needed)
            // Manual dropping caused item duplication (cursor + dropped)
            event.setCancelled(true);

            // Send error notification to player
            if (player != null) {
                sendValidationError(player, result.reason());
            }
        } else {
            logger.debug("Type lock validation PASSED for " + cursor.getType());
            // Stock guard: block customers from depositing payment when shop is out of stock.
            // Must run at HIGH priority - setCancelled(true) at MONITOR is unreliable in Paper 1.21.
            if (barterSign != null && player != null
                    && !barterSign.getOwner().equals(player.getUniqueId())
                    && barterSign.isPaymentAccepted(cursor)
                    && isOutOfStock(barterSign, event.getInventory())) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "x Shop is out of stock");
            }
        }
    }

    /**
     * Handles customer depositing payment in shop chest (deposit auto-exchange).
     * Uses MONITOR priority to detect items AFTER they are placed.
     * Automatically exchanges payment for offering if auto-exchange is enabled.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCustomerDepositPayment(InventoryClickEvent event) {
        ShopContainer shopContainer = getShopContainerFromEvent(event);
        if (shopContainer == null) return;

        Player player = (event.getWhoClicked() instanceof Player) ? (Player) event.getWhoClicked() : null;
        if (player == null) return;

        // Outside-window clicks (rawSlot = -999) are not chest interactions.
        // The rawSlot >= inventory.getSize() guard below does NOT catch this because -999 < any size.
        if (event.getSlotType() == InventoryType.SlotType.OUTSIDE) return;

        // SWAP_WITH_CURSOR is blocked at HIGH priority. Guard here as a safety net so we never
        // schedule a deposit trade for a swap action that atomically took the offering already.
        if (event.getAction() == InventoryAction.SWAP_WITH_CURSOR
                && event.getRawSlot() < event.getInventory().getSize()) {
            return;
        }

        BarterSign barterSign = shopContainer.getBarterSign();
        if (barterSign == null) return;

        // Owner restocking → not a purchase
        if (barterSign.getOwner().equals(player.getUniqueId())) {
            return;
        }

        // Check if player is placing item INTO shop container (not taking)
        if (event.getRawSlot() >= event.getInventory().getSize()) {
            // Click in player inventory - could be shift-click to shop
            if (event.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                return; // Not moving to shop
            }
        }

        // Get item being deposited — amount must reflect what was ACTUALLY placed, not the
        // full cursor. For PLACE_SOME, the cursor retains the items that didn't fit, so
        // cursor.getAmount() > actual deposited. This caused a "every other time" duplication
        // bug: trade 1 leaves payment in chest; trade 2 triggers PLACE_SOME (slot partially
        // occupied), overcounts cursor as the deposit amount, and gives 2× offerings.
        ItemStack cursorItem = event.getCursor();
        ItemStack depositedItem;
        if (cursorItem == null || cursorItem.getType() == Material.AIR) {
            // Shift-click (MOVE_TO_OTHER_INVENTORY): no cursor; getCurrentItem is the moved stack.
            // Guard 1: skip no-cursor actions that are NOT a shift-click move.
            if (event.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY) return;
            // Guard 2: only handle player-inventory → chest direction.
            // Chest-side rawSlot means the player is shift-clicking FROM the chest (taking),
            // not depositing — that path belongs to onCustomerTakeOffering.
            if (event.getRawSlot() < event.getInventory().getSize()) return;

            ItemStack movingStack = event.getCurrentItem();
            if (movingStack == null || movingStack.getType() == Material.AIR) return;

            // CRITICAL FIX (bug-BS-75): getCurrentItem().getAmount() is the FULL player stack,
            // NOT the amount actually deposited. When the chest already holds accumulated payment
            // items from a prior trade, only (availableSpace) items can fit — but the full stack
            // amount was previously used for increments, causing 2× offering delivery on every
            // other trade. Compute real available chest space to get the actual deposited count.
            int movingAmount = movingStack.getAmount();
            int availableSpace = 0;
            for (ItemStack slotItem : event.getInventory().getContents()) {
                if (slotItem == null || slotItem.getType() == Material.AIR) {
                    availableSpace += movingStack.getMaxStackSize();
                } else if (slotItem.isSimilar(movingStack)) {
                    availableSpace += movingStack.getMaxStackSize() - slotItem.getAmount();
                }
            }
            int actualDeposited = Math.min(movingAmount, availableSpace);
            if (actualDeposited <= 0) return;
            depositedItem = movingStack.clone();
            depositedItem.setAmount(actualDeposited);
        } else {
            // Cursor-based placement: actual deposited amount depends on action type.
            switch (event.getAction()) {
                case PLACE_ALL -> depositedItem = cursorItem; // Full cursor placed — correct as-is
                case PLACE_SOME -> {
                    // Fills slot to maxStack; only (maxStack - slotExisting) items deposited.
                    // cursorItem.getAmount() is the FULL cursor — overcounts actual deposit.
                    ItemStack slotItem = event.getCurrentItem();
                    int slotExisting = (slotItem != null && !slotItem.getType().isAir()) ? slotItem.getAmount() : 0;
                    int actualDeposited = Math.min(cursorItem.getAmount(), cursorItem.getMaxStackSize() - slotExisting);
                    if (actualDeposited <= 0) return;
                    depositedItem = cursorItem.clone();
                    depositedItem.setAmount(actualDeposited);
                }
                case PLACE_ONE -> {
                    // Right-click places exactly 1 item regardless of cursor size.
                    depositedItem = cursorItem.clone();
                    depositedItem.setAmount(1);
                }
                default -> { return; } // Not a deposit action (e.g. pickup, hotbar swap)
            }
        }

        // Check if deposited item is accepted payment (not offering)
        if (!barterSign.isPaymentAccepted(depositedItem)) {
            return; // Not a payment item
        }

        // Validate quantity is an exact multiple of the required payment amount
        int requiredQty = barterSign.getPaymentAmount(depositedItem.getType());
        if (requiredQty > 0) {
            int depositedAmount = depositedItem.getAmount();
            if (depositedAmount == 0 || depositedAmount % requiredQty != 0) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "x Must deposit " + requiredQty + "x "
                    + depositedItem.getType().name().toLowerCase().replace('_', ' ')
                    + ChatColor.GRAY + " (or an exact multiple)");
                return;
            }

            // Validate shop has stock for the full deposit quantity.
            // Use event.getInventory() (the live double chest inventory) rather than
            // shopContainer.getInventory() which can return only one half of a double chest
            // depending on which half the ShopContainer was registered against.
            ItemStack offering = barterSign.getItemOffering();
            if (offering != null) {
                int increments = depositedAmount / requiredQty;
                int neededStock = offering.getAmount() * increments;
                int availableStock = 0;
                for (ItemStack item : event.getInventory().getContents()) {
                    if (item != null && item.isSimilar(offering)) {
                        availableStock += item.getAmount();
                    }
                }
                if (availableStock < neededStock) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "x Shop is out of stock");
                    return;
                }
            }
        }

        // Check auto-exchange preference
        org.fourz.BarterShops.trade.AutoExchangeHandler autoExchange = plugin.getAutoExchangeHandler();
        if (autoExchange == null || !autoExchange.isAutoExchangeEnabled(player)) {
            return; // Auto-exchange disabled
        }

        // Per-event deduplication guard: Paper fires InventoryClickEvent for each DoubleChest
        // half on a shift-click, which would schedule two auto-exchange tasks before the
        // existing 500ms debounce in AutoExchangeHandler has a chance to block the second.
        // This set ensures only one task is scheduled per player+shop per interaction.
        final String depositKey = player.getUniqueId() + ":" + barterSign.getShopId();
        if (!pendingDeposits.add(depositKey)) {
            logger.debug("Duplicate deposit event suppressed — " + player.getName() + " shop " + barterSign.getShopId());
            return;
        }

        logger.debug("Processing payment deposit auto-exchange for " + player.getName());

        // Make final for lambda
        final ItemStack finalDepositedItem = depositedItem.clone();

        // Schedule for next tick to ensure item is in chest
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingDeposits.remove(depositKey);
            autoExchange.handlePaymentDeposit(player, finalDepositedItem, barterSign)
                .thenAccept(result -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (result.success()) {
                            player.sendMessage(ChatColor.GREEN + "+ Purchased " +
                                barterSign.getItemOffering().getAmount() + "x " +
                                barterSign.getItemOffering().getType().name());
                            logger.debug("Deposit auto-exchange completed: " + result.transactionId());
                        } else {
                            logger.debug("Deposit auto-exchange failed: " + result.message());
                            // Return deposited payment to prevent theft.
                            // The payment was placed in the chest before the trade ran;
                            // if the trade fails the chest keeps it unless we return it.
                            returnPaymentToPlayer(player, shopContainer, finalDepositedItem);
                            player.sendMessage(ChatColor.RED + "x Trade failed: " + result.message());
                        }
                    });
                })
                .exceptionally(ex -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        logger.error("Deposit auto-exchange error: " + ex.getMessage());
                        returnPaymentToPlayer(player, shopContainer, finalDepositedItem);
                        player.sendMessage(ChatColor.RED + "x Trade error - payment returned");
                    });
                    return null;
                });
        }, 1L); // 1 tick delay to ensure item is in chest
    }

    /**
     * Handles hoppers and droppers moving items.
     * Prevents automated systems from bypassing type locking.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        // Check if destination is a shop container
        ShopContainer shopContainer = getShopContainerFromInventory(event.getDestination());
        if (shopContainer == null) {
            logger.debug("InventoryMoveItemEvent: Destination is not a shop container");
            return;
        }

        ItemStack item = event.getItem();
        logger.info("InventoryMoveItemEvent: Automated item move detected: " + item.getType() +
                   " (qty=" + item.getAmount() + ") to shop at " + shopContainer.getLocation());

        // UNIFIED: Validate with user context (null player = automated system like hopper)
        ValidationResult result = shopContainer.validateItemForUser(item, null);
        if (!result.isValid()) {
            logger.warning("Type lock validation FAILED (hopper) - " + result.reason() +
                          ". Item: " + item.getType());

            // FIX: Cancel event only - Bukkit returns item to source (no manual drop needed)
            // Manual dropping caused item duplication
            event.setCancelled(true);
        } else {
            logger.debug("Type lock validation PASSED (hopper) for " + item.getType());
        }
    }

    /**
     * Handles players dragging items.
     * Validates each item being dragged into the container.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        ShopContainer shopContainer = getShopContainerFromEvent(event);
        if (shopContainer == null) {
            logger.debug("InventoryDragEvent: No shop container found");
            return;
        }

        Player player = (event.getWhoClicked() instanceof Player) ? (Player) event.getWhoClicked() : null;
        logger.debug("InventoryDragEvent: Shop container found at " + shopContainer.getLocation());

        ItemStack cursor = event.getOldCursor();
        if (cursor == null || cursor.getType() == Material.AIR) {
            logger.debug("InventoryDragEvent: Cursor is empty");
            return;
        }

        logger.info("InventoryDragEvent: Player " + (player != null ? player.getName() : "?") +
                  " dragging " + cursor.getType() + " (qty=" + cursor.getAmount() + ") into " + event.getRawSlots().size() + " slots");

        // Check if any drag slots are in the shop container
        for (int slot : event.getRawSlots()) {
            if (slot < event.getInventory().getSize()) {
                // Type detection on drag into untyped shop
                if (handleTypeDetection(shopContainer, cursor, player)) {
                    return; // Type just detected, allow the drag
                }

                // Slot is in shop container, validate
                logger.debug("InventoryDragEvent: Checking slot " + slot + " in container");
                // UNIFIED: Validate with user context (owner vs customer)
                ValidationResult result = shopContainer.validateItemForUser(cursor, player);
                if (!result.isValid()) {
                    logger.warning("Type lock validation FAILED (drag) - " + result.reason() +
                                  ". Item: " + cursor.getType());

                    // FIX: Cancel event only - Bukkit keeps item in cursor (no manual drop needed)
                    // Manual dropping caused item duplication
                    event.setCancelled(true);

                    // Send error notification to player
                    if (player != null) {
                        sendValidationError(player, result.reason());
                    }

                    return;
                } else {
                    logger.debug("Type lock validation PASSED (drag) for " + cursor.getType());
                }
            }
        }
    }

    /**
     * Handles customer taking offering from shop chest (withdrawal auto-deduct).
     * Uses MONITOR priority to detect items AFTER they are taken.
     * Automatically deducts payment from customer inventory if auto-exchange is enabled.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCustomerTakeOffering(InventoryClickEvent event) {
        ShopContainer shopContainer = getShopContainerFromEvent(event);
        if (shopContainer == null) return;

        Player player = (event.getWhoClicked() instanceof Player) ? (Player) event.getWhoClicked() : null;
        if (player == null) return;

        BarterSign barterSign = shopContainer.getBarterSign();
        if (barterSign == null) return;

        // Owner taking items → not a purchase
        if (barterSign.getOwner().equals(player.getUniqueId())) {
            return;
        }

        // Check if player is taking item FROM shop container (not placing)
        if (event.getRawSlot() >= event.getInventory().getSize()) {
            return; // Click in player inventory, not shop
        }

        // Get item being taken — clone immediately; the slot reference becomes AIR after
        // the event resolves, which would corrupt the async success message otherwise.
        ItemStack rawItem = event.getCurrentItem();
        if (rawItem == null || rawItem.getType() == Material.AIR) {
            return;
        }
        ItemStack takenItem = rawItem.clone();

        // Check if taken item matches offering
        ItemStack offering = barterSign.getItemOffering();
        if (offering == null || !takenItem.isSimilar(offering)) {
            return; // Not the offering item
        }

        // Calculate quantity taken
        int takenQty = calculateTakeQuantity(event, takenItem);
        if (takenQty <= 0) return;

        // Synchronous exact-multiple pre-validation — must happen before event resolves.
        // event.setCancelled() is ineffective inside thenAccept() (items already transferred).
        int baseOffering = offering.getAmount();
        if (baseOffering > 0 && takenQty % baseOffering != 0) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "✗ Must take exact multiple of " + baseOffering);
            return;
        }

        // Check auto-exchange preference
        org.fourz.BarterShops.trade.AutoExchangeHandler autoExchange = plugin.getAutoExchangeHandler();
        if (autoExchange == null || !autoExchange.isAutoExchangeEnabled(player)) {
            return; // Auto-exchange disabled
        }

        logger.debug("Processing offering withdrawal auto-deduct for " + player.getName());

        // Execute auto-deduct asynchronously
        autoExchange.handleOfferingWithdrawal(player, takenItem, takenQty, barterSign)
            .thenAccept(result -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (result.success()) {
                        // Payment deducted successfully
                        player.sendMessage(ChatColor.GREEN + "+ Purchased " + takenQty + "x " + takenItem.getType().name());
                        logger.debug("Withdrawal auto-deduct completed: " + result.transactionId());
                    } else {
                        // Payment deduction failed - cancel the take (return item to chest)
                        event.setCancelled(true);
                        player.sendMessage(ChatColor.RED + "✗ " + result.message());
                        logger.debug("Withdrawal auto-deduct failed: " + result.message());
                    }
                });
            })
            .exceptionally(ex -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "✗ Trade failed");
                    logger.error("Withdrawal auto-deduct error: " + ex.getMessage());
                });
                return null;
            });
    }

    /**
     * Calculates the quantity of items being taken from inventory.
     * Handles different click types (normal click, shift-click, etc).
     *
     * @param event The inventory click event
     * @param item The item being taken
     * @return Quantity being taken
     */
    private int calculateTakeQuantity(InventoryClickEvent event, ItemStack item) {
        return switch (event.getAction()) {
            case PICKUP_ALL, MOVE_TO_OTHER_INVENTORY -> item.getAmount(); // Take whole stack
            case PICKUP_HALF -> (int) Math.ceil(item.getAmount() / 2.0); // Take half
            case PICKUP_ONE -> 1; // Take one
            default -> 0; // Other actions don't take items
        };
    }

    /**
     * Sends validation error notification to player.
     * Uses NotificationManager if available, falls back to chat message.
     */
    private void sendValidationError(Player player, String reason) {
        try {
            NotificationManager notificationManager = plugin.getNotificationManager();
            if (notificationManager != null) {
                notificationManager.sendNotification(
                    player.getUniqueId(),
                    NotificationType.SYSTEM,
                    ChatColor.RED + "✗ " + reason
                );
                return;
            }
        } catch (Exception ignored) {
            // Fall through to chat message
        }

        // Fallback: send direct chat message
        player.sendMessage(ChatColor.RED + "✗ " + reason);
    }

    /**
     * Gets the shop container for an inventory event, or null if not a registered shop.
     */
    private ShopContainer getShopContainerFromEvent(InventoryEvent event) {
        return getShopContainerFromInventory(event.getInventory());
    }

    /**
     * Gets the shop container for an inventory, or null if not registered.
     * CRITICAL FIX: Compare by Location instead of object reference.
     * Bukkit creates new Container object instances for each event, so comparing == fails.
     * Handles both single chests (Container holder) and double chests (DoubleChest holder).
     */
    private ShopContainer getShopContainerFromInventory(org.bukkit.inventory.Inventory inventory) {
        // Single chest: holder is a Container
        if (inventory.getHolder() instanceof Container container) {
            Location containerLoc = container.getLocation();
            logger.debug("getShopContainerFromInventory: Container holder found at " + containerLoc +
                        ", searching " + shopContainers.size() + " registered containers");

            for (ShopContainer shopContainer : shopContainers.values()) {
                if (shopContainer.getLocation().equals(containerLoc)) {
                    logger.debug("getShopContainerFromInventory: Found matching shop container by location!");
                    return shopContainer;
                }
            }
            logger.debug("getShopContainerFromInventory: No matching shop container found in registered map");
            return null;
        }

        // Double chest: holder is a DoubleChest — check both halves
        if (inventory.getHolder() instanceof org.bukkit.block.DoubleChest doubleChest) {
            logger.debug("getShopContainerFromInventory: DoubleChest holder, checking both halves against " +
                        shopContainers.size() + " registered containers");
            for (ShopContainer shopContainer : shopContainers.values()) {
                Location regLoc = shopContainer.getLocation();
                if (doubleChest.getLeftSide() instanceof Container left
                        && left.getLocation().equals(regLoc)) {
                    logger.debug("getShopContainerFromInventory: Matched shop container via left half!");
                    return shopContainer;
                }
                if (doubleChest.getRightSide() instanceof Container right
                        && right.getLocation().equals(regLoc)) {
                    logger.debug("getShopContainerFromInventory: Matched shop container via right half!");
                    return shopContainer;
                }
            }
            logger.debug("getShopContainerFromInventory: No matching shop container found for double chest");
            return null;
        }

        logger.debug("getShopContainerFromInventory: Holder is not a Container or DoubleChest (type: " +
                    (inventory.getHolder() != null ? inventory.getHolder().getClass().getName() : "null") + ")");
        return null;
    }

    /**
     * Gets listener uptime for debugging.
     */
    public long getUptimeMillis() {
        return System.currentTimeMillis() - creationTime;
    }

    /**
     * Returns true if the inventory click action removes an item from a shop container slot.
     * Used to gate customer access: only the offering item (with auto-exchange on) is permitted.
     */
    private boolean isRemovingFromShopSlot(InventoryClickEvent event) {
        if (event.getRawSlot() >= event.getInventory().getSize()) return false; // Not a shop slot
        return switch (event.getAction()) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME,
                 SWAP_WITH_CURSOR, DROP_ONE_SLOT, DROP_ALL_SLOT,
                 MOVE_TO_OTHER_INVENTORY -> true;
            default -> false;
        };
    }

    /**
     * Returns true if the shop has insufficient stock to fulfil even one trade.
     * Accepts the live event inventory directly to avoid stale BlockState snapshot data.
     */
    private boolean isOutOfStock(BarterSign barterSign, org.bukkit.inventory.Inventory chestInventory) {
        ItemStack offering = barterSign.getItemOffering();
        if (offering == null) return false;
        int availableStock = 0;
        for (ItemStack item : chestInventory.getContents()) {
            if (item != null && item.isSimilar(offering)) {
                availableStock += item.getAmount();
            }
        }
        return availableStock < offering.getAmount();
    }

    /**
     * Returns deposited payment items from the shop chest back to the player.
     * Called when a deposit auto-exchange trade fails after payment was already placed in chest.
     * Must be called on the main thread.
     *
     * @param player Player to return items to
     * @param shopContainer The shop container holding the payment
     * @param payment The payment item to return (quantity as deposited)
     */
    private void returnPaymentToPlayer(Player player, ShopContainer shopContainer, ItemStack payment) {
        try {
            org.bukkit.inventory.Inventory chestInv = shopContainer.getInventory();
            ItemStack toReturn = payment.clone();
            int remaining = toReturn.getAmount();

            // Remove from chest
            org.bukkit.inventory.ItemStack[] contents = chestInv.getContents();
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                org.bukkit.inventory.ItemStack stack = contents[i];
                if (stack != null && stack.isSimilar(toReturn)) {
                    int take = Math.min(remaining, stack.getAmount());
                    stack.setAmount(stack.getAmount() - take);
                    chestInv.setItem(i, stack.getAmount() == 0 ? null : stack);
                    remaining -= take;
                }
            }

            int returned = toReturn.getAmount() - remaining;
            if (returned > 0) {
                toReturn.setAmount(returned);
                Map<Integer, org.bukkit.inventory.ItemStack> leftover = player.getInventory().addItem(toReturn);
                if (!leftover.isEmpty()) {
                    leftover.values().forEach(drop -> player.getWorld().dropItem(player.getLocation(), drop));
                    player.sendMessage(ChatColor.YELLOW + "! Payment dropped at your feet (inventory full)");
                }
                logger.debug("Returned " + returned + "x " + payment.getType() + " payment to " + player.getName());
            }
        } catch (Exception ex) {
            logger.error("Failed to return payment to " + player.getName() + ": " + ex.getMessage());
        }
    }

    /**
     * Cleanup on plugin disable.
     */
    public void cleanup() {
        shopContainers.clear();
        pendingDeposits.clear();
    }
}
