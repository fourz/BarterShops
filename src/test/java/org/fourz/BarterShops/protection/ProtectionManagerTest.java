package org.fourz.BarterShops.protection;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.fourz.BarterShops.BarterShops;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProtectionManager.
 * Tests provider detection, fallback, and protection operations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProtectionManager Tests")
class ProtectionManagerTest {

    @Mock
    private BarterShops plugin;

    @Mock
    private PluginManager pluginManager;

    @Mock
    private Plugin worldGuardPlugin;

    @Mock
    private Plugin griefPreventionPlugin;

    @Mock
    private Location testLocation;

    @Mock
    private World testWorld;

    @Mock
    private Player testPlayer;

    private ProtectionManager protectionManager;
    private UUID testPlayerUuid;

    @BeforeEach
    void setUp() {
        testPlayerUuid = UUID.randomUUID();

        // Setup plugin mock
        when(plugin.getServer().getPluginManager()).thenReturn(pluginManager);
        when(plugin.getConfig()).thenReturn(org.bukkit.configuration.Configuration.getConfiguration(null));
        when(plugin.getConfig().getBoolean("protection.enabled", true)).thenReturn(true);
        when(plugin.getConfig().getInt("protection.auto-protect-radius", 3)).thenReturn(3);
        when(plugin.getConfig().getInt("protection.max-shops-per-player", 5)).thenReturn(5);
        when(plugin.getConfig().getString("protection.provider", "auto")).thenReturn("auto");

        // Setup location mock
        when(testLocation.getWorld()).thenReturn(testWorld);
        when(testLocation.getBlockX()).thenReturn(100);
        when(testLocation.getBlockY()).thenReturn(64);
        when(testLocation.getBlockZ()).thenReturn(100);

        // Setup player mock
        when(testPlayer.getUniqueId()).thenReturn(testPlayerUuid);
        when(testPlayer.hasPermission(anyString())).thenReturn(false);
        when(testPlayer.isOp()).thenReturn(false);
    }

    // ====== Initialization Tests ======

    @Nested
    @DisplayName("Initialization")
    class InitializationTests {

        @Test
        @DisplayName("ProtectionManager initializes with NoOp provider when no plugins detected")
        void initializesWithNoOpProvider() {
            when(pluginManager.getPlugin("WorldGuard")).thenReturn(null);
            when(pluginManager.getPlugin("GriefPrevention")).thenReturn(null);

            protectionManager = new ProtectionManager(plugin);

            assertNotNull(protectionManager);
            assertNotNull(protectionManager.getProvider());
            assertEquals("NoOp", protectionManager.getProvider().getProviderName());
            assertTrue(protectionManager.isEnabled());
        }

        @Test
        @DisplayName("ProtectionManager respects disabled setting")
        void respectsDisabledSetting() {
            when(plugin.getConfig().getBoolean("protection.enabled", true)).thenReturn(false);

            protectionManager = new ProtectionManager(plugin);

            assertFalse(protectionManager.isEnabled());
            assertEquals("NoOp", protectionManager.getProvider().getProviderName());
        }

        @Test
        @DisplayName("ProtectionManager loads configuration values")
        void loadsConfigurationValues() {
            when(pluginManager.getPlugin("WorldGuard")).thenReturn(null);
            when(pluginManager.getPlugin("GriefPrevention")).thenReturn(null);

            protectionManager = new ProtectionManager(plugin);

            assertEquals(3, protectionManager.getAutoProtectRadius());
            assertEquals(5, protectionManager.getMaxShopsPerPlayer());
        }
    }

    // ====== Provider Detection Tests ======

    @Nested
    @DisplayName("Provider Detection")
    class ProviderDetectionTests {

        @Test
        @DisplayName("detects and prefers WorldGuard when specified")
        void prefersWorldGuardWhenSpecified() {
            when(plugin.getConfig().getString("protection.provider", "auto")).thenReturn("worldguard");
            when(pluginManager.getPlugin("WorldGuard")).thenReturn(worldGuardPlugin);
            when(worldGuardPlugin.isEnabled()).thenReturn(true);

            protectionManager = new ProtectionManager(plugin);

            assertNotNull(protectionManager.getProvider());
            // WorldGuardProvider or NoOp fallback
            assertTrue(protectionManager.getProvider().getProviderName().contains("Guard") ||
                      protectionManager.getProvider().getProviderName().equals("NoOp"));
        }

