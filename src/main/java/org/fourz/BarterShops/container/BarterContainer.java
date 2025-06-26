package org.fourz.BarterShops.container;

import org.bukkit.block.Container;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.util.Debug;

public class BarterContainer {
    private static final String CLASS_NAME = "BarterContainer";
    private final Container container;
    private final NamespacedKey barterIdKey;
    private final NamespacedKey paymentIdKey;
    private final Debug debug;
    
    public BarterContainer(Container container, BarterShops plugin) {
        this.container = container;
        this.barterIdKey = new NamespacedKey(plugin, "barter_container_id");
        this.paymentIdKey = new NamespacedKey(plugin, "payment_container_id");
        this.debug = new Debug(plugin, CLASS_NAME, plugin.getDebugger().getLogLevel()) {};
        debug.debug("Created new BarterContainer at " + container.getLocation());
    }

    public String getBarterContainerId() {
        PersistentDataContainer pdc = container.getPersistentDataContainer();
        String id = pdc.get(barterIdKey, PersistentDataType.STRING);
        debug.debug("Retrieved barter container ID: " + id);
        return id;
    }

    public void setBarterContainerId(String id) {
        debug.debug("Setting barter container ID to: " + id);
        PersistentDataContainer pdc = container.getPersistentDataContainer();
        pdc.set(barterIdKey, PersistentDataType.STRING, id);
        container.update();
    }

    public String getPaymentContainerId() {
        PersistentDataContainer pdc = container.getPersistentDataContainer();
        String id = pdc.get(paymentIdKey, PersistentDataType.STRING);
        debug.debug("Retrieved payment container ID: " + id);
        return id;
    }

    public void setPaymentContainerId(String id) {
        debug.debug("Setting payment container ID to: " + id);
        PersistentDataContainer pdc = container.getPersistentDataContainer();
        pdc.set(paymentIdKey, PersistentDataType.STRING, id);
        container.update();
    }

    public boolean isBarterContainer() {
        boolean result = container.getPersistentDataContainer().has(barterIdKey, PersistentDataType.STRING);
        debug.debug("Checking if container is barter container: " + result);
        return result;
    }

    public Block getBlock() {
        return container.getBlock();
    }

    public Container getContainer() {
        return container;
    }
}
