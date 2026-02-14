package org.fourz.BarterShops.container.factory;

import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.container.ShopContainer;
import org.fourz.BarterShops.container.validation.StackableItemTypeRule;
import org.fourz.BarterShops.container.validation.UnstackableItemOnlyRule;
import org.fourz.BarterShops.container.validation.ValidationRule;

import java.util.*;

/**
 * Factory for creating ShopContainer instances with appropriate validation rules.
 * Encapsulates container creation logic and rule selection based on shop type.
 */
public class ShopContainerFactory {
    /**
     * Creates a shop container with no validation rules.
     * Used for shops that accept any item type.
     *
     * @param container The Bukkit container block
     * @param shopId The ID of the shop
     * @return ShopContainer with empty rules
     */
    public static ShopContainer createContainer(Container container, UUID shopId) {
        return new ShopContainer(container, shopId, new ArrayList<>());
    }

    /**
     * Creates a shop container with type locking for stackable items.
     * Used for BARTER shops with a single stackable offering item.
     *
     * @param container The Bukkit container block
     * @param shopId The ID of the shop
     * @param itemType The material type to lock to
     * @param itemName Human-readable name for error messages
     * @return ShopContainer with StackableItemTypeRule
     */
    public static ShopContainer createStackableTypeLocked(
        Container container,
        UUID shopId,
        Material itemType,
        String itemName
    ) {
        List<ValidationRule> rules = new ArrayList<>();
        rules.add(new StackableItemTypeRule(itemType, itemName));
        return new ShopContainer(container, shopId, rules);
    }

    /**
     * Creates a shop container with type locking based on an existing item.
     * Used for BARTER shops with a single stackable offering item.
     *
     * @param container The Bukkit container block
     * @param shopId The ID of the shop
     * @param referenceItem The item to lock the type to
     * @return ShopContainer with StackableItemTypeRule based on item's material
     */
    public static ShopContainer createStackableTypeLocked(
        Container container,
        UUID shopId,
        ItemStack referenceItem
    ) {
        if (referenceItem == null || referenceItem.getType() == Material.AIR) {
            return createContainer(container, shopId);
        }

        Material itemType = referenceItem.getType();
        String itemName = itemType.toString().toLowerCase().replace('_', ' ');
        return createStackableTypeLocked(container, shopId, itemType, itemName);
    }

    /**
     * Creates a shop container that only allows unstackable items.
     * Used for BARTER shops with unstackable offerings (enchanted items, tools, armor).
     *
     * @param container The Bukkit container block
     * @param shopId The ID of the shop
     * @return ShopContainer with UnstackableItemOnlyRule
     */
    public static ShopContainer createUnstackableOnly(
        Container container,
        UUID shopId
    ) {
        List<ValidationRule> rules = new ArrayList<>();
        rules.add(new UnstackableItemOnlyRule());
        return new ShopContainer(container, shopId, rules);
    }

    /**
     * Creates a shop container with multiple validation rules.
     * Used for complex validation scenarios.
     *
     * @param container The Bukkit container block
     * @param shopId The ID of the shop
     * @param rules The validation rules to apply
     * @return ShopContainer with specified rules
     */
    public static ShopContainer createWithRules(
        Container container,
        UUID shopId,
        List<ValidationRule> rules
    ) {
        return new ShopContainer(container, shopId, new ArrayList<>(rules));
    }

    /**
     * Creates a shop container by analyzing the current inventory.
     * Automatically selects appropriate rules based on items present.
     *
     * @param container The Bukkit container block
     * @param shopId The ID of the shop
     * @return ShopContainer with auto-detected rules, or empty rules if no items present
     */
    public static ShopContainer createAutoDetected(Container container, UUID shopId) {
        // Find first non-empty item to determine shop type
        for (ItemStack item : container.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                // Check if stackable or unstackable
                if (UnstackableItemOnlyRule.isUnstackable(item)) {
                    return createUnstackableOnly(container, shopId);
                } else {
                    return createStackableTypeLocked(container, shopId, item);
                }
            }
        }

        // No items present, create with no rules
        return createContainer(container, shopId);
    }
}