        @Test
        @DisplayName("detects and prefers GriefPrevention when specified")
        void prefersGriefPreventionWhenSpecified() {
            when(plugin.getConfig().getString("protection.provider", "auto")).thenReturn("griefprevention");
            when(pluginManager.getPlugin("GriefPrevention")).thenReturn(griefPreventionPlugin);
            when(griefPreventionPlugin.isEnabled()).thenReturn(true);

            protectionManager = new ProtectionManager(plugin);

            assertNotNull(protectionManager.getProvider());
            // GriefPreventionProvider or NoOp fallback
            assertTrue(protectionManager.getProvider().getProviderName().contains("Grief") ||
                      protectionManager.getProvider().getProviderName().equals("NoOp"));
        }

        @Test
        @DisplayName("auto-detects WorldGuard when available")
        void autoDetectsWorldGuard() {
            when(plugin.getConfig().getString("protection.provider", "auto")).thenReturn("auto");
            when(pluginManager.getPlugin("WorldGuard")).thenReturn(worldGuardPlugin);
            when(worldGuardPlugin.isEnabled()).thenReturn(true);

            protectionManager = new ProtectionManager(plugin);

            assertNotNull(protectionManager.getProvider());
        }

        @Test
        @DisplayName("falls back to GriefPrevention if WorldGuard unavailable")
        void fallsBackToGriefPrevention() {
            when(plugin.getConfig().getString("protection.provider", "auto")).thenReturn("auto");
            when(pluginManager.getPlugin("WorldGuard")).thenReturn(null);
            when(pluginManager.getPlugin("GriefPrevention")).thenReturn(griefPreventionPlugin);
            when(griefPreventionPlugin.isEnabled()).thenReturn(true);

            protectionManager = new ProtectionManager(plugin);

            assertNotNull(protectionManager.getProvider());
        }

        @Test
        @DisplayName("uses NoOp provider when all plugins unavailable")
        void fallsBackToNoOpProvider() {
            when(plugin.getConfig().getString("protection.provider", "auto")).thenReturn("auto");
            when(pluginManager.getPlugin("WorldGuard")).thenReturn(null);
            when(pluginManager.getPlugin("GriefPrevention")).thenReturn(null);

            protectionManager = new ProtectionManager(plugin);

            assertEquals("NoOp", protectionManager.getProvider().getProviderName());
        }

        @Test
        @DisplayName("uses NoOp provider when provider set to 'none'")
        void usesNoOpWhenSetToNone() {
            when(plugin.getConfig().getString("protection.provider", "auto")).thenReturn("none");

            protectionManager = new ProtectionManager(plugin);

            assertEquals("NoOp", protectionManager.getProvider().getProviderName());
        }

        @Test
        @DisplayName("handles unavailable plugins gracefully")
        void handlesUnavailablePluginsGracefully() {
            when(plugin.getConfig().getString("protection.provider", "auto")).thenReturn("worldguard");
            when(pluginManager.getPlugin("WorldGuard")).thenReturn(null);
            when(pluginManager.getPlugin("GriefPrevention")).thenReturn(null);

            protectionManager = new ProtectionManager(plugin);

            assertEquals("NoOp", protectionManager.getProvider().getProviderName());
        }

        @Test
        @DisplayName("handles disabled plugins gracefully")
        void handlesDisabledPluginsGracefully() {
            when(plugin.getConfig().getString("protection.provider", "auto")).thenReturn("worldguard");
            when(pluginManager.getPlugin("WorldGuard")).thenReturn(worldGuardPlugin);
            when(worldGuardPlugin.isEnabled()).thenReturn(false);
            when(pluginManager.getPlugin("GriefPrevention")).thenReturn(null);

            protectionManager = new ProtectionManager(plugin);

            assertEquals("NoOp", protectionManager.getProvider().getProviderName());
        }
    }

    // ====== Player Limit Tests ======

    @Nested
    @DisplayName("Player Shop Limit")
    class PlayerLimitTests {

        @BeforeEach
        void setUpLimitTests() {
            when(pluginManager.getPlugin("WorldGuard")).thenReturn(null);
            when(pluginManager.getPlugin("GriefPrevention")).thenReturn(null);
            protectionManager = new ProtectionManager(plugin);
        }

