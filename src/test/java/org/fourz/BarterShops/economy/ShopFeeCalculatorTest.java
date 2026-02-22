package org.fourz.BarterShops.economy;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ShopFeeCalculator.
 * Tests rarity multipliers, volume discounts, and cost breakdowns.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShopFeeCalculator Tests")
class ShopFeeCalculatorTest {

    @Mock
    private EconomyManager economyManager;

    private ShopFeeCalculator calculator;

    @BeforeEach
    void setUp() {
        // Setup economy manager mock
        when(economyManager.calculateListingFee("BARTER")).thenReturn(100.0);
        when(economyManager.calculateListingFee("CURRENCY")).thenReturn(500.0);
        when(economyManager.calculateTradeTax(anyDouble())).thenAnswer(inv -> {
            double value = inv.getArgument(0);
            return value * 0.05; // 5% tax
        });
        when(economyManager.format(anyDouble())).thenAnswer(inv -> {
            double amount = inv.getArgument(0);
            return String.format("$%.2f", amount);
        });
        when(economyManager.getTaxRate()).thenReturn(0.05);
        when(economyManager.areFeesEnabled()).thenReturn(true);
        when(economyManager.areTaxesEnabled()).thenReturn(true);

        calculator = new ShopFeeCalculator(economyManager);
    }

    // ====== Basic Fee Calculation Tests ======

    @Nested
    @DisplayName("Basic Fee Calculation")
    class BasicFeeCalculationTests {

        @Test
        @DisplayName("calculateListingFee returns base fee for barter shops")
        void calculateListingFeeReturnsFee() {
            double fee = calculator.calculateListingFee("BARTER");

            assertEquals(100.0, fee);
            verify(economyManager).calculateListingFee("BARTER");
        }

        @Test
        @DisplayName("calculateListingFee returns higher fee for currency shops")
        void calculateListingFeeCurrencyShops() {
            double fee = calculator.calculateListingFee("CURRENCY");

            assertEquals(500.0, fee);
            verify(economyManager).calculateListingFee("CURRENCY");
        }

        @Test
        @DisplayName("calculateTradeTax returns percentage of trade value")
        void calculateTradeTaxReturnsPercentage() {
            double tax = calculator.calculateTradeTax(1000.0);

            assertEquals(50.0, tax);
            verify(economyManager).calculateTradeTax(1000.0);
        }
    }

    // ====== Rarity Multiplier Tests ======

    @Nested
    @DisplayName("Rarity Multipliers")
    class RarityMultiplierTests {

        @Test
        @DisplayName("calculateListingFeeWithRarity applies dragon egg multiplier")
        void rarityMultiplierDragonEgg() {
            ItemStack dragonEgg = new ItemStack(Material.DRAGON_EGG);
            when(economyManager.calculateListingFee("BARTER")).thenReturn(100.0);

            double fee = calculator.calculateListingFeeWithRarity("BARTER", dragonEgg);

            assertEquals(500.0, fee); // 100.0 * 5.0
        }

        @Test
        @DisplayName("calculateListingFeeWithRarity applies elytra multiplier")
        void rarityMultiplierElytra() {
            ItemStack elytra = new ItemStack(Material.ELYTRA);
            when(economyManager.calculateListingFee("BARTER")).thenReturn(100.0);

            double fee = calculator.calculateListingFeeWithRarity("BARTER", elytra);

            assertEquals(300.0, fee); // 100.0 * 3.0
        }

        @Test
        @DisplayName("calculateListingFeeWithRarity applies netherite multiplier")
        void rarityMultiplierNetherite() {
            ItemStack netherite = new ItemStack(Material.NETHERITE_INGOT);
            when(economyManager.calculateListingFee("BARTER")).thenReturn(100.0);

            double fee = calculator.calculateListingFeeWithRarity("BARTER", netherite);

            assertEquals(200.0, fee); // 100.0 * 2.0
        }

        @Test
        @DisplayName("calculateListingFeeWithRarity applies diamond multiplier")
        void rarityMultiplierDiamond() {
            ItemStack diamond = new ItemStack(Material.DIAMOND);
            when(economyManager.calculateListingFee("BARTER")).thenReturn(100.0);

            double fee = calculator.calculateListingFeeWithRarity("BARTER", diamond);

            assertEquals(150.0, fee); // 100.0 * 1.5
        }

        @Test
        @DisplayName("calculateListingFeeWithRarity applies common item multiplier")
        void rarityMultiplierCommon() {
            ItemStack dirt = new ItemStack(Material.DIRT);
            when(economyManager.calculateListingFee("BARTER")).thenReturn(100.0);

            double fee = calculator.calculateListingFeeWithRarity("BARTER", dirt);

            assertEquals(100.0, fee); // 100.0 * 1.0
        }

