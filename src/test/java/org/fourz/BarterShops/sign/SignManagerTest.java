package org.fourz.BarterShops.sign;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.PluginManager;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.config.ConfigManager;
import org.fourz.BarterShops.shop.ShopManager;
import org.fourz.rvnkcore.util.log.LogManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SignManager.
 * Tests sign creation, validation, interaction handling, and lifecycle management.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SignManager Tests")
class SignManagerTest {

    @Mock
    private BarterShops plugin;

    @Mock
    private Server server;

    @Mock
    private PluginManager pluginManager;

    @Mock
    private LogManager logger;

    @Mock
    private ShopManager shopManager;

    @Mock
    private Player player;

    @Mock
    private Sign sign;

    @Mock
    private Block signBlock;

    @Mock
    private Container container;

    @Mock
    private SignSide signSide;

    @Mock
    private BlockData blockData;

    @Mock
    private WallSign wallSign;

    @Mock
    private ConfigManager configManager;

    @Mock
    private World world;

    private SignManager signManager;
    private UUID playerUuid;
    private Location signLocation;

    @BeforeEach
    void setUp() {
        playerUuid = UUID.randomUUID();

        // Setup plugin mocks
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(plugin.getShopManager()).thenReturn(shopManager);
        when(plugin.getConfigManager()).thenReturn(configManager);
        when(configManager.getInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        when(configManager.getLong(anyString(), anyLong())).thenAnswer(inv -> inv.getArgument(1));

        // Setup player
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.getName()).thenReturn("TestPlayer");

        // Setup location
        signLocation = new Location(world, 10, 64, 20);
        when(signBlock.getLocation()).thenReturn(signLocation);

        // Setup sign
        when(sign.getBlock()).thenReturn(signBlock);
        when(sign.getSide(Side.FRONT)).thenReturn(signSide);
        when(sign.getSide(Side.BACK)).thenReturn(signSide);
    }

    // Helper to initialize SignManager with LogManager mock
    private SignManager createSignManager() {
        try (MockedStatic<LogManager> logManagerStatic = mockStatic(LogManager.class)) {
            logManagerStatic.when(() -> LogManager.getInstance(any(), anyString())).thenReturn(logger);
            return new SignManager(plugin);
        }
    }

    // ====== Sign Creation Tests ======

    @Nested
    @DisplayName("Sign Creation")
    class SignCreationTests {

        private SignChangeEvent signChangeEvent;

        @BeforeEach
        void setUpSignCreation() {
            signChangeEvent = mock(SignChangeEvent.class);
            when(signChangeEvent.getLine(0)).thenReturn("[barter]");
            when(signChangeEvent.getPlayer()).thenReturn(player);
            when(signChangeEvent.getBlock()).thenReturn(signBlock);
            when(signBlock.getState()).thenReturn(sign);
            when(sign.getBlockData()).thenReturn(wallSign);
            when(wallSign.getFacing()).thenReturn(BlockFace.NORTH);
            when(signBlock.getRelative(BlockFace.SOUTH)).thenReturn(mock(Block.class));
            when(signBlock.getRelative(BlockFace.SOUTH).getState()).thenReturn(container);

            try (MockedStatic<LogManager> logManagerStatic = mockStatic(LogManager.class)) {
                logManagerStatic.when(() -> LogManager.getInstance(any(), anyString())).thenReturn(logger);
                signManager = new SignManager(plugin);
            }
        }

        @Test
        @DisplayName("Valid barter sign creates BarterSign")
        void onSignChange_validBarterSign_createsBarterSign() {
            when(player.hasPermission("bartershops.create")).thenReturn(true);

            signManager.onSignChange(signChangeEvent);

            verify(player).sendMessage("Shop created successfully! Punch sign to configure.");
            verify(signChangeEvent, never()).setCancelled(true);

            Map<Location, BarterSign> barterSigns = signManager.getBarterSigns();
            assertEquals(1, barterSigns.size());
            assertTrue(barterSigns.containsKey(signLocation));

            BarterSign createdSign = barterSigns.get(signLocation);
            assertNotNull(createdSign);
            assertEquals(playerUuid, createdSign.getOwner());
            assertEquals(SignMode.SETUP, createdSign.getMode());
            assertEquals(SignType.STACKABLE, createdSign.getType());
            assertEquals(signLocation, createdSign.getSignLocation());
        }

