package org.fourz.BarterShops.sign;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Barrel;

public class SignUtil {
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
}
