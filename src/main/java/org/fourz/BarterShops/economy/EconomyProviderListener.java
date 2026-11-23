package org.fourz.BarterShops.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;

/**
 * Keeps {@link EconomyManager}'s cached Vault provider in step with the ServicesManager (#2444).
 *
 * <p>Before this, BarterShops resolved the provider once at enable. A {@code plugman unload
 * TokenEconomy} left a stale reference ({@code /shop fee} still listed fees) and a later load was
 * only picked up by {@code plugman reload BarterShops}.</p>
 *
 * <ul>
 *   <li>{@link ServiceRegisterEvent} / {@link ServiceUnregisterEvent} for {@link Economy}:
 *       re-resolve the highest-priority provider. Bukkit removes the registration before it
 *       fires the unregister event, so the lookup no longer sees the old provider.</li>
 *   <li>{@link PluginDisableEvent} of the plugin that owns the bound provider: fallback for a
 *       disable path that does not fire the unregister event. The owner's registration can still
 *       be listed at that point, so it is excluded from the lookup.</li>
 * </ul>
 *
 * <p>Only constructed when Vault is present - the handlers reference {@link Economy}.</p>
 */
public class EconomyProviderListener implements Listener {

    private final EconomyManager economyManager;

    public EconomyProviderListener(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServiceRegister(ServiceRegisterEvent event) {
        if (event.getProvider().getService() == Economy.class) {
            economyManager.refreshProvider(null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServiceUnregister(ServiceUnregisterEvent event) {
        if (event.getProvider().getService() == Economy.class) {
            economyManager.refreshProvider(null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (economyManager.isProvidedBy(event.getPlugin())) {
            economyManager.refreshProvider(event.getPlugin());
        }
    }
}
