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
    private SignMode mode;  // New property

    private BarterSign(Builder builder) {
        this.id = builder.id;
        this.owner = builder.owner;
        this.group = builder.group;
        this.type = builder.type;
        this.container1Coords = builder.container1Coords;
        this.container2Coords = builder.container2Coords;
        this.containerType = builder.containerType;
        this.mode = builder.mode;
    }

    // Getters
    public String getId() { return id; }
    public UUID getOwner() { return owner; }
    public String getGroup() { return group; }
    public SignType getType() { return type; }
    public Location getContainer1Coords() { return container1Coords; }
    public Location getContainer2Coords() { return container2Coords; }
    public ContainerType getContainerType() { return containerType; }
    public SignMode getMode() { return mode; }
    public void setMode(SignMode mode) { this.mode = mode; }

    // Builder class
    public static class Builder {
        private String id;
        private UUID owner;
        private String group;
        private SignType type;
        private Location container1Coords;
        private Location container2Coords;
        private ContainerType containerType;
        private SignMode mode = SignMode.SETUP;  // Default to SETUP mode

        public Builder id(String id) { this.id = id; return this; }
        public Builder owner(UUID owner) { this.owner = owner; return this; }
        public Builder group(String group) { this.group = group; return this; }
        public Builder type(SignType type) { this.type = type; return this; }
        public Builder container1Coords(Location coords) { this.container1Coords = coords; return this; }
        public Builder container2Coords(Location coords) { this.container2Coords = coords; return this; }
        public Builder mode(SignMode mode) { this.mode = mode; return this; }

        private void validateContainers() throws IllegalArgumentException {
            this.containerType = SignUtil.validateContainers(container1Coords, container2Coords);
        }

        public BarterSign build() {
            validateContainers();
            return new BarterSign(this);
        }
    }
}
