package org.fourz.BarterShops.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.fourz.BarterShops.BarterShops;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * State transitions for the Vault provider follow-up (#2444): unload drops the cached provider
 * with one WARN, a later register re-binds it with one INFO, and repeats log nothing.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EconomyProviderListener (#2444)")
class EconomyProviderListenerTest {

    /** Unique name so RVNKCore's LogManager cache gives this class its own logger. */
    private static final String PLUGIN_NAME = "BarterShopsEconomyProviderTest";
    private static final Logger TEST_LOGGER = Logger.getLogger(PLUGIN_NAME);
    private static final List<LogRecord> RECORDS = new ArrayList<>();

    static {
        TEST_LOGGER.setUseParentHandlers(false);
        TEST_LOGGER.addHandler(new Handler() {
            @Override public void publish(LogRecord r) { synchronized (RECORDS) { RECORDS.add(r); } }
            @Override public void flush() { }
            @Override public void close() { }
        });
    }

    @Mock private BarterShops plugin;
    @Mock private Server server;
    @Mock private PluginManager pluginManager;
    @Mock private ServicesManager servicesManager;
    @Mock private FileConfiguration config;
    @Mock private Plugin tokenEconomyPlugin;
    @Mock private Plugin otherPlugin;
    @Mock private Economy tokenEconomy;
    @Mock private Economy otherEconomy;
    @Mock private RegisteredServiceProvider<Economy> tokenRsp;
    @Mock private RegisteredServiceProvider<Economy> otherRsp;

    /** What the ServicesManager currently lists, highest priority first. */
    private final List<RegisteredServiceProvider<Economy>> registered = new ArrayList<>();

    private EconomyManager manager;
    private EconomyProviderListener listener;
    private final AtomicInteger changes = new AtomicInteger();

    @BeforeEach
    void setUp() {
        synchronized (RECORDS) { RECORDS.clear(); }

        when(plugin.getName()).thenReturn(PLUGIN_NAME);
        when(plugin.getLogger()).thenReturn(TEST_LOGGER);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getConfig()).thenReturn(config);
        when(config.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        when(config.getDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getServicesManager()).thenReturn(servicesManager);
        when(pluginManager.getPlugin("Vault")).thenReturn(mock(Plugin.class));
        when(servicesManager.getRegistrations(Economy.class)).thenAnswer(inv -> new ArrayList<>(registered));

        when(tokenEconomy.getName()).thenReturn("TokenEconomy");
        when(tokenRsp.getProvider()).thenReturn(tokenEconomy);
        when(tokenRsp.getPlugin()).thenReturn(tokenEconomyPlugin);
        doReturn(Economy.class).when(tokenRsp).getService();

        when(otherEconomy.getName()).thenReturn("OtherEconomy");
        when(otherRsp.getProvider()).thenReturn(otherEconomy);
        when(otherRsp.getPlugin()).thenReturn(otherPlugin);
        doReturn(Economy.class).when(otherRsp).getService();

        registered.add(tokenRsp);
        manager = new EconomyManager(plugin);
        manager.setProviderChangeListener(changes::incrementAndGet);
        listener = new EconomyProviderListener(manager);
        synchronized (RECORDS) { RECORDS.clear(); }
    }

    private List<String> messages(Level level) {
        synchronized (RECORDS) {
            return RECORDS.stream().filter(r -> r.getLevel() == level).map(LogRecord::getMessage).toList();
        }
    }

    private void unregister(RegisteredServiceProvider<Economy> rsp) {
        registered.remove(rsp); // Bukkit removes the registration before it fires the event
        listener.onServiceUnregister(new ServiceUnregisterEvent(rsp));
    }

    private void register(RegisteredServiceProvider<Economy> rsp) {
        registered.add(0, rsp);
        listener.onServiceRegister(new ServiceRegisterEvent(rsp));
    }

    @Test
    @DisplayName("enable binds the provider")
    void enableBindsProvider() {
        assertTrue(manager.isEconomyEnabled());
        assertEquals("TokenEconomy", manager.getEconomyProvider());
        assertTrue(manager.isProvidedBy(tokenEconomyPlugin));
    }

    @Test
    @DisplayName("unregister drops the provider with exactly one WARN and fees go to zero")
    void unregisterDropsProvider() {
        unregister(tokenRsp);
        unregister(tokenRsp); // a repeat event must not log again

        assertFalse(manager.isEconomyEnabled());
        assertEquals("None", manager.getEconomyProvider());
        assertFalse(manager.areFeesEnabled());
        assertEquals(0.0, manager.calculateListingFee("BARTER"));
        assertEquals(List.of("[Economy] Economy provider TokenEconomy unregistered - economy features disabled"),
                messages(Level.WARNING));
        assertEquals(1, changes.get());
        assertTrue(manager.getUnavailableMessage().contains("TokenEconomy"));
    }

    @Test
    @DisplayName("transactions refuse after unregister and never touch the stale provider")
    void transactionsRefuseAfterUnregister() throws Exception {
        unregister(tokenRsp);

        var result = manager.withdraw(java.util.UUID.randomUUID(), 10.0).get();
        assertFalse(result.success());
        assertEquals("Economy is not enabled", result.message());
        verify(tokenEconomy, never()).withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
    }

    @Test
    @DisplayName("register after unload re-binds with one INFO")
    void registerRebinds() {
        unregister(tokenRsp);
        synchronized (RECORDS) { RECORDS.clear(); }

        register(tokenRsp);

        assertTrue(manager.isEconomyEnabled());
        assertEquals("TokenEconomy", manager.getEconomyProvider());
        assertEquals(List.of("[Economy] Economy integration enabled using TokenEconomy"), messages(Level.INFO));
        assertTrue(messages(Level.WARNING).isEmpty());
        assertEquals(2, changes.get());
    }

    @Test
    @DisplayName("provider registered after enable is picked up (no provider at enable)")
    void lateProviderIsBound() {
        registered.clear();
        EconomyManager late = new EconomyManager(plugin);
        EconomyProviderListener lateListener = new EconomyProviderListener(late);
        assertFalse(late.isEconomyEnabled());
        assertTrue(late.getUnavailableMessage().contains("no Vault economy provider"));

        registered.add(tokenRsp);
        lateListener.onServiceRegister(new ServiceRegisterEvent(tokenRsp));

        assertTrue(late.isEconomyEnabled());
        assertEquals("TokenEconomy", late.getEconomyProvider());
    }

    @Test
    @DisplayName("unregister of the bound provider falls back to the next registered one")
    void unregisterFallsBackToNextProvider() {
        registered.add(otherRsp); // lower priority, listed second

        unregister(tokenRsp);

        assertTrue(manager.isEconomyEnabled());
        assertEquals("OtherEconomy", manager.getEconomyProvider());
        assertTrue(messages(Level.WARNING).isEmpty());
    }

    @Test
    @DisplayName("PluginDisableEvent of the owner drops the provider while it is still listed")
    void pluginDisableFallbackDrops() {
        // Registration still present: the disable fires before Bukkit unregisters services.
        listener.onPluginDisable(new PluginDisableEvent(tokenEconomyPlugin));

        assertFalse(manager.isEconomyEnabled());
        assertEquals(1, messages(Level.WARNING).size());

        // The later unregister event must not log a second WARN.
        unregister(tokenRsp);
        assertEquals(1, messages(Level.WARNING).size());
        assertEquals(1, changes.get());
    }

    @Test
    @DisplayName("PluginDisableEvent of an unrelated plugin changes nothing")
    void unrelatedPluginDisableIgnored() {
        listener.onPluginDisable(new PluginDisableEvent(otherPlugin));

        assertTrue(manager.isEconomyEnabled());
        assertEquals(0, changes.get());
        verify(servicesManager, times(1)).getRegistrations(Economy.class); // only the enable lookup
    }

    @Test
    @DisplayName("non-economy service events are ignored")
    void nonEconomyServiceIgnored() {
        @SuppressWarnings("unchecked")
        RegisteredServiceProvider<Object> unrelated = mock(RegisteredServiceProvider.class);
        doReturn(Object.class).when(unrelated).getService();

        listener.onServiceUnregister(new ServiceUnregisterEvent(unrelated));

        assertTrue(manager.isEconomyEnabled());
        verify(servicesManager, times(1)).getRegistrations(Economy.class);
    }

    @Test
    @DisplayName("without Vault the manager never resolves a provider")
    void noVaultNeverResolves() {
        when(pluginManager.getPlugin("Vault")).thenReturn(null);
        EconomyManager noVault = new EconomyManager(plugin);

        assertFalse(noVault.refreshProvider(null));
        assertFalse(noVault.isVaultPresent());
        assertEquals("Economy features are disabled (Vault not found)", noVault.getUnavailableMessage());
    }
}
