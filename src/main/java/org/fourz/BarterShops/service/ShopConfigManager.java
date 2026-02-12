package org.fourz.BarterShops.service;

import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.repository.IShopRepository;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.concurrent.CompletableFuture;

/**
 * Manages persistence of BarterSign configuration to database.
 * Saves offering items, prices, and payment options asynchronously.
 */
public class ShopConfigManager {

    private final BarterShops plugin;
    private final IShopRepository repository;
    private final LogManager logger;

    public ShopConfigManager(BarterShops plugin, IShopRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        this.logger = LogManager.getInstance(plugin, "ShopConfigManager");
    }

    /**
     * Save BarterSign configuration to database.
     * Called after owner makes configuration changes (setting offering, price, payments).
     * Loads existing shop data first, updates only configuration fields, then saves.
     * Runs asynchronously to avoid blocking the server thread.
     *
     * @param barterSign The BarterSign with updated configuration
     * @param shopId The database shop ID to update
     * @return CompletableFuture that completes when save is done
     */
    public CompletableFuture<Void> saveSignConfiguration(BarterSign barterSign, int shopId) {
        if (shopId <= 0) {
            logger.debug("Cannot save config for shop with ID: " + shopId);
            return CompletableFuture.completedFuture(null);
        }

        // Load existing shop data from database to preserve required fields (ownerUuid, shopName, locations, etc.)
        return repository.findById(shopId)
                .thenCompose(existingShop -> {
                    if (existingShop.isEmpty()) {
                        logger.debug("Cannot save config for shop ID " + shopId + ": shop not found in database");
                        return CompletableFuture.completedFuture(null);
                    }

                    ShopDataDTO existing = existingShop.get();

                    // Build new DTO preserving all existing shop data, updating only configuration fields
                    ShopDataDTO.Builder builder = ShopDataDTO.builder()
                            .shopId(existing.shopId())
                            .ownerUuid(existing.ownerUuid())  // CRITICAL: Preserve owner
                            .shopName(existing.shopName())
                            .shopType(existing.shopType())
                            .signLocation(existing.locationWorld(), existing.locationX(), existing.locationY(), existing.locationZ())
                            .chestLocation(existing.chestLocationWorld(), existing.chestLocationX(), existing.chestLocationY(), existing.chestLocationZ())
                            .isActive(existing.isActive())
                            .createdAt(existing.createdAt())
                            .lastModified(new java.sql.Timestamp(System.currentTimeMillis()));  // Update timestamp

                    // Merge configuration fields from BarterSign (only these change)
                    if (barterSign.getItemOffering() != null) {
                        builder.configuredOffering(barterSign.getItemOffering());
                    }

                    if (barterSign.getPriceItem() != null) {
                        builder.configuredPrice(barterSign.getPriceItem(), barterSign.getPriceAmount());
                    }

                    if (!barterSign.getAcceptedPayments().isEmpty()) {
                        builder.acceptedPayments(barterSign.getAcceptedPayments());
                    }

                    if (barterSign.getLockedItemType() != null) {
                        builder.lockedItemType(barterSign.getLockedItemType());
                    }

                    builder.isStackable(barterSign.isStackable())
                           .typeDetected(barterSign.isTypeDetected())
                           .isAdminShop(barterSign.isAdmin());

                    ShopDataDTO dto = builder.build();

                    // Save asynchronously
                    return repository.save(dto)
                            .thenAccept(saved -> {
                                logger.debug("Saved config for shop " + shopId + ": offering=" +
                                        (barterSign.getItemOffering() != null ? barterSign.getItemOffering().getType() : "none") +
                                        ", payments=" + barterSign.getAcceptedPayments().size());
                            });
                })
                .exceptionally(e -> {
                    logger.error("Failed to save shop config for shop " + shopId + ": " + e.getMessage());
                    return null;
                });
    }

    /**
     * Load BarterSign configuration from ShopDataDTO and populate the BarterSign.
     * Called when creating a BarterSign from database data.
     *
     * @param barterSign The BarterSign to populate
     * @param dto The ShopDataDTO containing persisted configuration
     */
    public void loadSignConfiguration(BarterSign barterSign, ShopDataDTO dto) {
        // Restore offering
        var offering = dto.getConfiguredOffering();
        if (offering != null) {
            barterSign.configureStackableShop(offering, offering.getAmount());
        }

        // Restore price
        var priceItem = dto.getConfiguredPriceItem();
        int priceAmount = dto.getConfiguredPriceAmount();
        if (priceItem != null && priceAmount > 0) {
            barterSign.configurePrice(priceItem, priceAmount);
        }

        // Restore accepted payments
        var payments = dto.getAcceptedPayments();
        if (!payments.isEmpty()) {
            for (var payment : payments) {
                barterSign.addPaymentOption(payment, payment.getAmount());
            }
        }

        // Restore configuration flags
        barterSign.setStackable(dto.isStackable());
        barterSign.setTypeDetected(dto.isTypeDetected());
        barterSign.setAdmin(dto.isAdminShop());

        // Restore locked item type (if applicable)
        var lockedType = dto.getLockedItemType();
        if (lockedType != null) {
            barterSign.setLockedItemType(lockedType);
        }

        logger.debug("Loaded config for shop " + dto.shopId() +
                ": offering=" + (offering != null ? offering.getType() : "none") +
                ", stackable=" + dto.isStackable() +
                ", typeDetected=" + dto.isTypeDetected());
    }
}
