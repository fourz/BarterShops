package org.fourz.BarterShops.data.dto;

import java.sql.Timestamp;
import java.util.Objects;

/**
 * Data Transfer Object for trade item configuration using Java Record.
 * Immutable and thread-safe for cross-plugin data transfer via RVNKCore.
 *
 * <p>Represents an item listing in a shop - what the shop offers or accepts.</p>
 */
public record TradeItemDTO(
    int tradeItemId,
    int shopId,
    String itemStackData,
    String currencyMaterial,
    int priceAmount,
    int stockQuantity,
    boolean isOffering,
    Timestamp createdAt
) {
    /**
     * Compact constructor with validation.
     */
    public TradeItemDTO {
        Objects.requireNonNull(itemStackData, "itemStackData cannot be null");

        if (priceAmount < 0) {
            throw new IllegalArgumentException("priceAmount cannot be negative");
        }
        if (stockQuantity < 0) {
            throw new IllegalArgumentException("stockQuantity cannot be negative");
        }
    }

    /**
     * Creates a new TradeItemDTO for an item the shop is offering.
     *
     * @param shopId The shop ID
     * @param itemStackData Serialized item data
     * @param currencyMaterial The currency material type
     * @param priceAmount The price in currency
     * @param stockQuantity Available stock
     * @return A new TradeItemDTO configured as an offering
     */
    public static TradeItemDTO offering(int shopId, String itemStackData,
            String currencyMaterial, int priceAmount, int stockQuantity) {
        return new TradeItemDTO(
            0, shopId, itemStackData, currencyMaterial,
            priceAmount, stockQuantity, true,
            new Timestamp(System.currentTimeMillis())
        );
    }

    /**
     * Creates a new TradeItemDTO for an item the shop is accepting.
     *
     * @param shopId The shop ID
     * @param itemStackData Serialized item data
     * @param currencyMaterial The currency material type
     * @param priceAmount The price in currency
     * @return A new TradeItemDTO configured as accepting
     */
    public static TradeItemDTO accepting(int shopId, String itemStackData,
            String currencyMaterial, int priceAmount) {
        return new TradeItemDTO(
            0, shopId, itemStackData, currencyMaterial,
            priceAmount, Integer.MAX_VALUE, false,
            new Timestamp(System.currentTimeMillis())
        );
    }

    /**
     * Builder for constructing TradeItemDTO with optional fields.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for TradeItemDTO.
     */
    public static class Builder {
        private int tradeItemId;
        private int shopId;
        private String itemStackData;
        private String currencyMaterial;
        private int priceAmount;
        private int stockQuantity;
        private boolean isOffering = true;
        private Timestamp createdAt = new Timestamp(System.currentTimeMillis());

        public Builder tradeItemId(int tradeItemId) {
            this.tradeItemId = tradeItemId;
            return this;
        }

        public Builder shopId(int shopId) {
            this.shopId = shopId;
            return this;
        }

        public Builder itemStackData(String itemStackData) {
            this.itemStackData = itemStackData;
            return this;
        }

        public Builder currencyMaterial(String currencyMaterial) {
            this.currencyMaterial = currencyMaterial;
            return this;
        }

        public Builder priceAmount(int priceAmount) {
            this.priceAmount = priceAmount;
            return this;
        }

        public Builder stockQuantity(int stockQuantity) {
            this.stockQuantity = stockQuantity;
            return this;
        }

        public Builder isOffering(boolean isOffering) {
            this.isOffering = isOffering;
            return this;
        }

        public Builder createdAt(Timestamp createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public TradeItemDTO build() {
            return new TradeItemDTO(
                tradeItemId, shopId, itemStackData, currencyMaterial,
                priceAmount, stockQuantity, isOffering, createdAt
            );
        }
    }
}
