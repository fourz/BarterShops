package org.fourz.BarterShops.trade;

import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.sign.BarterSign;

import java.util.UUID;

/**
 * Represents an active trade session between a buyer and a shop.
 * Tracks the state of an in-progress trade transaction.
 */
public class TradeSession {

    private final String sessionId;
    private final UUID buyerUuid;
    private final BarterSign shop;
    private final long createdAt;
    private final long expiresAt;

    private TradeState state;
    private ItemStack offeredItem;
    private int offeredQuantity;
    private ItemStack requestedItem;
    private int requestedQuantity;

    private static final long DEFAULT_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes

    /**
     * Creates a new trade session.
     *
     * @param buyerUuid The buyer's UUID
     * @param shop The shop being traded with
     */
    public TradeSession(UUID buyerUuid, BarterSign shop) {
        this.sessionId = UUID.randomUUID().toString();
        this.buyerUuid = buyerUuid;
        this.shop = shop;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = createdAt + DEFAULT_TIMEOUT_MS;
        this.state = TradeState.INITIATED;
    }

    /**
     * Creates a trade session with custom timeout.
     */
    public TradeSession(UUID buyerUuid, BarterSign shop, long timeoutMs) {
        this.sessionId = UUID.randomUUID().toString();
        this.buyerUuid = buyerUuid;
        this.shop = shop;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = createdAt + timeoutMs;
        this.state = TradeState.INITIATED;
    }

    // Getters
    public String getSessionId() { return sessionId; }
    public UUID getBuyerUuid() { return buyerUuid; }
    public BarterSign getShop() { return shop; }
    public UUID getSellerUuid() { return shop.getOwner(); }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }
    public TradeState getState() { return state; }

    public ItemStack getOfferedItem() { return offeredItem; }
    public int getOfferedQuantity() { return offeredQuantity; }
    public ItemStack getRequestedItem() { return requestedItem; }
    public int getRequestedQuantity() { return requestedQuantity; }

    // Setters
    public void setState(TradeState state) { this.state = state; }

    public void setOfferedItem(ItemStack item, int quantity) {
        this.offeredItem = item != null ? item.clone() : null;
        this.offeredQuantity = quantity;
    }

    public void setRequestedItem(ItemStack item, int quantity) {
        this.requestedItem = item != null ? item.clone() : null;
        this.requestedQuantity = quantity;
    }

    /**
     * Checks if this session has expired.
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    /**
     * Checks if this session is still active.
     */
    public boolean isActive() {
        return !isExpired() && state != TradeState.COMPLETED &&
               state != TradeState.CANCELLED && state != TradeState.FAILED;
    }

    /**
     * Gets remaining time in milliseconds.
     */
    public long getRemainingTime() {
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }

    /**
     * Trade session states.
     */
    public enum TradeState {
        /** Trade session just created */
        INITIATED,
        /** Waiting for buyer to confirm items */
        AWAITING_BUYER_CONFIRM,
        /** Validating inventory and items */
        VALIDATING,
        /** Waiting for final confirmation */
        AWAITING_FINAL_CONFIRM,
        /** Processing the item exchange */
        PROCESSING,
        /** Trade completed successfully */
        COMPLETED,
        /** Trade was cancelled */
        CANCELLED,
        /** Trade failed validation or execution */
        FAILED
    }
}
