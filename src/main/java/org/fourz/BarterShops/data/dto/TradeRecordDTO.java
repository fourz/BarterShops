package org.fourz.BarterShops.data.dto;

import java.sql.Timestamp;
import java.util.Objects;
import java.util.UUID;

/**
 * Data Transfer Object for completed trade records using Java Record.
 * Immutable and thread-safe for cross-plugin data transfer via RVNKCore.
 *
 * <p>Represents a historical trade transaction for auditing and analytics.</p>
 */
public record TradeRecordDTO(
    String transactionId,
    int shopId,
    UUID buyerUuid,
    UUID sellerUuid,
    String itemStackData,
    int quantity,
    String currencyMaterial,
    int pricePaid,
    TradeStatus status,
    Timestamp completedAt
) {
    /**
     * Compact constructor with validation.
     */
    public TradeRecordDTO {
        Objects.requireNonNull(transactionId, "transactionId cannot be null");
        Objects.requireNonNull(buyerUuid, "buyerUuid cannot be null");
        Objects.requireNonNull(sellerUuid, "sellerUuid cannot be null");

        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }

        if (status == null) {
            status = TradeStatus.COMPLETED;
        }
    }

    /**
     * Creates a completed trade record.
     *
     * @param shopId The shop where trade occurred
     * @param buyerUuid The buyer's UUID
     * @param sellerUuid The seller's UUID
     * @param itemStackData Serialized item data
     * @param quantity Number of items traded
     * @param currencyMaterial Currency used
     * @param pricePaid Total price paid
     * @return A new completed TradeRecordDTO
     */
    public static TradeRecordDTO completed(int shopId, UUID buyerUuid, UUID sellerUuid,
            String itemStackData, int quantity, String currencyMaterial, int pricePaid) {
        return new TradeRecordDTO(
            UUID.randomUUID().toString(),
            shopId, buyerUuid, sellerUuid, itemStackData, quantity,
            currencyMaterial, pricePaid, TradeStatus.COMPLETED,
            new Timestamp(System.currentTimeMillis())
        );
    }

    /**
     * Gets the timestamp as epoch milliseconds.
     *
     * @return Timestamp in milliseconds since epoch
     */
    public long getTimestamp() {
        return completedAt != null ? completedAt.getTime() : 0;
    }

    /**
     * Trade status enumeration.
     */
    public enum TradeStatus {
        /** Trade completed successfully */
        COMPLETED,
        /** Trade was cancelled */
        CANCELLED,
        /** Trade failed due to validation */
        FAILED,
        /** Trade is pending confirmation */
        PENDING,
        /** Trade was refunded */
        REFUNDED
    }

    /**
     * Builder for constructing TradeRecordDTO with optional fields.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for TradeRecordDTO.
     */
    public static class Builder {
        private String transactionId = UUID.randomUUID().toString();
        private int shopId;
        private UUID buyerUuid;
        private UUID sellerUuid;
        private String itemStackData;
        private int quantity;
        private String currencyMaterial;
        private int pricePaid;
        private TradeStatus status = TradeStatus.COMPLETED;
        private Timestamp completedAt = new Timestamp(System.currentTimeMillis());

        public Builder transactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public Builder shopId(int shopId) {
            this.shopId = shopId;
            return this;
        }

        public Builder buyerUuid(UUID buyerUuid) {
            this.buyerUuid = buyerUuid;
            return this;
        }

        public Builder sellerUuid(UUID sellerUuid) {
            this.sellerUuid = sellerUuid;
            return this;
        }

        public Builder itemStackData(String itemStackData) {
            this.itemStackData = itemStackData;
            return this;
        }

        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder currencyMaterial(String currencyMaterial) {
            this.currencyMaterial = currencyMaterial;
            return this;
        }

        public Builder pricePaid(int pricePaid) {
            this.pricePaid = pricePaid;
            return this;
        }

        public Builder status(TradeStatus status) {
            this.status = status;
            return this;
        }

        public Builder completedAt(Timestamp completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public TradeRecordDTO build() {
            return new TradeRecordDTO(
                transactionId, shopId, buyerUuid, sellerUuid,
                itemStackData, quantity, currencyMaterial, pricePaid,
                status, completedAt
            );
        }
    }
}
