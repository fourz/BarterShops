package org.fourz.BarterShops.sign;

import org.bukkit.block.Container;
import org.bukkit.block.sign.SignSide;
import org.fourz.BarterShops.container.ContainerType;

import java.util.UUID;

public class BarterSign {
    private final String id;
    private final UUID owner;
    private final String group;
    private final SignType type;
    private final ContainerType containerType;
    private final Container container;
    private final Container paymentContainer;
    private SignMode mode;
    private SignSide signSideDisplayFront;
    private SignSide signSideDisplayBack;

    private BarterSign(Builder builder) {
        this.id = builder.id;
        this.owner = builder.owner;
        this.group = builder.group;
        this.type = builder.type;
        this.containerType = builder.containerType;
        this.container = builder.container;
        this.paymentContainer = builder.paymentContainer;
        this.mode = builder.mode;
        this.signSideDisplayFront = builder.signSideDisplayFront;
        this.signSideDisplayBack = builder.signSideDisplayBack;
    }

    // Getters
    public String getId() { return id; }
    public UUID getOwner() { return owner; }
    public String getGroup() { return group; }
    public SignType getType() { return type; }
    public ContainerType getContainerType() { return containerType; }
    public Container getContainer() { return container; }
    public Container getPaymentContainer() { return paymentContainer; }
    public SignMode getMode() { return mode; }
    public void setMode(SignMode mode) { this.mode = mode; }
    public SignSide getSignSideDisplayFront() { return signSideDisplayFront; }
    public SignSide getSignSideDisplayBack() { return signSideDisplayBack; }
    public void setSignSideDisplayFront(SignSide side) { this.signSideDisplayFront = side; }
    public void setSignSideDisplayBack(SignSide side) { this.signSideDisplayBack = side; }

    public static class Builder {
        private String id;
        private UUID owner;
        private String group;
        private SignType type;
        private ContainerType containerType;
        private Container container;
        private Container paymentContainer;
        private SignMode mode = SignMode.SETUP;
        private SignSide signSideDisplayFront;
        private SignSide signSideDisplayBack;

        public Builder id(String id) { this.id = id; return this; }
        public Builder owner(UUID owner) { this.owner = owner; return this; }
        public Builder group(String group) { this.group = group; return this; }
        public Builder type(SignType type) { this.type = type; return this; }
        public Builder container(Container container) {
            this.container = container;
            return this;
        }

        public Builder paymentContainer(Container container) {
            this.paymentContainer = container;
            return this;
        }

        public Builder mode(SignMode mode) { this.mode = mode; return this; }
        public Builder signSideDisplayFront(SignSide side) {
            this.signSideDisplayFront = side;
            return this;
        }

        public Builder signSideDisplayBack(SignSide side) {
            this.signSideDisplayBack = side;
            return this;
        }

        public BarterSign build() {
            return new BarterSign(this);
        }
    }
}
