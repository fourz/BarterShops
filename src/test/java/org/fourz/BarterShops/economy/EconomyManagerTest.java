package org.fourz.BarterShops.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.fourz.BarterShops.BarterShops;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EconomyManager.
 * Tests Vault API integration, fee calculation, and transaction handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EconomyManager Tests")
class EconomyManagerTest {

    @Mock
    private BarterShops plugin;

    @Mock
    private Server server;

    @Mock
    private PluginManager pluginManager;

    @Mock
    private ServicesManager servicesManager;

    @Mock
    private FileConfiguration config;

    @Mock
    private Economy economy;

    @Mock
    private RegisteredServiceProvider<Economy> economyProvider;

    @Mock
    private OfflinePlayer offlinePlayer;

    private EconomyManager economyManager;
    private UUID testPlayerUuid;

    @BeforeEach
    void setUp() {
        testPlayerUuid = UUID.randomUUID();

        // Setup server mock
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getServicesManager()).thenReturn(servicesManager);

        // Setup config mock
        when(plugin.getConfig()).thenReturn(config);
        when(config.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        when(config.getDouble(anyString(), anyDouble())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            double defaultValue = invocation.getArgument(1);

            return switch (key) {
                case "economy.fees.base" -> 100.0;
                case "economy.fees.currency_shop" -> 500.0;
                case "economy.taxes.rate" -> 0.05;
                default -> defaultValue;
            };
        });

        // Setup economy provider mock
        when(servicesManager.getRegistration(Economy.class)).thenReturn(economyProvider);
        when(economyProvider.getProvider()).thenReturn(economy);
        when(economy.getName()).thenReturn("EssentialsEconomy");

