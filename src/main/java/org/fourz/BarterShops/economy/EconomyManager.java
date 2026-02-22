package org.fourz.BarterShops.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.fourz.BarterShops.BarterShops;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manager for economy operations using Vault API.
 * Provides shop listing fees, trade taxes, and currency-based transactions.
 *
 * <p>Graceful fallback: All operations return safe defaults when Vault is unavailable.</p>
 */
public class EconomyManager implements IEconomyService {

    private final BarterShops plugin;
    private final LogManager logger;
    private Economy economy;
    private boolean economyEnabled;

    // Configuration values
    private boolean feesEnabled;
    private double baseFee;
    private double currencyShopFee;
    private boolean taxesEnabled;
    private double taxRate;

    // Statistics tracking (synchronized access)
    private double totalFeesCollected = 0.0;
    private double totalTaxesCollected = 0.0;
    private final Object statsLock = new Object();

    public EconomyManager(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "Economy");
        this.economyEnabled = false;
        this.economy = null;

        setupEconomy();
        loadConfiguration();
    }

    /**
     * Sets up Vault economy integration.
     */
    private void setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            logger.info("Vault not found - economy features disabled");
            economyEnabled = false;
            return;
        }

        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            logger.info("Vault found but no economy provider registered - economy features disabled");
            economyEnabled = false;
            return;
        }

        economy = rsp.getProvider();
        economyEnabled = true;
        logger.info("Economy integration enabled using " + getEconomyProvider());
    }

    /**
     * Loads configuration values from config.yml.
     */
    private void loadConfiguration() {
        feesEnabled = plugin.getConfig().getBoolean("economy.fees.enabled", true);
        baseFee = plugin.getConfig().getDouble("economy.fees.base", 100.0);
        currencyShopFee = plugin.getConfig().getDouble("economy.fees.currency_shop", 500.0);

        taxesEnabled = plugin.getConfig().getBoolean("economy.taxes.enabled", true);
        taxRate = plugin.getConfig().getDouble("economy.taxes.rate", 0.05); // 5% default

        logger.debug("Configuration loaded - Fees: " + feesEnabled + ", Taxes: " + taxesEnabled);
    }

    /**
     * Reloads configuration from config.yml.
     */
    public void reloadConfiguration() {
        loadConfiguration();
        logger.info("Economy configuration reloaded");
    }

    @Override
    public boolean isEconomyEnabled() {
        return economyEnabled;
    }

    @Override
    public String getEconomyProvider() {
        return economyEnabled ? economy.getName() : "None";
    }

    @Override
    public CompletableFuture<Double> getBalance(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (!economyEnabled) return 0.0;

            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
            return economy.getBalance(player);
        });
    }

    @Override
    public CompletableFuture<Boolean> has(UUID playerUuid, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            if (!economyEnabled) return true; // Don't block if economy disabled

            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
            return economy.has(player, amount);
        });
    }

    @Override
    public CompletableFuture<TransactionResult> withdraw(UUID playerUuid, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            if (!economyEnabled) {
                return TransactionResult.economyDisabled();
            }

            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);

            if (!economy.has(player, amount)) {
                return TransactionResult.failure("Insufficient funds");
            }

            var response = economy.withdrawPlayer(player, amount);
            if (response.transactionSuccess()) {
                double newBalance = economy.getBalance(player);
                logger.debug("Withdrew " + format(amount) + " from " + player.getName());
                return TransactionResult.success(amount, newBalance);
            } else {
                return TransactionResult.failure(response.errorMessage);
            }
        });
    }

    @Override
    public CompletableFuture<TransactionResult> deposit(UUID playerUuid, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            if (!economyEnabled) {
                return TransactionResult.economyDisabled();
            }

            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
            var response = economy.depositPlayer(player, amount);

            if (response.transactionSuccess()) {
                double newBalance = economy.getBalance(player);
                logger.debug("Deposited " + format(amount) + " to " + player.getName());
                return TransactionResult.success(amount, newBalance);
            } else {
                return TransactionResult.failure(response.errorMessage);
            }
        });
    }

    @Override
    public double calculateListingFee(String shopType) {
        if (!economyEnabled || !feesEnabled) {
            return 0.0;
        }

        return switch (shopType.toUpperCase()) {
            case "CURRENCY", "MONEY" -> currencyShopFee;
            default -> baseFee;
        };
    }

    @Override
    public CompletableFuture<TransactionResult> chargeListingFee(UUID playerUuid, String shopType) {
        double fee = calculateListingFee(shopType);

        if (fee == 0.0) {
            return CompletableFuture.completedFuture(
                TransactionResult.success(0.0, 0.0)
            );
        }

        return withdraw(playerUuid, fee).thenApply(result -> {
            if (result.success()) {
                synchronized (statsLock) {
                    totalFeesCollected += fee;
                }
                logger.info("Charged listing fee of " + format(fee) + " for " + shopType + " shop");
            }
            return result;
        });
    }

    @Override
    public double calculateTradeTax(double tradeValue) {
        if (!economyEnabled || !taxesEnabled || tradeValue <= 0) {
            return 0.0;
        }

        return tradeValue * taxRate;
    }

    @Override
    public CompletableFuture<TransactionResult> applyTradeTax(UUID buyerUuid, UUID sellerUuid, double tradeValue) {
        double tax = calculateTradeTax(tradeValue);

        if (tax == 0.0) {
            return CompletableFuture.completedFuture(
                TransactionResult.success(0.0, 0.0)
            );
        }

        // Withdraw tax from buyer
        return withdraw(buyerUuid, tax).thenApply(result -> {
            if (result.success()) {
                synchronized (statsLock) {
                    totalTaxesCollected += tax;
                }
                logger.debug("Applied trade tax of " + format(tax) + " on " + format(tradeValue) + " trade");
            }
            return result;
        });
    }

    @Override
    public String format(double amount) {
        if (!economyEnabled) {
            return String.format("$%.2f", amount);
        }
        return economy.format(amount);
    }

    @Override
    public String currencyNameSingular() {
        return economyEnabled ? economy.currencyNameSingular() : "Dollar";
    }

    @Override
    public String currencyNamePlural() {
        return economyEnabled ? economy.currencyNamePlural() : "Dollars";
    }

    @Override
    public CompletableFuture<Double> getTotalFeesCollected() {
        synchronized (statsLock) {
            return CompletableFuture.completedFuture(totalFeesCollected);
        }
    }

    @Override
    public CompletableFuture<Double> getTotalTaxesCollected() {
        synchronized (statsLock) {
            return CompletableFuture.completedFuture(totalTaxesCollected);
        }
    }

    /**
     * Checks if fees are enabled.
     */
    public boolean areFeesEnabled() {
        return feesEnabled && economyEnabled;
    }

    /**
     * Checks if taxes are enabled.
     */
    public boolean areTaxesEnabled() {
        return taxesEnabled && economyEnabled;
    }

    /**
     * Gets the current tax rate.
     */
    public double getTaxRate() {
        return taxRate;
    }

    /**
     * Gets the base listing fee.
     */
    public double getBaseFee() {
        return baseFee;
    }

    /**
     * Gets the currency shop listing fee.
     */
    public double getCurrencyShopFee() {
        return currencyShopFee;
    }

    /**
     * Cleanup method for plugin shutdown.
     */
    public void cleanup() {
        synchronized (statsLock) {
            logger.info("Economy manager shutdown - Fees collected: " + format(totalFeesCollected) +
                       ", Taxes collected: " + format(totalTaxesCollected));
        }
    }
}
