package org.fourz.BarterShops.sign;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Barrel;
import java.util.UUID;

public class BarterSign {
    private final String id;
    private final UUID owner;
    private final String group;
    private final SignType type;
    private final Location container1Coords;
    private final Location container2Coords;
    private final ContainerType containerType;

    private BarterSign(Builder builder) {
        this.id = builder.id;
        this.owner = builder.owner;
        this.group = builder.group;
        this.type = builder.type;
        this.container1Coords = builder.container1Coords;
        this.container2Coords = builder.container2Coords;
        this.containerType = builder.containerType;
    }

    // Getters
    public String getId() { return id; }
    public UUID getOwner() { return owner; }
    public String getGroup() { return group; }
    public SignType getType() { return type; }
    public Location getContainer1Coords() { return container1Coords; }
    public Location getContainer2Coords() { return container2Coords; }
    public ContainerType getContainerType() { return containerType; }

    // Builder class
    public static class Builder {
        private String id;
        private UUID owner;
        private String group;
        private SignType type;
        private Location container1Coords;
        private Location container2Coords;
        private ContainerType containerType;

        public Builder id(String id) { this.id = id; return this; }
        public Builder owner(UUID owner) { this.owner = owner; return this; }
        public Builder group(String group) { this.group = group; return this; }
        public Builder type(SignType type) { this.type = type; return this; }
        public Builder container1Coords(Location coords) { this.container1Coords = coords; return this; }
        public Builder container2Coords(Location coords) { this.container2Coords = coords; return this; }

        private void validateContainers() throws IllegalArgumentException {
            if (container1Coords == null) {
                throw new IllegalArgumentException("Primary container location cannot be null");
            }

            Block container1 = container1Coords.getBlock();
            Block container2 = container2Coords != null ? container2Coords.getBlock() : null;

            if (container1.getState() instanceof Barrel) {
                if (container2 != null) {
                    throw new IllegalArgumentException("Barrels cannot be part of a double container");
                }
                containerType = ContainerType.BARREL;
            } else if (container1.getState() instanceof Chest) {
                if (container2 == null) {
                    containerType = ContainerType.SINGLE_CHEST;
                } else if (container2.getState() instanceof Chest) {
                    containerType = ContainerType.DOUBLE_CHEST;
                } else {
                    throw new IllegalArgumentException("Second container must be a chest");
                }
            } else {
                throw new IllegalArgumentException("Primary container must be a barrel or chest");
            }
        }

        public BarterSign build() {
            validateContainers();
            return new BarterSign(this);
        }
    }
}
