package org.fourz.BarterShops.container.validation;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Validates that containers only hold unstackable items (enchanted items, tools, armor, etc).
 * Used for BARTER shops with unstackable offerings.
 * Prevents stacking items that shouldn't be stacked.
 */
public class UnstackableItemOnlyRule implements ValidationRule {
    /**
     * Creates an unstackable-only validation rule.
     */
    public UnstackableItemOnlyRule() {
    }

    @Override
    public ValidationResult validate(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return ValidationResult.success(); // Empty slot is valid
        }

        // Check if item is stackable (maxStackSize > 1)
        if (item.getMaxStackSize() > 1) {
            return ValidationResult.failure(
                "This shop only accepts unstackable items (enchanted, tools, armor)",
                item
            );
        }

        return ValidationResult.success();
    }

    @Override
    public String getRuleName() {
        return "UnstackableItemOnlyRule";
    }

    @Override
    public String getDescription() {
        return "Only unstackable items allowed (enchanted, tools, armor, etc)";
    }

    /**
     * Checks if an item is unstackable (max stack size = 1).
     */
    public static boolean isUnstackable(ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getMaxStackSize() == 1;
    }

    /**
     * Checks if an item is stackable (max stack size > 1).
     */
    public static boolean isStackable(ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getMaxStackSize() > 1;
    }
}