        economyManager = new EconomyManager(plugin);
    }

    // ====== Initialization Tests ======

    @Nested
    @DisplayName("Initialization")
    class InitializationTests {

        @Test
        @DisplayName("EconomyManager initializes with Vault integration enabled")
        void economyManagerInitializesWithVaultEnabled() {
            assertTrue(economyManager.isEconomyEnabled());
            assertEquals("EssentialsEconomy", economyManager.getEconomyProvider());
        }

        @Test
        @DisplayName("EconomyManager disables gracefully when Vault not found")
        void economyManagerDisablesWhenVaultNotFound() {
            when(pluginManager.getPlugin("Vault")).thenReturn(null);
            EconomyManager noVaultManager = new EconomyManager(plugin);

            assertFalse(noVaultManager.isEconomyEnabled());
            assertEquals("None", noVaultManager.getEconomyProvider());
        }

        @Test
        @DisplayName("EconomyManager loads configuration values correctly")
        void economyManagerLoadsConfiguration() {
            assertEquals(100.0, economyManager.getBaseFee());
            assertEquals(500.0, economyManager.getCurrencyShopFee());
            assertEquals(0.05, economyManager.getTaxRate());
            assertTrue(economyManager.areFeesEnabled());
            assertTrue(economyManager.areTaxesEnabled());
        }
    }

    // ====== Balance Query Tests ======

    @Nested
    @DisplayName("Balance Queries")
    class BalanceQueryTests {

        @BeforeEach
        void setUpBalance() {
            when(economy.getBalance(any(OfflinePlayer.class))).thenReturn(1000.0);
            when(economy.has(any(OfflinePlayer.class), anyDouble())).thenReturn(true);
        }

        @Test
        @DisplayName("getBalance returns player's current balance")
        void getBalanceReturnsBalance() throws ExecutionException, InterruptedException {
            when(org.bukkit.Bukkit.getOfflinePlayer(testPlayerUuid)).thenReturn(offlinePlayer);
            when(economy.getBalance(offlinePlayer)).thenReturn(1500.0);

            Double balance = economyManager.getBalance(testPlayerUuid).get();

            assertEquals(1500.0, balance);
        }

        @Test
        @DisplayName("has returns true when player has sufficient funds")
        void hasReturnsTrueWhenSufficientFunds() throws ExecutionException, InterruptedException {
            when(org.bukkit.Bukkit.getOfflinePlayer(testPlayerUuid)).thenReturn(offlinePlayer);
            when(economy.has(offlinePlayer, 500.0)).thenReturn(true);

            Boolean has = economyManager.has(testPlayerUuid, 500.0).get();

            assertTrue(has);
        }

        @Test
        @DisplayName("has returns false when player lacks funds")
        void hasReturnsFalseWhenInsufficientFunds() throws ExecutionException, InterruptedException {
            when(org.bukkit.Bukkit.getOfflinePlayer(testPlayerUuid)).thenReturn(offlinePlayer);
            when(economy.has(offlinePlayer, 9999.0)).thenReturn(false);

            Boolean has = economyManager.has(testPlayerUuid, 9999.0).get();

            assertFalse(has);
        }
    }

    // ====== Withdrawal Tests ======

    @Nested
    @DisplayName("Withdrawal Operations")
    class WithdrawalTests {

        @BeforeEach
        void setUpWithdrawal() {
            when(org.bukkit.Bukkit.getOfflinePlayer(testPlayerUuid)).thenReturn(offlinePlayer);
        }

        @Test
        @DisplayName("withdraw succeeds with sufficient balance")
        void withdrawSucceedsWithSufficientBalance() throws ExecutionException, InterruptedException {
            when(economy.has(offlinePlayer, 250.0)).thenReturn(true);
            EconomyResponse response = new EconomyResponse(250.0, 750.0, EconomyResponse.ResponseType.SUCCESS, null);
            when(economy.withdrawPlayer(offlinePlayer, 250.0)).thenReturn(response);
            when(economy.getBalance(offlinePlayer)).thenReturn(750.0);

            EconomyManager.TransactionResult result = economyManager.withdraw(testPlayerUuid, 250.0).get();

            assertTrue(result.success());
            assertEquals(250.0, result.amount());
            assertEquals(750.0, result.newBalance());
        }

        @Test
        @DisplayName("withdraw fails with insufficient balance")
        void withdrawFailsWithInsufficientBalance() throws ExecutionException, InterruptedException {
            when(economy.has(offlinePlayer, 2000.0)).thenReturn(false);

            EconomyManager.TransactionResult result = economyManager.withdraw(testPlayerUuid, 2000.0).get();

            assertFalse(result.success());
            assertEquals("Insufficient funds", result.message());
        }

        @Test
        @DisplayName("withdraw returns economy disabled result when economy disabled")
        void withdrawReturnsDisabledWhenEconomyDisabled() throws ExecutionException, InterruptedException {
            when(pluginManager.getPlugin("Vault")).thenReturn(null);
            EconomyManager noEconomyManager = new EconomyManager(plugin);
            when(org.bukkit.Bukkit.getOfflinePlayer(testPlayerUuid)).thenReturn(offlinePlayer);

            EconomyManager.TransactionResult result = noEconomyManager.withdraw(testPlayerUuid, 100.0).get();

            assertFalse(result.success());
            assertEquals("Economy is not enabled", result.message());
        }
    }

    // ====== Deposit Tests ======

    @Nested
    @DisplayName("Deposit Operations")
    class DepositTests {

        @BeforeEach
        void setUpDeposit() {
            when(org.bukkit.Bukkit.getOfflinePlayer(testPlayerUuid)).thenReturn(offlinePlayer);
        }

        @Test
        @DisplayName("deposit adds funds to player")
        void depositAddsFunds() throws ExecutionException, InterruptedException {
            EconomyResponse response = new EconomyResponse(500.0, 1500.0, EconomyResponse.ResponseType.SUCCESS, null);
            when(economy.depositPlayer(offlinePlayer, 500.0)).thenReturn(response);
            when(economy.getBalance(offlinePlayer)).thenReturn(1500.0);

            EconomyManager.TransactionResult result = economyManager.deposit(testPlayerUuid, 500.0).get();

            assertTrue(result.success());
            assertEquals(500.0, result.amount());
            assertEquals(1500.0, result.newBalance());
        }

        @Test
        @DisplayName("deposit returns economy disabled result when economy disabled")
        void depositReturnsDisabledWhenEconomyDisabled() throws ExecutionException, InterruptedException {
            when(pluginManager.getPlugin("Vault")).thenReturn(null);
            EconomyManager noEconomyManager = new EconomyManager(plugin);
            when(org.bukkit.Bukkit.getOfflinePlayer(testPlayerUuid)).thenReturn(offlinePlayer);

            EconomyManager.TransactionResult result = noEconomyManager.deposit(testPlayerUuid, 100.0).get();

            assertFalse(result.success());
            assertEquals("Economy is not enabled", result.message());
        }
    }

    // ====== Fee Calculation Tests ======

    @Nested
    @DisplayName("Fee Calculations")
    class FeeCalculationTests {

        @Test
        @DisplayName("calculateListingFee returns base fee for barter shops")
        void calculateListingFeeReturnsBaseForBarter() {
            double fee = economyManager.calculateListingFee("BARTER");

            assertEquals(100.0, fee);
        }

        @Test
        @DisplayName("calculateListingFee returns higher fee for currency shops")
        void calculateListingFeeReturnsCurrencyFee() {
            double fee = economyManager.calculateListingFee("CURRENCY");

            assertEquals(500.0, fee);
        }

        @Test
        @DisplayName("calculateListingFee returns zero when fees disabled")
        void calculateListingFeeReturnsZeroWhenDisabled() {
            when(plugin.getConfig().getBoolean("economy.fees.enabled", true)).thenReturn(false);
            EconomyManager disabledManager = new EconomyManager(plugin);

            double fee = disabledManager.calculateListingFee("BARTER");

            assertEquals(0.0, fee);
        }
    }

    // ====== Tax Calculation Tests ======

    @Nested
    @DisplayName("Tax Calculations")
    class TaxCalculationTests {

        @Test
        @DisplayName("calculateTradeTax returns percentage of trade value")
        void calculateTradeTaxReturnsPercentage() {
            double tax = economyManager.calculateTradeTax(1000.0);

            assertEquals(50.0, tax); // 5% of 1000
        }

        @Test
        @DisplayName("calculateTradeTax returns zero for zero or negative trades")
        void calculateTradeTaxReturnsZeroForNegative() {
            assertEquals(0.0, economyManager.calculateTradeTax(0.0));
            assertEquals(0.0, economyManager.calculateTradeTax(-100.0));
        }

        @Test
        @DisplayName("calculateTradeTax returns zero when taxes disabled")
        void calculateTradeTaxReturnsZeroWhenDisabled() {
            when(plugin.getConfig().getBoolean("economy.taxes.enabled", true)).thenReturn(false);
            EconomyManager disabledManager = new EconomyManager(plugin);

            double tax = disabledManager.calculateTradeTax(1000.0);

            assertEquals(0.0, tax);
        }
    }

    // ====== Listing Fee Charge Tests ======

    @Nested
    @DisplayName("Listing Fee Charging")
    class ListingFeeChargeTests {

        @BeforeEach
        void setUpFeeCharging() {
            when(org.bukkit.Bukkit.getOfflinePlayer(testPlayerUuid)).thenReturn(offlinePlayer);
            when(economy.has(offlinePlayer, 100.0)).thenReturn(true);
            EconomyResponse response = new EconomyResponse(100.0, 900.0, EconomyResponse.ResponseType.SUCCESS, null);
            when(economy.withdrawPlayer(offlinePlayer, 100.0)).thenReturn(response);
            when(economy.getBalance(offlinePlayer)).thenReturn(900.0);
        }

        @Test
        @DisplayName("chargeListingFee successfully charges fee")
        void chargeListingFeeSucceeds() throws ExecutionException, InterruptedException {
            EconomyManager.TransactionResult result = economyManager.chargeListingFee(testPlayerUuid, "BARTER").get();

            assertTrue(result.success());
            assertEquals(100.0, result.amount());
        }

        @Test
        @DisplayName("chargeListingFee returns zero when fees disabled")
        void chargeListingFeeReturnsZeroWhenDisabled() throws ExecutionException, InterruptedException {
            when(plugin.getConfig().getBoolean("economy.fees.enabled", true)).thenReturn(false);
            EconomyManager disabledManager = new EconomyManager(plugin);

            EconomyManager.TransactionResult result = disabledManager.chargeListingFee(testPlayerUuid, "BARTER").get();

            assertTrue(result.success());
            assertEquals(0.0, result.amount());
        }
    }

    // ====== Trade Tax Application Tests ======

    @Nested
    @DisplayName("Trade Tax Application")
    class TradeTaxApplicationTests {

        @BeforeEach
        void setUpTaxApplication() {
            when(org.bukkit.Bukkit.getOfflinePlayer(testPlayerUuid)).thenReturn(offlinePlayer);
            when(economy.has(offlinePlayer, 50.0)).thenReturn(true);
            EconomyResponse response = new EconomyResponse(50.0, 950.0, EconomyResponse.ResponseType.SUCCESS, null);
            when(economy.withdrawPlayer(offlinePlayer, 50.0)).thenReturn(response);
            when(economy.getBalance(offlinePlayer)).thenReturn(950.0);
        }

        @Test
        @DisplayName("applyTradeTax successfully applies tax")
        void applyTradeTaxSucceeds() throws ExecutionException, InterruptedException {
            UUID sellerUuid = UUID.randomUUID();

            EconomyManager.TransactionResult result = economyManager.applyTradeTax(testPlayerUuid, sellerUuid, 1000.0).get();

            assertTrue(result.success());
            assertEquals(50.0, result.amount());
        }

        @Test
        @DisplayName("applyTradeTax returns zero when taxes disabled")
        void applyTradeTaxReturnsZeroWhenDisabled() throws ExecutionException, InterruptedException {
            when(plugin.getConfig().getBoolean("economy.taxes.enabled", true)).thenReturn(false);
            EconomyManager disabledManager = new EconomyManager(plugin);
            UUID sellerUuid = UUID.randomUUID();

            EconomyManager.TransactionResult result = disabledManager.applyTradeTax(testPlayerUuid, sellerUuid, 1000.0).get();

            assertTrue(result.success());
            assertEquals(0.0, result.amount());
        }
    }

    // ====== Currency Formatting Tests ======

    @Nested
    @DisplayName("Currency Formatting")
    class CurrencyFormattingTests {

        @Test
        @DisplayName("format delegates to economy plugin")
        void formatDelegatesToEconomy() {
            when(economy.format(100.0)).thenReturn("$100.00");

            String formatted = economyManager.format(100.0);

            assertEquals("$100.00", formatted);
        }

        @Test
        @DisplayName("currencyNameSingular delegates to economy plugin")
        void currencyNameSingularDelegatesToEconomy() {
            when(economy.currencyNameSingular()).thenReturn("Dollar");

            String singular = economyManager.currencyNameSingular();

            assertEquals("Dollar", singular);
        }

        @Test
        @DisplayName("currencyNamePlural delegates to economy plugin")
        void currencyNamePluralDelegatesToEconomy() {
            when(economy.currencyNamePlural()).thenReturn("Dollars");

            String plural = economyManager.currencyNamePlural();

            assertEquals("Dollars", plural);
        }
    }

    // ====== Statistics Collection Tests ======

    @Nested
    @DisplayName("Statistics Collection")
    class StatisticsTests {

        @Test
        @DisplayName("getTotalFeesCollected returns accumulated fees")
        void getTotalFeesCollectedReturnsValue() throws ExecutionException, InterruptedException {
            when(org.bukkit.Bukkit.getOfflinePlayer(testPlayerUuid)).thenReturn(offlinePlayer);
            when(economy.has(offlinePlayer, 100.0)).thenReturn(true);
            EconomyResponse response = new EconomyResponse(100.0, 900.0, EconomyResponse.ResponseType.SUCCESS, null);
            when(economy.withdrawPlayer(offlinePlayer, 100.0)).thenReturn(response);
            when(economy.getBalance(offlinePlayer)).thenReturn(900.0);

            // Charge a fee
            economyManager.chargeListingFee(testPlayerUuid, "BARTER").get();

            Double totalFees = economyManager.getTotalFeesCollected().get();

            assertEquals(100.0, totalFees);
        }

        @Test
        @DisplayName("getTotalTaxesCollected returns accumulated taxes")
        void getTotalTaxesCollectedReturnsValue() throws ExecutionException, InterruptedException {
            when(org.bukkit.Bukkit.getOfflinePlayer(testPlayerUuid)).thenReturn(offlinePlayer);
            when(economy.has(offlinePlayer, 50.0)).thenReturn(true);
            EconomyResponse response = new EconomyResponse(50.0, 950.0, EconomyResponse.ResponseType.SUCCESS, null);
            when(economy.withdrawPlayer(offlinePlayer, 50.0)).thenReturn(response);
            when(economy.getBalance(offlinePlayer)).thenReturn(950.0);
            UUID sellerUuid = UUID.randomUUID();

            // Apply a trade tax
            economyManager.applyTradeTax(testPlayerUuid, sellerUuid, 1000.0).get();

            Double totalTaxes = economyManager.getTotalTaxesCollected().get();

            assertEquals(50.0, totalTaxes);
        }
    }

    // ====== Cleanup Tests ======

    @Nested
    @DisplayName("Cleanup")
    class CleanupTests {

        @Test
        @DisplayName("cleanup executes without errors")
        void cleanupExecutesSuccessfully() {
            assertDoesNotThrow(() -> economyManager.cleanup());
        }
    }
}