        @Test
        @DisplayName("Sign creation without permission is cancelled")
        void onSignChange_noPermission_cancelsEvent() {
            when(player.hasPermission("bartershops.create")).thenReturn(false);

            signManager.onSignChange(signChangeEvent);

            verify(player).sendMessage("You do not have permission to create a shop!");
            verify(signChangeEvent).setCancelled(true);
            assertTrue(signManager.getBarterSigns().isEmpty());
        }

        @Test
        @DisplayName("Sign creation without container is cancelled")
        void onSignChange_noContainer_cancelsEvent() {
            when(player.hasPermission("bartershops.create")).thenReturn(true);
            // Return a non-Container block state behind and below the sign
            Block nonContainerBlock = mock(Block.class);
            org.bukkit.block.BlockState plainState = mock(org.bukkit.block.BlockState.class);
            when(nonContainerBlock.getState()).thenReturn(plainState);
            when(signBlock.getRelative(BlockFace.SOUTH)).thenReturn(nonContainerBlock);
            Block belowBlock = mock(Block.class);
            org.bukkit.block.BlockState belowState = mock(org.bukkit.block.BlockState.class);
            when(belowBlock.getState()).thenReturn(belowState);
            when(signBlock.getRelative(BlockFace.DOWN)).thenReturn(belowBlock);

            signManager.onSignChange(signChangeEvent);

            verify(player).sendMessage("Invalid shop location! Place sign on or above a container.");
            verify(signChangeEvent).setCancelled(true);
            assertTrue(signManager.getBarterSigns().isEmpty());
        }
    }

    // ====== Sign Interaction Tests ======

    @Nested
    @DisplayName("Sign Interaction")
    class SignInteractionTests {

        private PlayerInteractEvent interactEvent;

        @BeforeEach
        void setUpInteraction() {
            interactEvent = mock(PlayerInteractEvent.class);
            when(interactEvent.getClickedBlock()).thenReturn(signBlock);
            when(signBlock.getState()).thenReturn(sign);
            when(interactEvent.getPlayer()).thenReturn(player);
            when(interactEvent.getHand()).thenReturn(EquipmentSlot.HAND);
        }

        @Test
        @DisplayName("Left click delegates to SignInteraction")
        void onSignClick_leftClick_delegatesToSignInteraction() {
            when(interactEvent.getAction()).thenReturn(Action.LEFT_CLICK_BLOCK);

            try (MockedStatic<LogManager> logManagerStatic = mockStatic(LogManager.class)) {
                logManagerStatic.when(() -> LogManager.getInstance(any(), anyString())).thenReturn(logger);
                signManager = new SignManager(plugin);
            }

            signManager.onSignClick(interactEvent);

            verify(interactEvent).setCancelled(true);
        }

        @Test
        @DisplayName("Right click delegates to SignInteraction")
        void onSignClick_rightClick_delegatesToSignInteraction() {
            when(interactEvent.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);

            try (MockedStatic<LogManager> logManagerStatic = mockStatic(LogManager.class)) {
                logManagerStatic.when(() -> LogManager.getInstance(any(), anyString())).thenReturn(logger);
                signManager = new SignManager(plugin);
            }

            signManager.onSignClick(interactEvent);

            verify(interactEvent).setCancelled(true);
        }

        @Test
        @DisplayName("Click on non-sign block does nothing")
        void onSignClick_notASign_doesNothing() {
            Block nonSignBlock = mock(Block.class);
            org.bukkit.block.BlockState plainState = mock(org.bukkit.block.BlockState.class);
            when(nonSignBlock.getState()).thenReturn(plainState);
            when(interactEvent.getClickedBlock()).thenReturn(nonSignBlock);

            try (MockedStatic<LogManager> logManagerStatic = mockStatic(LogManager.class)) {
                logManagerStatic.when(() -> LogManager.getInstance(any(), anyString())).thenReturn(logger);
                signManager = new SignManager(plugin);
            }

            signManager.onSignClick(interactEvent);

            verify(interactEvent, never()).setCancelled(anyBoolean());
        }
    }

