package org.fourz.BarterShops.sign;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitScheduler;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.config.ConfigManager;
import org.fourz.BarterShops.shop.ShopMode;
import org.fourz.BarterShops.service.ITradeService.TradeResultDTO;
import org.fourz.BarterShops.trade.TradeConfirmationGUI;
import org.fourz.BarterShops.trade.TradeEngine;
import org.fourz.BarterShops.trade.TradeSession;
import org.fourz.rvnkcore.util.log.LogManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SignInteraction.
 * Tests owner configuration interactions and customer trade interactions.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SignInteraction Tests")
class SignInteractionTest {

    @Mock
    private BarterShops plugin;

    @Mock
    private Server server;

    @Mock
    private BukkitScheduler scheduler;

    @Mock
    private LogManager logger;

    @Mock
    private TradeEngine tradeEngine;

    @Mock
    private TradeConfirmationGUI confirmationGUI;

    @Mock
    private Player owner;

    @Mock
    private Player customer;

    @Mock
    private Sign sign;

    @Mock
    private Container container;

    @Mock
    private Inventory inventory;

    @Mock
    private PlayerInventory playerInventory;

    @Mock
    private PlayerInteractEvent interactEvent;

    @Mock
    private ConfigManager configManager;

    @Mock
    private TradeSession tradeSession;

    private SignInteraction signInteraction;
    private BarterSign barterSign;
    private UUID ownerUuid;
    private UUID customerUuid;

