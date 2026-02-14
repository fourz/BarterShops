package org.fourz.BarterShops.container;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.container.validation.ValidationResult;
import org.fourz.BarterShops.container.validation.ValidationRule;

import java.util.*;

/**
 * Wrapper around Bukkit Container with validation rules, type safety, and access control.
 * Enforces type locking and prevents invalid items from entering the shop container.
 */
public class ShopContainer {
    private final Container container;
    private final Location location;
    private final UUID shopId;
    private final List<ValidationRule> validationRules;
    private final long createdAt;

    /**
     * Creates a shop container wrapper.
     *
     * @param container The Bukkit container block
     * @param shopId The ID of the shop this container belongs to
     * @param validationRules Rules to enforce on inventory operations
     */
    public ShopContainer(Container container, UUID shopId, List<ValidationRule> validationRules) {
        this.container = container;
        this.location = container.getLocation();
        this.shopId = shopId;
        this.validationRules = new ArrayList<>(validationRules);
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * Gets the Bukkit container (chest, barrel, etc).
     */
    public Container getContainer() {
        return container;
    }

    /**
     * Gets the container's location.
     */
    public Location getLocation() {
        return location;
    }

    /**
     * Gets the shop ID this container is associated with.
     */
    public UUID getShopId() {
        return shopId;
    }

    /**
     * Gets the container's inventory.
     */
    public Inventory getInventory() {
        return container.getInventory();
    }

    /**
     * Validates an item against all configured rules.
     * Returns first failure encountered.
     *
     * @param item The item to validate
     * @return ValidationResult with success/failure
     */
    public ValidationResult validateItem(ItemStack item) {
        for (ValidationRule rule : validationRules) {
            ValidationResult result = rule.validate(item);
            if (!result.isValid()) {
                return result;
            }
        }
        return ValidationResult.success();
    }

    /**
     * Validates all items in the container.
     *
     * @return First invalid item's ValidationResult, or success if all valid
     */
    public ValidationResult validateAllItems() {
        Inventory inv = getInventory();
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                ValidationResult result = validateItem(item);
                if (!result.isValid()) {
                    return result;
                }
            }
        }
        return ValidationResult.success();
    }

    /**
     * Adds a validation rule to this container.
     * Used for dynamic rule changes (e.g., type changes).
     */
    public void addValidationRule(ValidationRule rule) {
        validationRules.add(rule);
    }

    /**
     * Removes a validation rule from this container.
     */
    public void removeValidationRule(ValidationRule rule) {
        validationRules.remove(rule);
    }

    /**
     * Clears all validation rules.
     */
    public void clearValidationRules() {
        validationRules.clear();
    }

    /**
     * Gets all validation rules for this container.
     */
    public List<ValidationRule> getValidationRules() {
        return new ArrayList<>(validationRules);
    }

    /**
     * Gets the time this container was created (milliseconds since epoch).
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Gets the age of this container in milliseconds.
     */
    public long getAgeMillis() {
        return System.currentTimeMillis() - createdAt;
    }

    /**
     * Checks if this container's block still exists and is a container.
     */
    public boolean isValid() {
        return location.getBlock().getState() instanceof Container;
    }

    /**
     * Gets a debugging string representation.
     */
    @Override
    public String toString() {
        return String.format("ShopContainer[shop=%s, loc=%s, rules=%d]",
            shopId.toString().substring(0, 8),
            location,
            validationRules.size()
        );
    }
}
