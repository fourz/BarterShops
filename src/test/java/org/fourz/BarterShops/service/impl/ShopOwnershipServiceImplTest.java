package org.fourz.BarterShops.service.impl;

import org.bukkit.command.CommandSender;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.data.dto.OwnershipChangeResult;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.repository.IShopGroupRepository;
import org.fourz.BarterShops.data.repository.IShopRepository;
import org.fourz.BarterShops.shop.ShopManager;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.BarterShops.sign.SignManager;
import org.fourz.BarterShops.trade.TradeEngine;
import org.fourz.rvnkcore.util.log.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Ownership transfer (#2285): owner and group change in one repository call, a failed or
 * throwing write changes nothing, and a failed cache reload restores the old owner and group.
 */
@DisplayName("ShopOwnershipServiceImpl Tests")
class ShopOwnershipServiceImplTest {

    private static final int SHOP_ID = 42;
    private static final int OLD_GROUP = 7;

    private final UUID oldOwner = UUID.randomUUID();
    private final UUID newOwner = UUID.randomUUID();

    private BarterShops plugin;
    private IShopRepository shopRepository;
    private IShopGroupRepository groupRepository;
    private SignManager signManager;
    private TradeEngine tradeEngine;
    private ShopManager shopManager;
    private BarterSign reloadedSign;
    private CommandSender admin;
    private ShopOwnershipServiceImpl service;

    @BeforeEach
    void setUp() {
        plugin = mock(BarterShops.class);
        shopRepository = mock(IShopRepository.class);
        groupRepository = mock(IShopGroupRepository.class);
        signManager = mock(SignManager.class);
        tradeEngine = mock(TradeEngine.class);
        shopManager = mock(ShopManager.class);
        reloadedSign = mock(BarterSign.class);
        admin = mock(CommandSender.class);

        when(plugin.getShopRepository()).thenReturn(shopRepository);
        when(plugin.getShopGroupRepository()).thenReturn(groupRepository);
        when(plugin.getSignManager()).thenReturn(signManager);
        when(plugin.getTradeEngine()).thenReturn(tradeEngine);
        when(plugin.getShopManager()).thenReturn(shopManager);
        when(admin.hasPermission("bartershops.admin")).thenReturn(true);
        when(admin.getName()).thenReturn("console");

        ShopDataDTO shop = ShopDataDTO.builder()
            .shopId(SHOP_ID)
            .ownerUuid(oldOwner)
            .shopName("Barter Shop")
            .shopType(ShopDataDTO.ShopType.BARTER)
            .signLocation("world", 1, 64, 1)
            .chestLocation("world", 1, 63, 1)
            .isActive(true)
            .groupId(OLD_GROUP)
            .build();
        when(shopRepository.findById(SHOP_ID))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(shop)));

        // Direct executor: the transfer runs on the test thread
        service = new ShopOwnershipServiceImpl(plugin, mock(LogManager.class), Runnable::run);
    }

    private OwnershipChangeResult transfer() throws Exception {
        return service.transferOwnership(SHOP_ID, newOwner, admin).get(10, TimeUnit.SECONDS);
    }

    private void stubTransferWrite(CompletableFuture<Boolean> result) {
        when(shopRepository.updateOwnerAndGroup(SHOP_ID, oldOwner, newOwner, null)).thenReturn(result);
    }

    private void assertNothingChangedAfterFailedWrite() {
        verify(shopRepository, never()).save(any());
        // No compensating write: the transfer never landed
        verify(shopRepository, never()).updateOwnerAndGroup(eq(SHOP_ID), eq(newOwner), any(), any());
        verifyNoInteractions(signManager, tradeEngine, shopManager, groupRepository);
    }

    @Test
    @DisplayName("repository returns false: owner and sign group untouched, failure reported")
    void falseResultIsAFailedTransfer() throws Exception {
        stubTransferWrite(CompletableFuture.completedFuture(false));

        OwnershipChangeResult result = transfer();

        assertFalse(result.success());
        assertTrue(result.message().contains("ownership not changed"), result.message());
        assertNothingChangedAfterFailedWrite();
    }

    @Test
    @DisplayName("repository throws: owner and sign group untouched, failure reported")
    void exceptionIsAFailedTransfer() throws Exception {
        stubTransferWrite(CompletableFuture.failedFuture(new RuntimeException("connection reset")));

        OwnershipChangeResult result = transfer();

        assertFalse(result.success());
        assertTrue(result.message().contains("ownership not changed"), result.message());
        assertNothingChangedAfterFailedWrite();
    }

    @Test
    @DisplayName("repository throws synchronously: failure reported, nothing changed")
    void synchronousExceptionIsAFailedTransfer() throws Exception {
        when(shopRepository.updateOwnerAndGroup(SHOP_ID, oldOwner, newOwner, null))
            .thenThrow(new IllegalStateException("pool closed"));

        OwnershipChangeResult result = transfer();

        assertFalse(result.success());
        assertNothingChangedAfterFailedWrite();
    }

    @Test
    @DisplayName("missing shop repository: failure reported")
    void missingRepositoryIsAFailedTransfer() throws Exception {
        when(plugin.getShopRepository()).thenReturn(null);

        OwnershipChangeResult result = transfer();

        assertFalse(result.success());
        assertTrue(result.message().contains("ownership not changed"), result.message());
        verifyNoInteractions(signManager, tradeEngine, shopManager, groupRepository);
    }

    @Test
    @DisplayName("success: owner and group written together, sign group set to 0")
    void successUpdatesOwnerAndGroupTogether() throws Exception {
        stubTransferWrite(CompletableFuture.completedFuture(true));
        when(signManager.reloadShopFromDatabase(SHOP_ID))
            .thenReturn(CompletableFuture.completedFuture(reloadedSign));
        when(tradeEngine.invalidateSessionsForShop(SHOP_ID)).thenReturn(2);

        OwnershipChangeResult result = transfer();

        assertTrue(result.success(), result.message());
        assertEquals(oldOwner, result.oldOwner());
        assertEquals(newOwner, result.newOwner());
        assertEquals(2, result.sessionsInvalidated());
        // One statement: new owner and group_id = NULL
        verify(shopRepository).updateOwnerAndGroup(SHOP_ID, oldOwner, newOwner, null);
        verify(shopRepository, never()).save(any());
        verifyNoInteractions(groupRepository);
        verify(reloadedSign).setGroupId(0);
        verify(shopManager).invalidateSessionsForShop(reloadedSign);
    }

    @Test
    @DisplayName("cache reload fails: old owner and group restored, failure reported")
    void reloadFailureRestoresOwnerAndGroup() throws Exception {
        stubTransferWrite(CompletableFuture.completedFuture(true));
        when(signManager.reloadShopFromDatabase(SHOP_ID))
            .thenReturn(CompletableFuture.completedFuture(null));
        when(shopRepository.updateOwnerAndGroup(SHOP_ID, newOwner, oldOwner, OLD_GROUP))
            .thenReturn(CompletableFuture.completedFuture(true));

        OwnershipChangeResult result = transfer();

        assertFalse(result.success());
        assertTrue(result.message().contains("rolled back"), result.message());
        verify(shopRepository).updateOwnerAndGroup(SHOP_ID, newOwner, oldOwner, OLD_GROUP);
        verifyNoInteractions(tradeEngine, shopManager);
    }

    @Test
    @DisplayName("cache reload fails and rollback fails: failure says so")
    void failedRollbackIsReported() throws Exception {
        stubTransferWrite(CompletableFuture.completedFuture(true));
        when(signManager.reloadShopFromDatabase(SHOP_ID))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("timeout")));
        when(shopRepository.updateOwnerAndGroup(SHOP_ID, newOwner, oldOwner, OLD_GROUP))
            .thenReturn(CompletableFuture.completedFuture(false));

        OwnershipChangeResult result = transfer();

        assertFalse(result.success());
        assertTrue(result.message().contains("rollback failed"), result.message());
    }

    @Test
    @DisplayName("same owner: refused before any write")
    void sameOwnerIsRefused() throws Exception {
        OwnershipChangeResult result = service.transferOwnership(SHOP_ID, oldOwner, admin)
            .get(10, TimeUnit.SECONDS);

        assertFalse(result.success());
        verify(shopRepository, never()).updateOwnerAndGroup(anyInt(), any(), any(), any());
    }
}
