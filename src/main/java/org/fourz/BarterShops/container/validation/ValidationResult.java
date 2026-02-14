package org.fourz.BarterShops.container.validation;

import org.bukkit.inventory.ItemStack;

/**
 * Result of an inventory validation operation.
 * Immutable DTO for validation results.
 */
public record ValidationResult(
    boolean valid,
    String reason,
    ItemStack invalidItem
) {
    /**
     * Creates a successful validation result.
     */
    public static ValidationResult success() {
        return new ValidationResult(true, null, null);
    }

    /**
     * Creates a failed validation result.
     */
    public static ValidationResult failure(String reason, ItemStack invalidItem) {
        return new ValidationResult(false, reason, invalidItem);
    }

    /**
     * Checks if validation passed.
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Gets the failure reason (null if valid).
     */
    public String getReason() {
        return reason;
    }

    /**
     * Gets the invalid item that triggered the failure (null if valid).
     */
    public ItemStack getInvalidItem() {
        return invalidItem;
    }
}