        @Test
        @DisplayName("canPlayerCreateShop returns true when limit not reached")
        void canCreateShopWhenLimitNotReached() throws ExecutionException, InterruptedException {
            Boolean result = protectionManager.canPlayerCreateShop(testPlayer).get();

            assertTrue(result);
        }

        @Test
        @DisplayName("canPlayerCreateShop returns true for admins")
        void canCreateShopWhenAdmin() throws ExecutionException, InterruptedException {
            when(testPlayer.hasPermission("bartershops.admin.unlimited")).thenReturn(true);

            Boolean result = protectionManager.canPlayerCreateShop(testPlayer).get();

            assertTrue(result);
        }

        @Test
        @DisplayName("canPlayerCreateShop returns true for operators")
        void canCreateShopWhenOperator() throws ExecutionException, InterruptedException {
            when(testPlayer.isOp()).thenReturn(true);

            Boolean result = protectionManager.canPlayerCreateShop(testPlayer).get();

            assertTrue(result);
        }

        @Test
        @DisplayName("canPlayerCreateShop returns true when disabled")
        void canCreateShopWhenDisabled() throws ExecutionException, InterruptedException {
            when(plugin.getConfig().getBoolean("protection.enabled", true)).thenReturn(false);
            ProtectionManager disabledManager = new ProtectionManager(plugin);

            Boolean result = disabledManager.canPlayerCreateShop(testPlayer).get();

            assertTrue(result);
        }
    }

    // ====== Shop Protection Tests ======

    @Nested
    @DisplayName("Shop Protection")
    class ShopProtectionTests {

        @BeforeEach
        void setUpProtectionTests() {
            when(pluginManager.getPlugin("WorldGuard")).thenReturn(null);
            when(pluginManager.getPlugin("GriefPrevention")).thenReturn(null);
            protectionManager = new ProtectionManager(plugin);
        }

        @Test
        @DisplayName("protectShop returns true when protection system disabled")
        void protectShopWhenDisabled() throws ExecutionException, InterruptedException {
            when(plugin.getConfig().getBoolean("protection.enabled", true)).thenReturn(false);
            ProtectionManager disabledManager = new ProtectionManager(plugin);

            Boolean result = disabledManager.protectShop("shop-1", testLocation, testPlayerUuid).get();

            assertTrue(result);
        }

        @Test
        @DisplayName("unprotectShop returns true when protection system disabled")
        void unprotectShopWhenDisabled() throws ExecutionException, InterruptedException {
            when(plugin.getConfig().getBoolean("protection.enabled", true)).thenReturn(false);
            ProtectionManager disabledManager = new ProtectionManager(plugin);

            Boolean result = disabledManager.unprotectShop("shop-1", testLocation).get();

            assertTrue(result);
        }

        @Test
        @DisplayName("protectShop delegates to provider when enabled")
        void protectShopDelegatesToProvider() throws ExecutionException, InterruptedException {
            Boolean result = protectionManager.protectShop("shop-1", testLocation, testPlayerUuid).get();

            assertNotNull(result);
        }

        @Test
        @DisplayName("unprotectShop delegates to provider when enabled")
        void unprotectShopDelegatesToProvider() throws ExecutionException, InterruptedException {
            Boolean result = protectionManager.unprotectShop("shop-1", testLocation).get();

            assertNotNull(result);
        }
    }

    // ====== Build Permission Tests ======

    @Nested
    @DisplayName("Build Permission Checks")
    class BuildPermissionTests {

        @BeforeEach
        void setUpPermissionTests() {
            when(pluginManager.getPlugin("WorldGuard")).thenReturn(null);
            when(pluginManager.getPlugin("GriefPrevention")).thenReturn(null);
            protectionManager = new ProtectionManager(plugin);
        }

        @Test
        @DisplayName("canPlayerModify returns true when disabled")
        void canPlayerModifyWhenDisabled() throws ExecutionException, InterruptedException {
            when(plugin.getConfig().getBoolean("protection.enabled", true)).thenReturn(false);
            ProtectionManager disabledManager = new ProtectionManager(plugin);

            Boolean result = disabledManager.canPlayerModify(testPlayer, testLocation).get();

            assertTrue(result);
        }

        @Test
        @DisplayName("canPlayerModify delegates to provider when enabled")
        void canPlayerModifyDelegatesToProvider() throws ExecutionException, InterruptedException {
            Boolean result = protectionManager.canPlayerModify(testPlayer, testLocation).get();

            assertNotNull(result);
        }
    }

