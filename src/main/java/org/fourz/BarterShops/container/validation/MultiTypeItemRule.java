package org.fourz.BarterShops.container.validation;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates that items are of allowed types for multi-payment shops.
 * Used for BARTER shops with multiple payment options and offering items.
 * Accepts any combination of configured payment types + offering type.
 * Fixes bug-45: validation message backwards logic (was showing offering, should show payments).
 */
public class MultiTypeItemRule implements ValidationRule {
    private final Set<Material> allowedTypes;

    /**
     * Creates a type validation rule for multiple materials.
     *
     * @param allowedTypes The material types allowed in this shop (payment types + offering type)
     */
    public MultiTypeItemRule(Set<Material> allowedTypes) {
        this.allowedTypes = allowedTypes;
    }

    @Override
    public ValidationResult validate(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return ValidationResult.success(); // Empty slot is valid
        }

        if (!allowedTypes.contains(item.getType())) {
            // Generate user-friendly list of allowed types
            String allowedNames = allowedTypes.stream()
                .map(m -> m.name().toLowerCase().replace('_', ' '))
                .sorted()
                .collect(Collectors.joining(", "));

            return ValidationResult.failure(
                "Shop accepts: " + allowedNames,
                item
            );
        }

        return ValidationResult.success();
    }

    @Override
    public String getRuleName() {
        return "MultiTypeItemRule";
    }

    @Override
    public String getDescription() {
        return "Multi-type validation: Allows " + allowedTypes.size() + " material types";
    }

    /**
     * Gets the allowed material types.
     */
    public Set<Material> getAllowedTypes() {
        return allowedTypes;
    }
}
