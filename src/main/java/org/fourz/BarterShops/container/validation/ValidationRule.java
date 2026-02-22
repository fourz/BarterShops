package org.fourz.BarterShops.container.validation;

import org.bukkit.inventory.ItemStack;

/**
 * Strategy interface for inventory validation rules.
 * Implementers define specific validation logic (type checking, stackability, etc).
 */
public interface ValidationRule {
    /**
     * Validates an item against this rule.
     *
     * @param item The item to validate
     * @return ValidationResult with success/failure and reason
     */
    ValidationResult validate(ItemStack item);

    /**
     * Gets the name of this validation rule for debugging.
     */
    String getRuleName();

    /**
     * Gets a human-readable description of what this rule enforces.
     */
    String getDescription();
}