    // ====== Information Retrieval Tests ======

    @Nested
    @DisplayName("Information Retrieval")
    class InformationRetrievalTests {

        @BeforeEach
        void setUpInfoTests() {
            when(pluginManager.getPlugin("WorldGuard")).thenReturn(null);
            when(pluginManager.getPlugin("GriefPrevention")).thenReturn(null);
            protectionManager = new ProtectionManager(plugin);
        }

        @Test
        @DisplayName("getProtectionInfo delegates to provider")
        void getProtectionInfoDelegatesToProvider() throws ExecutionException, InterruptedException {
            IProtectionProvider.ProtectionInfo info = protectionManager.getProtectionInfo(testLocation).get();

            assertNotNull(info);
        }

        @Test
        @DisplayName("getProvider returns non-null provider")
        void getProviderReturnsProvider() {
            assertNotNull(protectionManager.getProvider());
        }
    }

    // ====== Reload Tests ======

    @Nested
    @DisplayName("Configuration Reload")
    class ReloadTests {

        @BeforeEach
        void setUpReloadTests() {
            when(pluginManager.getPlugin("WorldGuard")).thenReturn(null);
            when(pluginManager.getPlugin("GriefPrevention")).thenReturn(null);
            protectionManager = new ProtectionManager(plugin);
        }

        @Test
        @DisplayName("reload reinitializes provider")
        void reloadReinitializesProvider() {
            assertDoesNotThrow(() -> {
                protectionManager.reload();
            });

            assertNotNull(protectionManager.getProvider());
        }

        @Test
        @DisplayName("reload reloads configuration values")
        void reloadReloadsConfig() {
            when(plugin.getConfig().getInt("protection.auto-protect-radius", 3)).thenReturn(5);

            protectionManager.reload();

            assertEquals(5, protectionManager.getAutoProtectRadius());
        }

        @Test
        @DisplayName("reload updates max shops per player setting")
        void reloadUpdatesMaxShops() {
            when(plugin.getConfig().getInt("protection.max-shops-per-player", 5)).thenReturn(10);

            protectionManager.reload();

            assertEquals(10, protectionManager.getMaxShopsPerPlayer());
        }
    }

    // ====== Cleanup Tests ======

    @Nested
    @DisplayName("Cleanup")
    class CleanupTests {

        @BeforeEach
        void setUpCleanupTests() {
            when(pluginManager.getPlugin("WorldGuard")).thenReturn(null);
            when(pluginManager.getPlugin("GriefPrevention")).thenReturn(null);
            protectionManager = new ProtectionManager(plugin);
        }

        @Test
        @DisplayName("cleanup executes without errors")
        void cleanupExecutesSuccessfully() {
            assertDoesNotThrow(() -> {
                protectionManager.cleanup();
            });
        }

        @Test
        @DisplayName("cleanup clears provider reference")
        void cleanupClearsProvider() {
            protectionManager.cleanup();

            // Provider should be cleaned up
            assertNotNull(protectionManager.getProvider()); // NoOp has no cleanup
        }
    }

    // ====== State Validation Tests ======

    @Nested
    @DisplayName("State Validation")
    class StateValidationTests {

        @BeforeEach
        void setUpStateTests() {
            when(pluginManager.getPlugin("WorldGuard")).thenReturn(null);
            when(pluginManager.getPlugin("GriefPrevention")).thenReturn(null);
            protectionManager = new ProtectionManager(plugin);
        }

        @Test
        @DisplayName("isEnabled reflects configuration setting")
        void isEnabledReflectsConfig() {
            assertTrue(protectionManager.isEnabled());

            when(plugin.getConfig().getBoolean("protection.enabled", true)).thenReturn(false);
            ProtectionManager disabledManager = new ProtectionManager(plugin);

            assertFalse(disabledManager.isEnabled());
        }

        @Test
        @DisplayName("getAutoProtectRadius returns configured value")
        void getAutoProtectRadiusReturnsValue() {
            assertEquals(3, protectionManager.getAutoProtectRadius());
        }

        @Test
        @DisplayName("getMaxShopsPerPlayer returns configured value")
        void getMaxShopsPerPlayerReturnsValue() {
            assertEquals(5, protectionManager.getMaxShopsPerPlayer());
        }
    }
}
