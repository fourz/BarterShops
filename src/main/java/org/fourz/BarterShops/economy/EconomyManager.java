package org.fourz.BarterShops.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
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
    private final boolean vaultPresent;

    /**
     * Current Vault provider, or null when none is bound. Volatile because async transaction
     * lambdas read it; each operation copies it to a local once so it never sees a provider that
     * is dropped half-way through (#2444).
     */
    private volatile Economy economy;
    private volatile String providerName;
    private volatile Plugin providerPlugin;
    private volatile String lastProviderName;
    private volatile Runnable providerChangeListener;

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
        this.economy = null;
        this.vaultPresent = plugin.getServer().getPluginManager().getPlugin("Vault") != null;

        setupEconomy();
        loadConfiguration();
    }

    /**
     * Sets up Vault economy integration.
     */
    private void setupEconomy() {
        if (!vaultPresent) {
            logger.info("Vault not found - economy features disabled");
            return;
        }

        refreshProvider(null);
        if (economy == null) {
            logger.info("Vault found but no economy provider registered - economy features disabled");
        }
    }

    /**
     * True when Vault is installed. The provider listener is only registered in that case.
     */
    public boolean isVaultPresent() {
        return vaultPresent;
    }

    /**
     * Re-resolves the highest-priority Vault {@link Economy} provider and swaps the cached one.
     *
     * <p>Called at enable and on every Vault economy {@code ServiceRegisterEvent},
     * {@code ServiceUnregisterEvent} and on {@code PluginDisableEvent} of the owning plugin (#2444).
     * Logs only on a transition: one WARN when the bound provider goes away, one INFO when a
     * provider is bound. A repeat event with no change logs nothing.</p>
     *
     * @param excluding a plugin whose registrations must be skipped (it is disabling but its
     *                  services are not unregistered yet), or null
     * @return true when the bound provider changed
     */
    public synchronized boolean refreshProvider(Plugin excluding) {
        if (!vaultPresent) {
            return false;
        }

        RegisteredServiceProvider<Economy> chosen = null;
        var registrations = plugin.getServer().getServicesManager().getRegistrations(Economy.class);
        if (registrations != null) {
            // Bukkit returns registrations highest priority first.
            for (RegisteredServiceProvider<Economy> rsp : registrations) {
                if (rsp == null || rsp.getProvider() == null) continue;
                if (excluding != null && excluding.equals(rsp.getPlugin())) continue;
                chosen = rsp;
                break;
            }
        }

        Economy next = chosen == null ? null : chosen.getProvider();
        Economy previous = economy;
        if (next == previous) {
            return false;
        }

        if (next == null) {
            String gone = providerName;
            economy = null;
            providerName = null;
            providerPlugin = null;
            lastProviderName = gone;
            logger.warning("Economy provider " + gone + " unregistered - economy features disabled");
        } else {
            String name = safeName(next);
            economy = next;
            providerName = name;
            providerPlugin = chosen.getPlugin();
            logger.info("Economy integration enabled using " + name);
        }

        Runnable listener = providerChangeListener;
        if (listener != null) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                logger.error("Economy provider change listener failed", e);
            }
        }
        return true;
    }

    /**
     * True when the bound provider was registered by {@code owner}.
     */
    public boolean isProvidedBy(Plugin owner) {
        Plugin current = providerPlugin;
        return owner != null && current != null && owner.equals(current);
    }

    /**
     * Runs after every provider change (bind, swap or drop). Used to re-apply the SELL/BUY
     * shop-type constraint.
     */
    public void setProviderChangeListener(Runnable listener) {
        this.providerChangeListener = listener;
    }

    /**
     * Player/console text for a refused economy action.
     */
    public String getUnavailableMessage() {
        if (!vaultPresent) {
            return "Economy features are disabled (Vault not found)";
        }
        String last = lastProviderName;
        if (last != null) {
            return "Economy features are disabled (economy provider " + last
                    + " is not loaded - try again after it is back)";
        }
        return "Economy features are disabled (no Vault economy provider registered)";
    }

    private static String safeName(Economy eco) {
        try {
            String name = eco.getName();
            return name == null ? eco.getClass().getSimpleName() : name;
        } catch (RuntimeException e) {
            return eco.getClass().getSimpleName();
        }
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
        return economy != null;
    }

    @Override
    public String getEconomyProvider() {
        String name = providerName;
        return economy != null && name != null ? name : "None";
    }

    @Override
    public CompletableFuture<Double> getBalance(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Economy eco = economy;
            if (eco == null) return 0.0;

            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
            return eco.getBalance(player);
        });
    }

    @Override
    public CompletableFuture<Boolean> has(UUID playerUuid, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            Economy eco = economy;
            if (eco == null) return true; // Don't block if economy disabled

            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
            return eco.has(player, amount);
        });
    }

    @Override
    public CompletableFuture<TransactionResult> withdraw(UUID playerUuid, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            Economy eco = economy;
            if (eco == null) {
                return TransactionResult.economyDisabled();
            }

            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);

            if (!eco.has(player, amount)) {
                return TransactionResult.failure("Insufficient funds");
            }

            var response = eco.withdrawPlayer(player, amount);
            if (response.transactionSuccess()) {
                double newBalance = eco.getBalance(player);
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
            Economy eco = economy;
            if (eco == null) {
                return TransactionResult.economyDisabled();
            }

            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
            var response = eco.depositPlayer(player, amount);

            if (response.transactionSuccess()) {
                double newBalance = eco.getBalance(player);
                logger.debug("Deposited " + format(amount) + " to " + player.getName());
                return TransactionResult.success(amount, newBalance);
            } else {
                return TransactionResult.failure(response.errorMessage);
            }
        });
    }

    @Override
    public double calculateListingFee(String shopType) {
        if (economy == null || !feesEnabled) {
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
        if (economy == null || !taxesEnabled || tradeValue <= 0) {
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
        Economy eco = economy;
        if (eco == null) {
            return String.format("$%.2f", amount);
        }
        return eco.format(amount);
    }

    @Override
    public String currencyNameSingular() {
        Economy eco = economy;
        return eco != null ? eco.currencyNameSingular() : "Dollar";
    }

    @Override
    public String currencyNamePlural() {
        Economy eco = economy;
        return eco != null ? eco.currencyNamePlural() : "Dollars";
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
        return feesEnabled && economy != null;
    }

    /**
     * Checks if taxes are enabled.
     */
    public boolean areTaxesEnabled() {
        return taxesEnabled && economy != null;
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
