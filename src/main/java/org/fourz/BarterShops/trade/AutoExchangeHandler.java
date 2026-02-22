package org.fourz.BarterShops.trade;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.container.ShopContainer;
import org.fourz.BarterShops.preferences.ShopPreferenceManager;
import org.fourz.BarterShops.service.ITradeService.TradeResultDTO;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles auto-exchange purchasing for BarterShops.
 * Orchestrates three purchase methods:
 * 1. Instant left-click purchase (payment in hand)
 * 2. Deposit payment auto-exchange (payment placed in chest)
 * 3. Withdrawal auto-deduct (offering taken from chest)
 */
public class AutoExchangeHandler {

    private final BarterShops plugin;
    private final LogManager logger;
    private final TradeEngine tradeEngine;
    private final ShopPreferenceManager preferenceManager;

    /** Debounce tracking: playerId+shopId -> last trade timestamp */
    private final Map<String, Long> lastTradeTime = new ConcurrentHashMap<>();

    /** Debounce period in milliseconds */
    private static final long DEBOUNCE_MS = 500;

    public AutoExchangeHandler(
        BarterShops plugin,
        TradeEngine tradeEngine,
        ShopPreferenceManager preferenceManager
    ) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "AutoExchange");
        this.tradeEngine = tradeEngine;
        this.preferenceManager = preferenceManager;
    }

    /**
     * Handles payment deposit auto-exchange.
     * Triggered when customer places payment item in shop chest.
     *
     * @param player The customer depositing payment
     * @param depositedItem Item being deposited
     * @param shop Shop sign data
     * @return CompletableFuture with trade result
     */
    public CompletableFuture<TradeResultDTO> handlePaymentDeposit(
        Player player,
        ItemStack depositedItem,
        BarterSign shop
    ) {
        logger.debug("Handling payment deposit for " + player.getName());

        // Check auto-exchange preference
        if (!isAutoExchangeEnabled(player)) {
            return CompletableFuture.completedFuture(
                TradeResultDTO.failure("Auto-exchange disabled")
            );
        }

        // Check debounce
        if (isDebouncePeriodActive(player.getUniqueId(), shop.getShopId())) {
            logger.debug("Debounce active - skipping duplicate trade");
            return CompletableFuture.completedFuture(
                TradeResultDTO.failure("Trade too soon")
            );
        }

        // Verify deposited item is accepted payment
        if (!shop.isPaymentAccepted(depositedItem)) {
            return CompletableFuture.completedFuture(
                TradeResultDTO.failure("Payment not accepted")
            );
        }

        // Get offering and payment details
        ItemStack offering = shop.getItemOffering();
        if (offering == null) {
            return CompletableFuture.completedFuture(
                TradeResultDTO.failure("Shop not fully configured")
            );
        }
        int baseOfferedQty = offering.getAmount();
        int basePaymentQty = shop.getPaymentAmount(depositedItem.getType());

        if (basePaymentQty == 0) {
            return CompletableFuture.completedFuture(
                TradeResultDTO.failure("Payment amount not configured")
            );
        }

        // PHASE 2 FIX: Calculate increments for bulk purchases
        int depositedAmount = depositedItem.getAmount();

        // Validate exact multiple
        if (depositedAmount % basePaymentQty != 0) {
            return CompletableFuture.completedFuture(
                TradeResultDTO.failure("Must deposit exact multiple of " + basePaymentQty)
            );
        }

        // Calculate how many trades (increments) this payment covers
        int increments = depositedAmount / basePaymentQty;

        // Scale offering and payment quantities
        int totalOffering = baseOfferedQty * increments;
        int totalPayment = basePaymentQty * increments;

        // Inventory space is not pre-checked here: TradeEngine.executeItemExchange()
        // drops any overflow items at the buyer's feet via dropItems(), so a full
        // inventory should not block the trade.

        // Execute direct trade — payment already deposited in chest, skip Steps 2 & 4
        updateDebounce(player.getUniqueId(), shop.getShopId());
        return tradeEngine.executeDirectTrade(
            player,
            shop,
            offering,
            totalOffering,
            null,   // Payment already in chest — skip remove-from-player and re-add-to-chest
            0,
            TradeSource.DEPOSIT_EXCHANGE
        );
    }

    /**
     * Handles offering withdrawal auto-deduct.
     * Triggered when customer takes offering from shop chest.
     *
     * @param player The customer taking offering
     * @param takenItem Item being taken
     * @param takenQty Quantity taken
     * @param shop Shop sign data
     * @return CompletableFuture with trade result
     */
    public CompletableFuture<TradeResultDTO> handleOfferingWithdrawal(
        Player player,
        ItemStack takenItem,
        int takenQty,
        BarterSign shop
    ) {
        logger.debug("Handling offering withdrawal for " + player.getName());

        // Check auto-exchange preference
        if (!isAutoExchangeEnabled(player)) {
            return CompletableFuture.completedFuture(
                TradeResultDTO.failure("Auto-exchange disabled")
            );
        }

        // Check debounce
        if (isDebouncePeriodActive(player.getUniqueId(), shop.getShopId())) {
            logger.debug("Debounce active - skipping duplicate trade");
            return CompletableFuture.completedFuture(
                TradeResultDTO.failure("Trade too soon")
            );
        }

        // Verify taken item matches offering
        ItemStack offering = shop.getItemOffering();
        if (!takenItem.isSimilar(offering)) {
            return CompletableFuture.completedFuture(
                TradeResultDTO.failure("Item mismatch")
            );
        }

        // PHASE 2 FIX: Calculate payment required based on quantity taken
        int baseOffering = offering.getAmount();

        // Validate exact multiple
        if (takenQty % baseOffering != 0) {
            return CompletableFuture.completedFuture(
                TradeResultDTO.failure("Must take exact multiple of " + baseOffering)
            );
        }

        // Calculate increments
        int increments = takenQty / baseOffering;

        // Calculate payment required
        // For BARTER shops, need to check what payment player has available
        ItemStack payment = null;
        int basePaymentQty = 0;

        if (shop.getType() == org.fourz.BarterShops.sign.SignType.BARTER) {
            // Check player inventory for any accepted payment
            for (ItemStack acceptedPayment : shop.getAcceptedPayments()) {
                int baseRequired = shop.getPaymentAmount(acceptedPayment.getType());
                int totalRequired = baseRequired * increments;
                int available = countItems(player.getInventory(), acceptedPayment);
                if (available >= totalRequired) {
                    payment = acceptedPayment.clone();
                    payment.setAmount(1);
                    basePaymentQty = baseRequired;
                    break;
                }
            }

            if (payment == null) {
                return CompletableFuture.completedFuture(
                    TradeResultDTO.failure("Insufficient payment")
                );
            }
        } else {
            // BUY/SELL shops have fixed price
            payment = shop.getPriceItem();
            basePaymentQty = shop.getPriceAmount();

            if (payment == null || basePaymentQty <= 0) {
                return CompletableFuture.completedFuture(
                    TradeResultDTO.failure("Shop not configured")
                );
            }

            // Verify player has payment for all increments
            int totalRequired = basePaymentQty * increments;
            int available = countItems(player.getInventory(), payment);
            if (available < totalRequired) {
                return CompletableFuture.completedFuture(
                    TradeResultDTO.failure("Insufficient payment")
                );
            }
        }

        // Scale payment quantity
        int totalPayment = basePaymentQty * increments;

        // Execute withdrawal trade: player already has the offering (taken from chest
        // via the Bukkit event). Only deduct payment and deposit it into the shop chest.
        // executeDirectTrade must NOT be used here — it would re-deliver the offering.
        updateDebounce(player.getUniqueId(), shop.getShopId());
        return tradeEngine.executeWithdrawalTrade(
            player,
            shop,
            offering,
            takenQty,
            payment,
            totalPayment,
            TradeSource.WITHDRAWAL_EXCHANGE
        );
    }

    /**
     * Checks if auto-exchange is enabled for a player.
     */
    public boolean isAutoExchangeEnabled(Player player) {
        return preferenceManager.isAutoExchangeEnabled(player.getUniqueId());
    }

    /**
     * Checks if player is still in debounce period for a shop.
     */
    private boolean isDebouncePeriodActive(UUID playerId, int shopId) {
        String key = playerId.toString() + ":" + shopId;
        Long lastTrade = lastTradeTime.get(key);
        if (lastTrade == null) return false;

        long elapsed = System.currentTimeMillis() - lastTrade;
        return elapsed < DEBOUNCE_MS;
    }

    /**
     * Updates debounce timestamp for a player+shop combination.
     */
    private void updateDebounce(UUID playerId, int shopId) {
        String key = playerId.toString() + ":" + shopId;
        lastTradeTime.put(key, System.currentTimeMillis());
    }

    /**
     * Checks if player has enough inventory space for items.
     */
    private boolean hasInventorySpace(Inventory inventory, ItemStack item, int amount) {
        int maxStack = item.getMaxStackSize();
        int slotsNeeded = (int) Math.ceil((double) amount / maxStack);
        int emptySlots = 0;

        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                emptySlots++;
            } else if (stack.isSimilar(item)) {
                int space = maxStack - stack.getAmount();
                if (space > 0) {
                    amount -= space;
                    if (amount <= 0) return true;
                }
            }
        }

        return emptySlots >= slotsNeeded;
    }

    /**
     * Counts items in inventory matching the given item.
     */
    private int countItems(Inventory inventory, ItemStack item) {
        int count = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && stack.isSimilar(item)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    /**
     * Cleanup debounce tracking.
     */
    public void cleanup() {
        lastTradeTime.clear();
        logger.info("AutoExchangeHandler cleanup complete");
    }
}
