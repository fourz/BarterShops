package org.fourz.BarterShops.economy;

import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Calculator for shop-related fees and pricing logic.
 * Provides advanced fee calculation based on shop type, item rarity, and trade volume.
 */
public class ShopFeeCalculator {

    private final EconomyManager economyManager;

    // Item rarity multipliers
    private static final Map<String, Double> RARITY_MULTIPLIERS = new HashMap<>();

    static {
        RARITY_MULTIPLIERS.put("NETHERITE", 2.0);
        RARITY_MULTIPLIERS.put("DIAMOND", 1.5);
        RARITY_MULTIPLIERS.put("EMERALD", 1.5);
        RARITY_MULTIPLIERS.put("GOLD", 1.3);
        RARITY_MULTIPLIERS.put("IRON", 1.2);
        RARITY_MULTIPLIERS.put("ELYTRA", 3.0);
        RARITY_MULTIPLIERS.put("ENCHANTED_GOLDEN_APPLE", 2.5);
        RARITY_MULTIPLIERS.put("TOTEM_OF_UNDYING", 2.5);
        RARITY_MULTIPLIERS.put("DRAGON_EGG", 5.0);
        RARITY_MULTIPLIERS.put("NETHER_STAR", 2.0);
    }

    public ShopFeeCalculator(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    /**
     * Calculates the listing fee for a shop based on type and configuration.
     *
     * @param shopType The type of shop being created
     * @return The calculated listing fee
     */
    public double calculateListingFee(String shopType) {
        return economyManager.calculateListingFee(shopType);
    }

    /**
     * Calculates the listing fee with item rarity bonus.
     *
     * @param shopType The type of shop
     * @param primaryItem The primary item being sold (can be null)
     * @return The calculated fee with rarity multiplier applied
     */
    public double calculateListingFeeWithRarity(String shopType, ItemStack primaryItem) {
        double baseFee = calculateListingFee(shopType);

        if (primaryItem == null || baseFee == 0.0) {
            return baseFee;
        }

        double multiplier = getRarityMultiplier(primaryItem);
        return baseFee * multiplier;
    }

    /**
     * Gets the rarity multiplier for an item.
     *
     * @param item The item to check
     * @return The rarity multiplier (1.0 = common)
     */
    private double getRarityMultiplier(ItemStack item) {
        String itemType = item.getType().name();

        for (Map.Entry<String, Double> entry : RARITY_MULTIPLIERS.entrySet()) {
            if (itemType.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return 1.0; // Common items
    }

    /**
     * Calculates trade tax for a currency-based transaction.
     *
     * @param tradeValue The value of the trade
     * @return The calculated tax amount
     */
    public double calculateTradeTax(double tradeValue) {
        return economyManager.calculateTradeTax(tradeValue);
    }

    /**
     * Calculates trade tax with volume discount.
     * Larger trades get lower tax rates.
     *
     * @param tradeValue The value of the trade
     * @return The calculated tax with volume discount
     */
    public double calculateTradeTaxWithDiscount(double tradeValue) {
        double baseTax = calculateTradeTax(tradeValue);

        if (baseTax == 0.0) {
            return 0.0;
        }

        // Volume discounts
        double discount = 1.0;
        if (tradeValue >= 10000) {
            discount = 0.75; // 25% discount for large trades
        } else if (tradeValue >= 5000) {
            discount = 0.85; // 15% discount for medium trades
        } else if (tradeValue >= 1000) {
            discount = 0.95; // 5% discount for small trades
        }

        return baseTax * discount;
    }

    /**
     * Estimates the total cost of creating a shop including fees.
     *
     * @param shopType The type of shop
     * @param primaryItem The primary item (can be null)
     * @return Total estimated cost
     */
    public double estimateShopCost(String shopType, ItemStack primaryItem) {
        return calculateListingFeeWithRarity(shopType, primaryItem);
    }

    /**
     * Estimates the net profit from a trade after taxes.
     *
     * @param salePrice The gross sale price
     * @param useVolumeDiscount Whether to apply volume discount
     * @return Net profit after tax
     */
    public double estimateNetProfit(double salePrice, boolean useVolumeDiscount) {
        double tax = useVolumeDiscount ?
            calculateTradeTaxWithDiscount(salePrice) :
            calculateTradeTax(salePrice);

        return salePrice - tax;
    }

    /**
     * Formats a currency amount for display.
     *
     * @param amount The amount to format
     * @return Formatted currency string
     */
    public String formatCurrency(double amount) {
        return economyManager.format(amount);
    }

    /**
     * Gets the current tax rate as a percentage string.
     *
     * @return Tax rate percentage (e.g., "5.0%")
     */
    public String getTaxRatePercentage() {
        return String.format("%.1f%%", economyManager.getTaxRate() * 100);
    }

    /**
     * Checks if fees are currently enabled.
     *
     * @return true if fees are enabled
     */
    public boolean areFeesEnabled() {
        return economyManager.areFeesEnabled();
    }

    /**
     * Checks if taxes are currently enabled.
     *
     * @return true if taxes are enabled
     */
    public boolean areTaxesEnabled() {
        return economyManager.areTaxesEnabled();
    }

    /**
     * Calculates a breakdown of shop creation costs.
     */
    public record CostBreakdown(
        double baseFee,
        double rarityMultiplier,
        double totalCost,
        String itemType
    ) {
        public String formatBreakdown() {
            if (totalCost == 0.0) {
                return "No fees (economy disabled or fees disabled)";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Base Fee: ").append(String.format("$%.2f", baseFee)).append("\n");
            if (rarityMultiplier > 1.0) {
                sb.append("Rarity Bonus: x").append(String.format("%.1f", rarityMultiplier))
                  .append(" (").append(itemType).append(")\n");
            }
            sb.append("Total: ").append(String.format("$%.2f", totalCost));
            return sb.toString();
        }
    }

    /**
     * Gets a detailed cost breakdown for shop creation.
     *
     * @param shopType The type of shop
     * @param primaryItem The primary item (can be null)
     * @return Cost breakdown details
     */
    public CostBreakdown getShopCostBreakdown(String shopType, ItemStack primaryItem) {
        double baseFee = calculateListingFee(shopType);
        double multiplier = 1.0;
        String itemType = "None";

        if (primaryItem != null && baseFee > 0.0) {
            multiplier = getRarityMultiplier(primaryItem);
            itemType = primaryItem.getType().name();
        }

        double totalCost = baseFee * multiplier;

        return new CostBreakdown(baseFee, multiplier, totalCost, itemType);
    }
}