    @BeforeEach
    void setUp() {
        ownerUuid = UUID.randomUUID();
        customerUuid = UUID.randomUUID();

        // Setup plugin mocks
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(plugin.getTradeEngine()).thenReturn(tradeEngine);
        when(plugin.getTradeConfirmationGUI()).thenReturn(confirmationGUI);
        when(plugin.getConfigManager()).thenReturn(configManager);
        when(configManager.getInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        when(configManager.getLong(anyString(), anyLong())).thenAnswer(inv -> inv.getArgument(1));

        // Setup TypeAvailabilityManager mock
        var typeAvailabilityManager = mock(org.fourz.BarterShops.config.TypeAvailabilityManager.class);
        when(plugin.getTypeAvailabilityManager()).thenReturn(typeAvailabilityManager);
        // Default: cycle through types
        when(typeAvailabilityManager.getNextSignType(any())).thenAnswer(inv -> {
            SignType current = inv.getArgument(0);
            return switch(current) {
                case STACKABLE -> SignType.UNSTACKABLE;
                case UNSTACKABLE -> SignType.BARTER;
                case BARTER -> SignType.STACKABLE;
            };
        });

        // Setup players
        when(owner.getUniqueId()).thenReturn(ownerUuid);
        when(owner.getName()).thenReturn("ShopOwner");
        when(owner.getInventory()).thenReturn(playerInventory);

        when(customer.getUniqueId()).thenReturn(customerUuid);
        when(customer.getName()).thenReturn("Customer");
        when(customer.getInventory()).thenReturn(playerInventory);

        // Setup container
        when(container.getInventory()).thenReturn(inventory);

        // Setup barter sign
        barterSign = new BarterSign.Builder()
            .id(UUID.randomUUID().toString())
            .owner(ownerUuid)
            .container(container)
            .mode(ShopMode.SETUP)
            .type(SignType.STACKABLE)
            .signSideDisplayFront(mock(org.bukkit.block.sign.SignSide.class))
            .signSideDisplayBack(mock(org.bukkit.block.sign.SignSide.class))
            .build();

        // Initialize SignInteraction with LogManager mock
        try (MockedStatic<LogManager> logManagerStatic = mockStatic(LogManager.class)) {
            logManagerStatic.when(() -> LogManager.getInstance(any(), anyString())).thenReturn(logger);
            signInteraction = new SignInteraction(plugin);
        }
    }

    // ====== Owner Left Click Tests ======

    @Nested
    @DisplayName("Owner Left Click")
    class OwnerLeftClickTests {

        @Test
        @DisplayName("Owner left click enters setup mode")
        void handleLeftClick_owner_enterSetupMode() {
            when(owner.hasPermission("bartershops.configure")).thenReturn(true);
            barterSign.setMode(ShopMode.BOARD);

            try (MockedStatic<SignDisplay> signDisplayStatic = mockStatic(SignDisplay.class)) {
                signInteraction.handleLeftClick(owner, sign, barterSign, interactEvent);

                assertEquals(ShopMode.SETUP, barterSign.getMode());
                verify(owner).sendMessage("Entering shop configuration mode");
                verify(interactEvent).setCancelled(true);
                signDisplayStatic.verify(() -> SignDisplay.updateSign(sign, barterSign));
            }
        }

        @Test
        @DisplayName("Non-owner left click does nothing")
        void handleLeftClick_notOwner_doesNothing() {
            when(customer.hasPermission("bartershops.configure")).thenReturn(true);
            barterSign.setMode(ShopMode.BOARD);

            try (MockedStatic<SignDisplay> signDisplayStatic = mockStatic(SignDisplay.class)) {
                signInteraction.handleLeftClick(customer, sign, barterSign, interactEvent);

                assertEquals(ShopMode.BOARD, barterSign.getMode());
                verify(customer, never()).sendMessage(anyString());
                verify(interactEvent, never()).setCancelled(anyBoolean());
                signDisplayStatic.verify(() -> SignDisplay.updateSign(sign, barterSign), never());
            }
        }
    }

    // ====== Owner Right Click Tests ======

    @Nested
    @DisplayName("Owner Right Click")
    class OwnerRightClickTests {

        @Test
        @DisplayName("Owner right click from SETUP advances to TYPE")
        void handleRightClick_ownerFromSetup_advancesToType() {
            barterSign.setMode(ShopMode.SETUP);

            try (MockedStatic<SignDisplay> signDisplayStatic = mockStatic(SignDisplay.class)) {
                signInteraction.handleRightClick(owner, sign, barterSign);

                assertEquals(ShopMode.TYPE, barterSign.getMode());
                verify(owner).sendMessage("Click to toggle shop type");
                signDisplayStatic.verify(() -> SignDisplay.updateSign(sign, barterSign));
            }
        }

        @Test
        @DisplayName("Owner right click from TYPE advances to BOARD")
        void handleRightClick_ownerFromType_advancesToBoard() {
            barterSign.setMode(ShopMode.TYPE);

            try (MockedStatic<SignDisplay> signDisplayStatic = mockStatic(SignDisplay.class)) {
                signInteraction.handleRightClick(owner, sign, barterSign);

                assertEquals(ShopMode.BOARD, barterSign.getMode());
                verify(owner).sendMessage("Click to edit the shop display");
                signDisplayStatic.verify(() -> SignDisplay.updateSign(sign, barterSign));
            }
        }

        @Test
        @DisplayName("Owner right click from BOARD advances to DELETE")
        void handleRightClick_ownerFromBoard_advancesToDelete() {
            barterSign.setMode(ShopMode.BOARD);

            try (MockedStatic<SignDisplay> signDisplayStatic = mockStatic(SignDisplay.class)) {
                signInteraction.handleRightClick(owner, sign, barterSign);

                assertEquals(ShopMode.DELETE, barterSign.getMode());
                verify(owner).sendMessage("Break sign to confirm deletion");
                signDisplayStatic.verify(() -> SignDisplay.updateSign(sign, barterSign));
            }
        }

        @Test
        @DisplayName("Owner right click from DELETE wraps to SETUP")
        void handleRightClick_ownerFromDelete_wrapsToSetup() {
            barterSign.setMode(ShopMode.DELETE);

            try (MockedStatic<SignDisplay> signDisplayStatic = mockStatic(SignDisplay.class)) {
                signInteraction.handleRightClick(owner, sign, barterSign);

                assertEquals(ShopMode.SETUP, barterSign.getMode());
                verify(owner).sendMessage("Right-click sign with payment item to configure");
                signDisplayStatic.verify(() -> SignDisplay.updateSign(sign, barterSign));
            }
        }
    }

    // ====== Customer Right Click Tests ======

    @Nested
    @DisplayName("Customer Right Click")
    class CustomerRightClickTests {

        private org.bukkit.block.sign.SignSide mockFrontSide;

        @BeforeEach
        void setUpTrade() {
            barterSign.setMode(ShopMode.BOARD);
            when(tradeEngine.isInFallbackMode()).thenReturn(false);

            // Setup sign mock to return proper SignSide
            mockFrontSide = mock(org.bukkit.block.sign.SignSide.class);
            when(sign.getSide(org.bukkit.block.sign.Side.FRONT)).thenReturn(mockFrontSide);

            // Setup shop inventory with stackable offering configured
            ItemStack shopItem = new ItemStack(Material.DIAMOND, 5);
            ItemStack[] contents = new ItemStack[]{shopItem, null, null};
            when(inventory.getContents()).thenReturn(contents);

            // Configure barterSign with stackable offering and price
            barterSign.configureStackableShop(shopItem, 1);
            barterSign.configurePrice(new ItemStack(Material.EMERALD, 1), 1);

            // Setup player inventory
            ItemStack paymentItem = new ItemStack(Material.EMERALD, 1);
            when(playerInventory.getItemInMainHand()).thenReturn(paymentItem);
            when(playerInventory.getContents()).thenReturn(new ItemStack[]{paymentItem});

            // Setup trade session
            when(tradeEngine.initiateTrade(customer, barterSign)).thenReturn(Optional.of(tradeSession));
            when(tradeSession.getSessionId()).thenReturn(UUID.randomUUID().toString());

            // Setup trade execution
            TradeResultDTO successResult = TradeResultDTO.success("tx-123");
            when(tradeEngine.executeTrade(anyString())).thenReturn(CompletableFuture.completedFuture(successResult));
        }

        @Test
        @DisplayName("Customer right click initiates trade")
        void handleRightClick_customer_initiatesTrade() {
            signInteraction.handleRightClick(customer, sign, barterSign);

            verify(tradeEngine).initiateTrade(customer, barterSign);
            verify(tradeSession).setState(TradeSession.TradeState.AWAITING_BUYER_CONFIRM);
            verify(confirmationGUI).openConfirmation(eq(customer), eq(tradeSession), any(), any());
        }

        @Test
        @DisplayName("Customer blocked when shop not in BOARD mode")
        void handleRightClick_notBoardMode_blocked() {
            barterSign.setMode(ShopMode.SETUP);

            signInteraction.handleRightClick(customer, sign, barterSign);

            verify(tradeEngine, never()).initiateTrade(any(), any());
        }

        @Test
        @DisplayName("Customer blocked when trade engine in fallback mode")
        void handleRightClick_fallbackMode_blocked() {
            when(tradeEngine.isInFallbackMode()).thenReturn(true);

            signInteraction.handleRightClick(customer, sign, barterSign);

            verify(tradeEngine, never()).initiateTrade(any(), any());
        }

        @Test
        @DisplayName("Customer blocked when shop out of stock")
        void handleRightClick_outOfStock_blocked() {
            // Override inventory to be empty
            ItemStack[] emptyContents = new ItemStack[]{null, null, null};
            when(inventory.getContents()).thenReturn(emptyContents);

            signInteraction.handleRightClick(customer, sign, barterSign);

            verify(tradeEngine, never()).initiateTrade(any(), any());
        }

        @Test
        @DisplayName("Customer blocked when not holding payment item")
        void handleRightClick_noPaymentItem_blocked() {
            // Override with empty inventory
            when(playerInventory.getContents()).thenReturn(new ItemStack[]{new ItemStack(Material.AIR)});

            signInteraction.handleRightClick(customer, sign, barterSign);

            // Trade should not initiate due to insufficient payment
            verify(tradeEngine, never()).initiateTrade(any(), any());
        }

        @Test
        @DisplayName("Customer blocked when no container configured")
        void handleRightClick_noContainer_blocked() {
            barterSign = new BarterSign.Builder()
                .id(UUID.randomUUID().toString())
                .owner(ownerUuid)
                .container(null)
                .shopContainer(null)
                .mode(ShopMode.BOARD)
                .type(SignType.STACKABLE)
                .signSideDisplayFront(mockFrontSide)
                .signSideDisplayBack(mock(org.bukkit.block.sign.SignSide.class))
                .build();

            signInteraction.handleRightClick(customer, sign, barterSign);

            // Trade should not initiate due to no container
            verify(tradeEngine, never()).initiateTrade(any(), any());
        }
    }

    // ====== Permission Tests ======

    @Nested
    @DisplayName("Permission Tests")
    class PermissionTests {

        @Test
        @DisplayName("Left click without configure permission is blocked")
        void handleRightClick_noPermission_blocked() {
            when(owner.hasPermission("bartershops.configure")).thenReturn(false);

            signInteraction.handleLeftClick(owner, sign, barterSign, interactEvent);

            verify(interactEvent, never()).setCancelled(anyBoolean());
            verify(owner, never()).sendMessage(anyString());
        }
    }
}
