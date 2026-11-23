package org.fourz.BarterShops.trade;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitScheduler;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.config.ConfigManager;
import org.fourz.BarterShops.service.ITradeService.TradeResultDTO;
import org.fourz.BarterShops.service.impl.TradeServiceImpl;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.rvnkcore.util.log.LogManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Regression test for the GUI trade dupe in #2116: two buyers confirm against a shop with
 * stock for exactly one trade.
 *
 * What this proves: executeTrade hands the whole validate-then-remove step to the server
 * scheduler (runTask) as ONE task, so the main thread can never interleave two buyers
 * between the stock check and the stock removal. The scheduler here is a queue that runs
 * only when the test drains it, on the test thread, standing in for the main thread.
 *
 * What this does not prove: real Paper tick scheduling, real chest inventories, or the
 * in-game GUI click path. Those need a live server (the two-player check on #2116).
 */
@DisplayName("TradeEngine two buyers, one stock (#2116)")
class TradeEngineRaceTest {

    private static final Material DIAMOND = kind("DIAMOND");
    private static final Material EMERALD = kind("EMERALD");

    /** Array-backed Inventory stub that records every thread that touches it. */
    private static Inventory fakeInventory(Inventory mock, int size, Set<Thread> touchedBy) {
        ItemStack[][] store = { new ItemStack[size] };
        when(mock.getContents()).thenAnswer(inv -> {
            touchedBy.add(Thread.currentThread());
            return store[0].clone();
        });
        when(mock.getStorageContents()).thenAnswer(inv -> store[0].clone());
        doAnswer(inv -> {
            touchedBy.add(Thread.currentThread());
            store[0][inv.<Integer>getArgument(0)] = inv.getArgument(1);
            return null;
        }).when(mock).setItem(anyInt(), any());
        doAnswer(inv -> {
            touchedBy.add(Thread.currentThread());
            ItemStack[] in = inv.getArgument(0);
            store[0] = in.clone();
            return null;
        }).when(mock).setContents(any());
        when(mock.addItem(any(ItemStack[].class))).thenAnswer(inv -> {
            touchedBy.add(Thread.currentThread());
            HashMap<Integer, ItemStack> failed = new HashMap<>();
            ItemStack[] items = java.util.Arrays.stream(inv.getArguments())
                    .map(o -> (ItemStack) o).toArray(ItemStack[]::new);
            for (ItemStack it : items) {
                boolean placed = false;
                for (int i = 0; i < store[0].length && !placed; i++) {
                    ItemStack cur = store[0][i];
                    if (cur == null) {
                        store[0][i] = it.clone();
                        placed = true;
                    } else if (cur.isSimilar(it) && cur.getAmount() + it.getAmount() <= cur.getMaxStackSize()) {
                        cur.setAmount(cur.getAmount() + it.getAmount());
                        placed = true;
                    }
                }
                if (!placed) failed.put(failed.size(), it);
            }
            return failed;
        });
        return mock;
    }


    /**
     * Real Material/ItemStack need a Bukkit registry (Material.isAir fails with "No registry
     * present"), so items here are stateful mocks: a mock Material per kind, and stacks that
     * track amount and compare by kind like ItemStack.isSimilar does for meta-less items.
     */
    private static Material kind(String name) {
        Material m = mock(Material.class);
        doReturn(false).when(m).isAir();
        doReturn(name).when(m).name();
        doReturn(name).when(m).toString();
        return m;
    }

    private static ItemStack stack(Material kind, int amount) {
        int[] qty = {amount};
        ItemStack it = mock(ItemStack.class);
        when(it.getType()).thenReturn(kind);
        when(it.getAmount()).thenAnswer(inv -> qty[0]);
        doAnswer(inv -> {
            qty[0] = inv.getArgument(0);
            return null;
        }).when(it).setAmount(anyInt());
        when(it.getMaxStackSize()).thenReturn(64);
        when(it.hasItemMeta()).thenReturn(false);
        when(it.isSimilar(any())).thenAnswer(inv -> {
            ItemStack o = inv.getArgument(0);
            return o != null && o.getType() == kind;
        });
        when(it.clone()).thenAnswer(inv -> stack(kind, qty[0]));
        return it;
    }

    private static int count(Inventory inv, Material m) {
        int n = 0;
        for (ItemStack s : inv.getContents()) {
            if (s != null && s.getType() == m) n += s.getAmount();
        }
        return n;
    }

    private static Player buyer(UUID id, String name, PlayerInventory inv) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(id);
        when(p.getName()).thenReturn(name);
        when(p.isOnline()).thenReturn(true);
        when(p.getInventory()).thenReturn(inv);
        return p;
    }

    @Test
    @DisplayName("exactly one buyer gets the stock; the other is refused with nothing taken")
    void twoBuyersOneStock_exactlyOneTradeSucceeds() throws Exception {
        Set<Thread> shopTouchedBy = ConcurrentHashMap.newKeySet();
        Set<Thread> unusedA = ConcurrentHashMap.newKeySet();
        Set<Thread> unusedB = ConcurrentHashMap.newKeySet();

        // Scheduler: queue tasks, run them only when the "main thread" (this thread) drains.
        Queue<Runnable> mainThreadQueue = new ArrayDeque<>();
        BarterShops plugin = mock(BarterShops.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(any(org.bukkit.plugin.Plugin.class), any(Runnable.class))).thenAnswer(inv -> {
            mainThreadQueue.add(inv.getArgument(1));
            return null;
        });
        ConfigManager config = mock(ConfigManager.class);
        when(plugin.getConfigManager()).thenReturn(config);
        when(config.getInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        when(config.getLong(anyString(), anyLong())).thenAnswer(inv -> inv.getArgument(1));
        TradeServiceImpl tradeService = mock(TradeServiceImpl.class);
        when(tradeService.saveTrade(any())).thenReturn(new CompletableFuture<>());
        when(plugin.getTradeService()).thenReturn(tradeService);

        // Shop: stock for exactly one trade (1 diamond), nothing else.
        Inventory shopInv = fakeInventory(mock(Inventory.class), 27, shopTouchedBy);
        shopInv.setItem(0, stack(DIAMOND, 1));
        Chest chest = mock(Chest.class);
        when(chest.getInventory()).thenReturn(shopInv);
        UUID ownerId = UUID.randomUUID();
        BarterSign shop = mock(BarterSign.class);
        when(shop.getOwner()).thenReturn(ownerId);
        when(shop.getShopContainer()).thenReturn(chest);

        // Two buyers, each holding exactly the price (1 emerald).
        PlayerInventory invA = (PlayerInventory) fakeInventory(mock(PlayerInventory.class), 36, unusedA);
        PlayerInventory invB = (PlayerInventory) fakeInventory(mock(PlayerInventory.class), 36, unusedB);
        invA.setItem(0, stack(EMERALD, 1));
        invB.setItem(0, stack(EMERALD, 1));
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        Player buyerA = buyer(idA, "BuyerA", invA);
        Player buyerB = buyer(idB, "BuyerB", invB);

        TradeEngine engine;
        try (var logs = mockStatic(LogManager.class)) {
            logs.when(() -> LogManager.getInstance(any(), anyString())).thenReturn(mock(LogManager.class));
            engine = new TradeEngine(plugin);
        }

        TradeSession sessionA = engine.initiateTrade(buyerA, shop).orElseThrow();
        TradeSession sessionB = engine.initiateTrade(buyerB, shop).orElseThrow();
        for (TradeSession s : new TradeSession[] {sessionA, sessionB}) {
            s.setOfferedItem(stack(DIAMOND, 1), 1);
            s.setRequestedItem(stack(EMERALD, 1), 1);
        }
        shopTouchedBy.clear();

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(idA)).thenReturn(buyerA);
            bukkit.when(() -> Bukkit.getPlayer(idB)).thenReturn(buyerB);

            // Both buyers confirm "at the same time": both requests are submitted before any runs.
            CompletableFuture<TradeResultDTO> futureA = engine.executeTrade(sessionA.getSessionId());
            CompletableFuture<TradeResultDTO> futureB = engine.executeTrade(sessionB.getSessionId());

            // Atomicity: nothing may have validated or touched stock before the main thread runs.
            assertEquals(2, mainThreadQueue.size(),
                    "each trade must be one scheduler task (validate + remove together on the main thread)");
            assertFalse(futureA.isDone() || futureB.isDone(),
                    "no trade may complete before the main thread runs it");
            assertTrue(shopTouchedBy.isEmpty(),
                    "stock must not be read or changed off the main thread: " + shopTouchedBy);

            Thread mainThread = Thread.currentThread();
            while (!mainThreadQueue.isEmpty()) {
                mainThreadQueue.poll().run();
            }

            TradeResultDTO resultA = futureA.get();
            TradeResultDTO resultB = futureB.get();
            assertEquals(Set.of(mainThread), shopTouchedBy,
                    "all stock access must be on the main thread; results were " + resultA + " / " + resultB);

            // Exactly one trade succeeds; the first queued buyer wins, the second is refused.
            assertTrue(resultA.success(), "first buyer should succeed: " + resultA);
            assertFalse(resultB.success(), "second buyer must be refused: " + resultB);

            // Stock: the single diamond moved to buyer A, the payment moved into the shop.
            assertEquals(0, count(shopInv, DIAMOND), "shop stock must be gone exactly once");
            assertEquals(1, count(shopInv, EMERALD), "shop receives exactly one payment");
            assertEquals(1, count(invA, DIAMOND));
            assertEquals(0, count(invA, EMERALD));

            // Refused buyer: nothing taken, nothing given.
            assertEquals(1, count(invB, EMERALD), "refused buyer keeps the payment");
            assertEquals(0, count(invB, DIAMOND), "refused buyer gets no item");

            // Dupe check across the whole system: one diamond, one emerald each side, total unchanged.
            assertEquals(1, count(shopInv, DIAMOND) + count(invA, DIAMOND)
                    + count(invB, DIAMOND), "total diamonds must stay 1");
            assertEquals(2, count(shopInv, EMERALD) + count(invA, EMERALD)
                    + count(invB, EMERALD) , "total emeralds must stay 2");
        }
    }
}
