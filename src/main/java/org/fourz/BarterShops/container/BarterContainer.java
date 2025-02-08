package org.fourz.BarterShops.container;

import org.bukkit.block.Container;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.fourz.BarterShops.Main;

public class BarterContainer {
    private final Container container;
    private final NamespacedKey barterIdKey;
    private final NamespacedKey paymentIdKey;
    
    public BarterContainer(Container container, Main plugin) {
        this.container = container;
        this.barterIdKey = new NamespacedKey(plugin, "barter_container_id");
        this.paymentIdKey = new NamespacedKey(plugin, "payment_container_id");
    }

    public String getBarterContainerId() {
        PersistentDataContainer pdc = container.getPersistentDataContainer();
        return pdc.get(barterIdKey, PersistentDataType.STRING);
    }

    public void setBarterContainerId(String id) {
        PersistentDataContainer pdc = container.getPersistentDataContainer();
        pdc.set(barterIdKey, PersistentDataType.STRING, id);
        container.update();
    }

    public String getPaymentContainerId() {
        PersistentDataContainer pdc = container.getPersistentDataContainer();
        return pdc.get(paymentIdKey, PersistentDataType.STRING);
    }

    public void setPaymentContainerId(String id) {
        PersistentDataContainer pdc = container.getPersistentDataContainer();
        pdc.set(paymentIdKey, PersistentDataType.STRING, id);
        container.update();
    }

    public boolean isBarterContainer() {
        return container.getPersistentDataContainer().has(barterIdKey, PersistentDataType.STRING);
    }

    public Block getBlock() {
        return container.getBlock();
    }

    public Container getContainer() {
        return container;
    }
}