        @Test
        @DisplayName("calculateListingFeeWithRarity handles null item")
        void rarityMultiplierNullItem() {
            when(economyManager.calculateListingFee("BARTER")).thenReturn(100.0);

            double fee = calculator.calculateListingFeeWithRarity("BARTER", null);

            assertEquals(100.0, fee); // No multiplier applied
        }
    }

    // ====== Volume Discount Tests ======

    @Nested
    @DisplayName("Volume Discounts")
    class VolumeDiscountTests {

        @Test
        @DisplayName("calculateTradeTaxWithDiscount applies 25% discount for large trades (10000+)")
        void volumeDiscountLargeTradeAbove10k() {
            when(economyManager.calculateTradeTax(10000.0)).thenReturn(500.0);

            double tax = calculator.calculateTradeTaxWithDiscount(10000.0);

            assertEquals(375.0, tax); // 500.0 * 0.75
        }

        @Test
        @DisplayName("calculateTradeTaxWithDiscount applies 15% discount for medium trades (5000-9999)")
        void volumeDiscountMediumTrade5k() {
            when(economyManager.calculateTradeTax(5000.0)).thenReturn(250.0);

            double tax = calculator.calculateTradeTaxWithDiscount(5000.0);

            assertEquals(212.5, tax); // 250.0 * 0.85
        }

        @Test
        @DisplayName("calculateTradeTaxWithDiscount applies 5% discount for small trades (1000-4999)")
        void volumeDiscountSmallTrade1k() {
            when(economyManager.calculateTradeTax(1000.0)).thenReturn(50.0);

            double tax = calculator.calculateTradeTaxWithDiscount(1000.0);

            assertEquals(47.5, tax); // 50.0 * 0.95
        }

        @Test
        @DisplayName("calculateTradeTaxWithDiscount applies no discount for trades under 1000")
        void volumeDiscountNoDiscountUnder1k() {
            when(economyManager.calculateTradeTax(500.0)).thenReturn(25.0);

            double tax = calculator.calculateTradeTaxWithDiscount(500.0);

            assertEquals(25.0, tax); // No discount
        }

        @Test
        @DisplayName("calculateTradeTaxWithDiscount returns zero when tax is zero")
        void volumeDiscountZeroTax() {
            when(economyManager.calculateTradeTax(10000.0)).thenReturn(0.0);

            double tax = calculator.calculateTradeTaxWithDiscount(10000.0);

            assertEquals(0.0, tax);
        }
    }

    // ====== Cost Estimation Tests ======

    @Nested
    @DisplayName("Cost Estimation")
    class CostEstimationTests {

        @Test
        @DisplayName("estimateShopCost returns fee with rarity multiplier")
        void estimateShopCostWithRarity() {
            ItemStack netherite = new ItemStack(Material.NETHERITE_INGOT);
            when(economyManager.calculateListingFee("BARTER")).thenReturn(100.0);

            double cost = calculator.estimateShopCost("BARTER", netherite);

            assertEquals(200.0, cost); // 100.0 * 2.0
        }

        @Test
        @DisplayName("estimateNetProfit calculates profit after tax")
        void estimateNetProfitAfterTax() {
            double netProfit = calculator.estimateNetProfit(1000.0, false);

            assertEquals(950.0, netProfit); // 1000.0 - 50.0 (5% tax)
        }

        @Test
        @DisplayName("estimateNetProfit applies volume discount")
        void estimateNetProfitWithDiscount() {
            when(economyManager.calculateTradeTax(10000.0)).thenReturn(500.0);

            double netProfit = calculator.estimateNetProfit(10000.0, true);

            assertEquals(9625.0, netProfit); // 10000.0 - 375.0 (500.0 * 0.75)
        }
    }

    // ====== Formatting Tests ======

    @Nested
    @DisplayName("Formatting and Conversion")
    class FormattingTests {

        @Test
        @DisplayName("formatCurrency delegates to economy manager")
        void formatCurrencyDelegatesToEconomy() {
            String formatted = calculator.formatCurrency(100.0);

            assertEquals("$100.00", formatted);
            verify(economyManager).format(100.0);
        }

        @Test
        @DisplayName("getTaxRatePercentage returns percentage string")
        void getTaxRatePercentageReturnsString() {
            String percentage = calculator.getTaxRatePercentage();

            assertEquals("5.0%", percentage);
        }
    }

    // ====== Feature Check Tests ======

    @Nested
    @DisplayName("Feature Status Checks")
    class FeatureStatusTests {

        @Test
        @DisplayName("areFeesEnabled returns true when enabled")
        void areFeesEnabledTrue() {
            assertTrue(calculator.areFeesEnabled());
        }

        @Test
        @DisplayName("areFeesEnabled returns false when disabled")
        void areFeesEnabledFalse() {
            when(economyManager.areFeesEnabled()).thenReturn(false);

            assertFalse(calculator.areFeesEnabled());
        }