    // ====== BarterSigns Map Tests ======

    @Nested
    @DisplayName("BarterSigns Map")
    class BarterSignsMapTests {

        @Test
        @DisplayName("BarterSigns map uses Location keys correctly")
        void barterSignsMap_putAndGet_worksCorrectly() {
            try (MockedStatic<LogManager> logManagerStatic = mockStatic(LogManager.class)) {
                logManagerStatic.when(() -> LogManager.getInstance(any(), anyString())).thenReturn(logger);
                signManager = new SignManager(plugin);
            }

            // Create sign change event
            SignChangeEvent signChangeEvent = mock(SignChangeEvent.class);
            when(signChangeEvent.getLine(0)).thenReturn("[barter]");
            when(signChangeEvent.getPlayer()).thenReturn(player);
            when(signChangeEvent.getBlock()).thenReturn(signBlock);
            when(signBlock.getState()).thenReturn(sign);
            when(sign.getBlockData()).thenReturn(wallSign);
            when(wallSign.getFacing()).thenReturn(BlockFace.NORTH);
            when(signBlock.getRelative(BlockFace.SOUTH)).thenReturn(mock(Block.class));
            when(signBlock.getRelative(BlockFace.SOUTH).getState()).thenReturn(container);
            when(player.hasPermission("bartershops.create")).thenReturn(true);

            signManager.onSignChange(signChangeEvent);

            Map<Location, BarterSign> barterSigns = signManager.getBarterSigns();
            assertFalse(barterSigns.isEmpty());
            assertTrue(barterSigns.containsKey(signLocation));

            BarterSign retrievedSign = barterSigns.get(signLocation);
            assertNotNull(retrievedSign);
            assertEquals(playerUuid, retrievedSign.getOwner());
        }
    }

    // ====== Cleanup Tests ======

    @Nested
    @DisplayName("Cleanup")
    class CleanupTests {

        @Test
        @DisplayName("Cleanup clears all registered signs")
        void cleanup_clearsAllSigns() {
            try (MockedStatic<LogManager> logManagerStatic = mockStatic(LogManager.class)) {
                logManagerStatic.when(() -> LogManager.getInstance(any(), anyString())).thenReturn(logger);
                signManager = new SignManager(plugin);
            }

            // Create a sign
            SignChangeEvent signChangeEvent = mock(SignChangeEvent.class);
            when(signChangeEvent.getLine(0)).thenReturn("[barter]");
            when(signChangeEvent.getPlayer()).thenReturn(player);
            when(signChangeEvent.getBlock()).thenReturn(signBlock);
            when(signBlock.getState()).thenReturn(sign);
            when(sign.getBlockData()).thenReturn(wallSign);
            when(wallSign.getFacing()).thenReturn(BlockFace.NORTH);
            when(signBlock.getRelative(BlockFace.SOUTH)).thenReturn(mock(Block.class));
            when(signBlock.getRelative(BlockFace.SOUTH).getState()).thenReturn(container);
            when(player.hasPermission("bartershops.create")).thenReturn(true);

            signManager.onSignChange(signChangeEvent);
            assertFalse(signManager.getBarterSigns().isEmpty());

            signManager.cleanup();

            assertTrue(signManager.getBarterSigns().isEmpty());
        }
    }

    // ====== Sign Protection Tests ======

    @Nested
    @DisplayName("Sign Protection")
    class SignProtectionTests {

        @BeforeEach
        void setUpProtection() {
            try (MockedStatic<LogManager> logManagerStatic = mockStatic(LogManager.class)) {
                logManagerStatic.when(() -> LogManager.getInstance(any(), anyString())).thenReturn(logger);
                signManager = new SignManager(plugin);
            }

            // Create a sign via event
            SignChangeEvent signChangeEvent = mock(SignChangeEvent.class);
            when(signChangeEvent.getLine(0)).thenReturn("[barter]");
            when(signChangeEvent.getPlayer()).thenReturn(player);
            when(signChangeEvent.getBlock()).thenReturn(signBlock);
            when(signBlock.getState()).thenReturn(sign);
            when(sign.getBlockData()).thenReturn(wallSign);
            when(wallSign.getFacing()).thenReturn(BlockFace.NORTH);
            when(signBlock.getRelative(BlockFace.SOUTH)).thenReturn(mock(Block.class));
            when(signBlock.getRelative(BlockFace.SOUTH).getState()).thenReturn(container);
            when(player.hasPermission("bartershops.create")).thenReturn(true);

            signManager.onSignChange(signChangeEvent);
        }

