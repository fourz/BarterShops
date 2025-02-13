package org.fourz.BarterShops.sign;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Barrel;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.container.ContainerType;

public final class SignUtil {
    public static ContainerType validateContainers(Location container1Coords, Location container2Coords) {
        if (container1Coords == null) {
            throw new IllegalArgumentException("Primary container location cannot be null");
        }

        Block container1 = container1Coords.getBlock();
        Block container2 = container2Coords != null ? container2Coords.getBlock() : null;

        if (container1.getState() instanceof Barrel) {
            if (container2 != null) {
                throw new IllegalArgumentException("Barrels cannot be part of a double container");
            }
            return ContainerType.BARREL;
        } else if (container1.getState() instanceof Chest) {
            if (container2 == null) {
                return ContainerType.SINGLE_CHEST;
            } else if (container2.getState() instanceof Chest) {
                return ContainerType.DOUBLE_CHEST;
            } else {
                throw new IllegalArgumentException("Second container must be a chest");
            }
        } else {
            throw new IllegalArgumentException("Primary container must be a barrel or chest");
        }
    }

    public static String[] splitItemName(Block block) {
        if (block == null) {
            throw new IllegalArgumentException("Block cannot be null");
        }
        return formatMaterialName(block.getType());
    }

    public static String[] splitItemName(ItemStack item) {
        if (item == null) {
            throw new IllegalArgumentException("Item cannot be null");
        }
        return formatMaterialName(item.getType());
    }

    private static String[] formatMaterialName(Material material) {
        String name = material.name().toLowerCase()
                            .replace('_', ' ');
        String[] words = name.split(" ");
        for (int i = 0; i < words.length; i++) {
            if (!words[i].isEmpty()) {
                words[i] = words[i].substring(0, 1).toUpperCase() + words[i].substring(1);
            }
        }
        name = String.join(" ", words);

        // Split into two lines
        String[] lines = new String[2];
        int mid = name.length() / 2;
        int splitPos = name.lastIndexOf(' ', mid);

        if (splitPos == -1) {
            splitPos = mid;
        }

        lines[0] = name.substring(0, splitPos).trim();
        lines[1] = name.substring(splitPos).trim();

        return lines;
    }
}
