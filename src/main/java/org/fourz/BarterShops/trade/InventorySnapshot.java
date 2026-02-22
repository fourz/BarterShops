package org.fourz.BarterShops.trade;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Captures and restores inventory state for transaction safety.
 * Used to implement rollback on trade failures.
 */
public class InventorySnapshot {
    private final ItemStack[] contents;

    /**
     * Creates a snapshot of the current inventory state.
     * Deep clones all items to preserve state at snapshot time.
     */
    public InventorySnapshot(Inventory inventory) {
        if (inventory == null) {
            this.contents = new ItemStack[0];
            return;
        }

        ItemStack[] original = inventory.getContents();
        this.contents = new ItemStack[original.length];

        for (int i = 0; i < original.length; i++) {
            if (original[i] != null && !original[i].getType().isAir()) {
                // Deep clone the item stack
                this.contents[i] = original[i].clone();
            } else {
                this.contents[i] = null;
            }
        }
    }

    /**
     * Restores the inventory to the state captured by this snapshot.
     * Clears current inventory and replaces with snapshot contents.
     */
    public void restore(Inventory inventory) {
        if (inventory == null) return;

        // Create deep clones of snapshot items (don't restore references)
        ItemStack[] restoreContents = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && !contents[i].getType().isAir()) {
                restoreContents[i] = contents[i].clone();
            } else {
                restoreContents[i] = null;
            }
        }

        inventory.setContents(restoreContents);
    }
}
