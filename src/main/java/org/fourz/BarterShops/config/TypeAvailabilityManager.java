package org.fourz.BarterShops.config;

import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.economy.EconomyManager;
import org.fourz.BarterShops.sign.SignType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages availability of ShopType and SignType based on configuration
 * and runtime dependencies (Vault economy).
 *
 * <p>Caches enabled types for fast lookup during cycling and validation.</p>
 */
public class TypeAvailabilityManager {
    private final BarterShops plugin;
    private final LogManager logger;
    private final ConfigManager configManager;
    private final EconomyManager economyManager;

    // Cached enabled types (updated on reload)
    private Set<ShopDataDTO.ShopType> enabledShopTypes;
    private Set<SignType> enabledSignTypes;

    public TypeAvailabilityManager(BarterShops plugin, ConfigManager configManager,
                                   EconomyManager economyManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "TypeAvailability");
        this.configManager = configManager;
        this.economyManager = economyManager;

        loadEnabledTypes();
    }

    /**
     * Loads enabled types from config and applies runtime constraints.
     * Called during initialization and after config reload.
     */
    public void loadEnabledTypes() {
        // Load from config
        Set<ShopDataDTO.ShopType> configShopTypes = new HashSet<>(configManager.getEnabledShopTypes());
        Set<SignType> configSignTypes = new HashSet<>(configManager.getEnabledSignTypes());

        // Apply economy constraint: auto-disable SELL/BUY if Vault missing
        if (!economyManager.isEconomyEnabled()) {
            boolean removed = configShopTypes.remove(ShopDataDTO.ShopType.SELL) |
                             configShopTypes.remove(ShopDataDTO.ShopType.BUY);
            if (removed) {
                logger.info("Auto-disabled SELL/BUY shop types (Vault not available)");
            }
        }

        // Ensure at least one type is enabled (safety fallback)
        if (configShopTypes.isEmpty()) {
            logger.warning("No shop types enabled! Force-enabling BARTER");
            configShopTypes.add(ShopDataDTO.ShopType.BARTER);
        }
        if (configSignTypes.isEmpty()) {
            logger.warning("No sign types enabled! Force-enabling BARTER");
            configSignTypes.add(SignType.BARTER);
        }

        this.enabledShopTypes = Collections.unmodifiableSet(configShopTypes);
        this.enabledSignTypes = Collections.unmodifiableSet(configSignTypes);

        logEnabledTypes();
    }

    private void logEnabledTypes() {
        String shopTypesStr = enabledShopTypes.stream()
            .map(Enum::name)
            .collect(Collectors.joining(", "));
        String signTypesStr = enabledSignTypes.stream()
            .map(Enum::name)
            .collect(Collectors.joining(", "));

        logger.info("Enabled shop types: " + shopTypesStr);
        logger.info("Enabled sign types: " + signTypesStr);
    }

    // Query methods
    public boolean isShopTypeAvailable(ShopDataDTO.ShopType type) {
        return enabledShopTypes.contains(type);
    }

    public boolean isSignTypeAvailable(SignType type) {
        return enabledSignTypes.contains(type);
    }

    public Set<ShopDataDTO.ShopType> getAvailableShopTypes() {
        return enabledShopTypes;
    }

    public Set<SignType> getAvailableSignTypes() {
        return enabledSignTypes;
    }

    /**
     * Gets the next enabled SignType in cycling order.
     * Skips disabled types.
     *
     * @param current Current SignType
     * @return Next enabled SignType in cycle
     */
    public SignType getNextSignType(SignType current) {
        List<SignType> availableList = new ArrayList<>(enabledSignTypes);
        if (availableList.isEmpty()) return current;

        int currentIndex = availableList.indexOf(current);
        if (currentIndex == -1) {
            // Current type not in list, return first available
            return availableList.get(0);
        }

        int nextIndex = (currentIndex + 1) % availableList.size();
        return availableList.get(nextIndex);
    }

    /**
     * Validates if a shop can be created with the given type.
     *
     * @param type ShopType to validate
     * @return Validation result with error message if invalid
     */
    public ValidationResult validateShopType(ShopDataDTO.ShopType type) {
        if (!isShopTypeAvailable(type)) {
            String reason = getDisabledReason(type);
            return new ValidationResult(false,
                "Shop type " + type.name() + " is not available. " + reason);
        }
        return new ValidationResult(true, null);
    }

    private String getDisabledReason(ShopDataDTO.ShopType type) {
        if (type == ShopDataDTO.ShopType.SELL || type == ShopDataDTO.ShopType.BUY) {
            if (!economyManager.isEconomyEnabled()) {
                return "Requires Vault economy plugin.";
            }
        }
        return "Disabled in configuration.";
    }

    /**
     * Simple validation result holder.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        public ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