        @Test
        @DisplayName("areTaxesEnabled returns true when enabled")
        void areTaxesEnabledTrue() {
            assertTrue(calculator.areTaxesEnabled());
        }

        @Test
        @DisplayName("areTaxesEnabled returns false when disabled")
        void areTaxesEnabledFalse() {
            when(economyManager.areTaxesEnabled()).thenReturn(false);

            assertFalse(calculator.areTaxesEnabled());
        }
    }

    // ====== Cost Breakdown Tests ======

    @Nested
    @DisplayName("Cost Breakdown")
    class CostBreakdownTests {

        @Test
        @DisplayName("getShopCostBreakdown returns correct values for rare item")
        void costBreakdownRareItem() {
            ItemStack netherite = new ItemStack(Material.NETHERITE_INGOT);
            when(economyManager.calculateListingFee("BARTER")).thenReturn(100.0);

            ShopFeeCalculator.CostBreakdown breakdown = calculator.getShopCostBreakdown("BARTER", netherite);

            assertEquals(100.0, breakdown.baseFee());
            assertEquals(2.0, breakdown.rarityMultiplier());
            assertEquals(200.0, breakdown.totalCost());
            assertEquals("NETHERITE_INGOT", breakdown.itemType());
        }

        @Test
        @DisplayName("getShopCostBreakdown returns values for common item")
        void costBreakdownCommonItem() {
            ItemStack dirt = new ItemStack(Material.DIRT);
            when(economyManager.calculateListingFee("BARTER")).thenReturn(100.0);

            ShopFeeCalculator.CostBreakdown breakdown = calculator.getShopCostBreakdown("BARTER", dirt);

            assertEquals(100.0, breakdown.baseFee());
            assertEquals(1.0, breakdown.rarityMultiplier());
            assertEquals(100.0, breakdown.totalCost());
            assertEquals("DIRT", breakdown.itemType());
        }

        @Test
        @DisplayName("getShopCostBreakdown handles null item")
        void costBreakdownNullItem() {
            when(economyManager.calculateListingFee("BARTER")).thenReturn(100.0);

            ShopFeeCalculator.CostBreakdown breakdown = calculator.getShopCostBreakdown("BARTER", null);

            assertEquals(100.0, breakdown.baseFee());
            assertEquals(1.0, breakdown.rarityMultiplier());
            assertEquals(100.0, breakdown.totalCost());
            assertEquals("None", breakdown.itemType());
        }

        @Test
        @DisplayName("CostBreakdown.formatBreakdown returns formatted string")
        void costBreakdownFormatting() {
            ItemStack netherite = new ItemStack(Material.NETHERITE_INGOT);
            when(economyManager.calculateListingFee("BARTER")).thenReturn(100.0);

            ShopFeeCalculator.CostBreakdown breakdown = calculator.getShopCostBreakdown("BARTER", netherite);
            String formatted = breakdown.formatBreakdown();

            assertNotNull(formatted);
            assertTrue(formatted.contains("Base Fee"));
            assertTrue(formatted.contains("Rarity Bonus"));
            assertTrue(formatted.contains("Total"));
        }

        @Test
        @DisplayName("CostBreakdown.formatBreakdown shows no fees message when zero cost")
        void costBreakdownNoFeesMessage() {
            when(economyManager.calculateListingFee("BARTER")).thenReturn(0.0);

            ShopFeeCalculator.CostBreakdown breakdown = calculator.getShopCostBreakdown("BARTER", null);
            String formatted = breakdown.formatBreakdown();

            assertTrue(formatted.contains("No fees"));
        }
    }

    // ====== Edge Case Tests ======

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("handles zero trade value")
        void handlesZeroTradeValue() {
            when(economyManager.calculateTradeTax(0.0)).thenReturn(0.0);

            double tax = calculator.calculateTradeTax(0.0);
            double discount = calculator.calculateTradeTaxWithDiscount(0.0);

            assertEquals(0.0, tax);
            assertEquals(0.0, discount);
        }

        @Test
        @DisplayName("handles very large trade values")
        void handlesLargeTradeValues() {
            when(economyManager.calculateTradeTax(1000000.0)).thenReturn(50000.0);

            double tax = calculator.calculateTradeTax(1000000.0);
            double discount = calculator.calculateTradeTaxWithDiscount(1000000.0);

            assertEquals(50000.0, tax);
            assertEquals(37500.0, discount); // 50000 * 0.75
        }

        @Test
        @DisplayName("handles extreme rarity multipliers")
        void handlesExtremeRarityMultipliers() {
            ItemStack dragonEgg = new ItemStack(Material.DRAGON_EGG);
            when(economyManager.calculateListingFee("BARTER")).thenReturn(100.0);

            double fee = calculator.calculateListingFeeWithRarity("BARTER", dragonEgg);

            assertEquals(500.0, fee); // 100.0 * 5.0 (dragon egg max multiplier)
        }
    }
}