        @Test
        @DisplayName("Non-owner cannot break shop sign")
        void onSignBreak_nonOwner_cancelled() {
            Player stranger = mock(Player.class);
            when(stranger.getUniqueId()).thenReturn(UUID.randomUUID());
            when(stranger.hasPermission("bartershops.admin")).thenReturn(false);

            org.bukkit.event.block.BlockBreakEvent breakEvent = mock(org.bukkit.event.block.BlockBreakEvent.class);
            when(breakEvent.getBlock()).thenReturn(signBlock);
            when(breakEvent.getPlayer()).thenReturn(stranger);

            signManager.onSignBreak(breakEvent);

            verify(breakEvent).setCancelled(true);
            assertFalse(signManager.getBarterSigns().isEmpty());
        }

        @Test
        @DisplayName("Owner cannot break sign without DELETE mode")
        void onSignBreak_ownerNotDeleteMode_cancelled() {
            when(player.hasPermission("bartershops.admin")).thenReturn(false);

            org.bukkit.event.block.BlockBreakEvent breakEvent = mock(org.bukkit.event.block.BlockBreakEvent.class);
            when(breakEvent.getBlock()).thenReturn(signBlock);
            when(breakEvent.getPlayer()).thenReturn(player);

            // Sign is in SETUP mode (default after creation)
            signManager.onSignBreak(breakEvent);

            verify(breakEvent).setCancelled(true);
            assertFalse(signManager.getBarterSigns().isEmpty());
        }

        @Test
        @DisplayName("Owner can break sign in DELETE mode")
        void onSignBreak_ownerDeleteMode_removed() {
            when(player.hasPermission("bartershops.admin")).thenReturn(false);

            // Set the sign to DELETE mode
            BarterSign barterSign = signManager.getBarterSigns().get(signLocation);
            barterSign.setMode(SignMode.DELETE);

            org.bukkit.event.block.BlockBreakEvent breakEvent = mock(org.bukkit.event.block.BlockBreakEvent.class);
            when(breakEvent.getBlock()).thenReturn(signBlock);
            when(breakEvent.getPlayer()).thenReturn(player);

            signManager.onSignBreak(breakEvent);

            verify(breakEvent, never()).setCancelled(true);
            assertTrue(signManager.getBarterSigns().isEmpty());
        }

        @Test
        @DisplayName("Admin can break sign regardless of mode")
        void onSignBreak_admin_removed() {
            Player admin = mock(Player.class);
            when(admin.getUniqueId()).thenReturn(UUID.randomUUID());
            when(admin.getName()).thenReturn("Admin");
            when(admin.hasPermission("bartershops.admin")).thenReturn(true);

            org.bukkit.event.block.BlockBreakEvent breakEvent = mock(org.bukkit.event.block.BlockBreakEvent.class);
            when(breakEvent.getBlock()).thenReturn(signBlock);
            when(breakEvent.getPlayer()).thenReturn(admin);

            signManager.onSignBreak(breakEvent);

            verify(breakEvent, never()).setCancelled(true);
            assertTrue(signManager.getBarterSigns().isEmpty());
        }

        @Test
        @DisplayName("Break event on non-shop block passes through")
        void onSignBreak_nonShopBlock_passesThrough() {
            Block otherBlock = mock(Block.class);
            Location otherLoc = new Location(world, 99, 99, 99);
            when(otherBlock.getLocation()).thenReturn(otherLoc);

            org.bukkit.event.block.BlockBreakEvent breakEvent = mock(org.bukkit.event.block.BlockBreakEvent.class);
            when(breakEvent.getBlock()).thenReturn(otherBlock);
            when(breakEvent.getPlayer()).thenReturn(player);

            signManager.onSignBreak(breakEvent);

            verify(breakEvent, never()).setCancelled(anyBoolean());
        }
    }
}
