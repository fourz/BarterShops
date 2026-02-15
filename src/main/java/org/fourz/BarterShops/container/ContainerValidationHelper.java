package org.fourz.BarterShops.container;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.container.validation.ValidationResult;
import org.fourz.BarterShops.sign.BarterSign;

/**
 * UNIFIED: Container validation with user context (owner vs customer).
 * Ensures consistent validation rules across all container access patterns.
 *
 * Design principle: Validate differently based on user role:
 * - OWNER: Can place offering items (for restocking) + all payment items
 * - CUSTOMER: Can only place payment items required for trades
 *
 * This centralizes the validation logic so there's one source of truth.
 */
public class ContainerValidationHelper {

    /**
     * UNIFIED METHOD: Validate item for a specific player in a shop context.
     * Applies different validation rules based on whether player is owner or customer.
     *
     * @param item The item being placed
     * @param player The player placing the item (can be null for automated events like hoppers)
     * @param barterSign The shop data containing owner info and configuration
     * @param shopContainer The container being validated against
     * @return ValidationResult indicating success/failure with reason
     */
    public static ValidationResult validateItemForUser(
            ItemStack item,
            Player player,
            BarterSign barterSign,
            ShopContainer shopContainer) {

        // If no player (automated system like hopper), use standard validation
        if (player == null) {
            return shopContainer.validateItem(item);
        }

        // OWNER: Has broader permissions (restocking allowed)
        if (isOwner(player, barterSign)) {
            return validateOwnerItem(item, barterSign, shopContainer);
        }

        // CUSTOMER: Restricted to payment items only
        return validateCustomerItem(item, barterSign, shopContainer);
    }

    /**
     * Checks if player is the shop owner.
     */
    private static boolean isOwner(Player player, BarterSign barterSign) {
        return barterSign.getOwner().equals(player.getUniqueId());
    }

    /**
     * OWNER validation: Allow offering items (for restocking) + all payment items.
     * Owners need flexibility to restock their offerings without validation blocking them.
     */
    private static ValidationResult validateOwnerItem(
            ItemStack item,
            BarterSign barterSign,
            ShopContainer shopContainer) {

        // Owner can place offering item (for restocking)
        if (barterSign.getItemOffering() != null &&
            item.getType() == barterSign.getItemOffering().getType()) {
            return ValidationResult.success();
        }

        // Owner can place any payment option items
        if (barterSign.isPaymentAccepted(item)) {
            return ValidationResult.success();
        }

        // Otherwise apply standard validation rules (shouldn't normally block owner)
        return shopContainer.validateItem(item);
    }

    /**
     * CUSTOMER validation: Only allow payment items required for trades.
     * Customers cannot place arbitrary items or offering items in the container.
     */
    private static ValidationResult validateCustomerItem(
            ItemStack item,
            BarterSign barterSign,
            ShopContainer shopContainer) {

        // Customer can ONLY place payment items
        if (barterSign.isPaymentAccepted(item)) {
            return ValidationResult.success();
        }

        // Reject offering items - customers shouldn't place what they're supposed to receive
        if (barterSign.getItemOffering() != null &&
            item.getType() == barterSign.getItemOffering().getType()) {
            String offeringName = barterSign.getItemOffering().getType().name().toLowerCase().replace('_', ' ');
            return ValidationResult.failure(
                "Can't place offering here - that's what you'll receive!",
                item
            );
        }

        // Reject any other items
        return ValidationResult.failure(
            "Only payment items allowed in this shop",
            item
        );
    }
}
