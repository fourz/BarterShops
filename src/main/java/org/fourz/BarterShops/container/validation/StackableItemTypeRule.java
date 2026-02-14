package org.fourz.BarterShops.container.validation;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Validates that items are of allowed types for stackable item shops.
 * Used for BARTER shops with a single offering item type.
 * Enforces type locking (bug-36 fix).
 */
public class StackableItemTypeRule implements ValidationRule {
    private final Material allowedType;
    private final String displayName;

    /**
     * Creates a type validation rule for a specific material.
     *
     * @param allowedType The only material type allowed in this shop
     * @param displayName Human-readable name for UI messages
     */
    public StackableItemTypeRule(Material allowedType, String displayName) {
        this.allowedType = allowedType;
        this.displayName = displayName;
    }

    @Override
    public ValidationResult validate(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return ValidationResult.success(); // Empty slot is valid
        }

        if (item.getType() != allowedType) {
            return ValidationResult.failure(
                "Shop only accepts " + displayName + " (" + allowedType + ")",
                item
            );
        }

        return ValidationResult.success();
    }

    @Override
    public String getRuleName() {
        return "StackableItemTypeRule";
    }

    @Override
    public String getDescription() {
        return "Type locking: Only " + displayName + " items allowed";
    }

    /**
     * Gets the allowed material type.
     */
    public Material getAllowedType() {
        return allowedType;
    }

    /**
     * Gets the human-readable display name.
     */
    public String getDisplayName() {
        return displayName;
    }
}
