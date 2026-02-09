package org.fourz.BarterShops.sign;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.trade.TradeEngine;
import org.fourz.BarterShops.trade.TradeSession;
import org.fourz.BarterShops.trade.TradeConfirmationGUI;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


public class SignInteraction {
    private static final String CLASS_NAME = "SignInteraction";
    private static final long REVERT_DELAY_TICKS = 200L; // 10 seconds
    private static final long STATUS_DISPLAY_TICKS = 60L; // 3 seconds

    private final BarterShops plugin;
    private final LogManager logger;
    private final Map<Location, BukkitTask> activeRevertTasks = new ConcurrentHashMap<>();

    public SignInteraction(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, CLASS_NAME);
    }

    public void handleLeftClick(Player player, Sign sign, BarterSign barterSign, PlayerInteractEvent event) {
        if (!player.hasPermission("bartershops.configure")) {
            logger.debug("Punch ignored - player lacks configure permission");
            return;
        }

        if (barterSign != null && barterSign.getOwner().equals(player.getUniqueId())) {
            logger.debug("Owner punch detected - entering configuration mode");
            cancelRevert(sign.getLocation());
            barterSign.setMode(SignMode.SETUP);
            SignDisplay.updateSign(sign, barterSign);
            event.setCancelled(true);
        }
    }

    public void handleRightClick(Player player, Sign sign, BarterSign barterSign) {
        logger.debug(String.format("Processing %s interaction in mode: %s",
            player.getName(), barterSign.getMode()));

        if (barterSign.getOwner().equals(player.getUniqueId())) {
            handleOwnerRightClick(player, sign, barterSign);
        } else {
            handleCustomerRightClick(player, sign, barterSign);
        }
    }

    private void handleOwnerRightClick(Player player, Sign sign, BarterSign barterSign) {
        logger.debug("Processing owner interaction");
        cancelRevert(sign.getLocation());

        switch (barterSign.getMode()) {
            case SETUP -> {
                logger.debug("Owner: SETUP -> TYPE");
                barterSign.setMode(SignMode.TYPE);
            }
            case TYPE -> {
                logger.debug("Owner: TYPE -> BOARD");
                barterSign.setMode(SignMode.BOARD);
            }
            case BOARD -> {
                logger.debug("Owner: BOARD -> DELETE");
                barterSign.setMode(SignMode.DELETE);
                scheduleRevert(sign, barterSign);
            }
            case DELETE -> {
                logger.debug("Owner: DELETE -> SETUP");
                barterSign.setMode(SignMode.SETUP);
            }
            case HELP -> {
                logger.debug("Owner: HELP -> BOARD");
                barterSign.setMode(SignMode.BOARD);
                scheduleRevert(sign, barterSign);
            }
            default -> {
                logger.warning("Unknown mode encountered: " + barterSign.getMode());
                barterSign.setMode(SignMode.BOARD);
            }
        }

        SignDisplay.updateSign(sign, barterSign);
    }

    private void handleCustomerRightClick(Player player, Sign sign, BarterSign barterSign) {
        logger.debug("Processing customer interaction");

        if (barterSign.getMode() != SignMode.BOARD) {
            logger.debug("Customer tried to interact with non-BOARD mode shop");
            return;
        }

        processTrade(player, sign, barterSign);
    }

    private void processTrade(Player player, Sign sign, BarterSign barterSign) {
        logger.debug("Processing trade for player: " + player.getName());

        TradeEngine tradeEngine = plugin.getTradeEngine();
        TradeConfirmationGUI confirmationGUI = plugin.getTradeConfirmationGUI();

        // Check fallback mode
        if (tradeEngine.isInFallbackMode()) {
            showTemporaryStatus(sign, barterSign, "\u00A7cTemporarily", "\u00A7cunavailable");
            logger.warning("Trade rejected - system in fallback mode");
            return;
        }

        // Get shop container for trade items
        Container shopContainer = barterSign.getShopContainer();
        if (shopContainer == null) {
            shopContainer = barterSign.getContainer();
        }

        if (shopContainer == null) {
            showTemporaryStatus(sign, barterSign, "\u00A7cNo inventory", "\u00A7cconfigured");
            logger.debug("Trade rejected - no shop container");
            return;
        }

        // Get offered item (first non-empty slot in shop container)
        ItemStack offeredItem = null;
        int offeredQuantity = 0;
        for (ItemStack item : shopContainer.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                offeredItem = item.clone();
                offeredQuantity = item.getAmount();
                break;
            }
        }

        if (offeredItem == null) {
            showTemporaryStatus(sign, barterSign, "\u00A7cOut of", "\u00A7cstock");
            logger.debug("Trade rejected - shop out of stock");
            return;
        }

        // Get requested payment from player's main hand
        ItemStack requestedItem = player.getInventory().getItemInMainHand();
        int requestedQuantity = 0;

        if (requestedItem != null && requestedItem.getType() != Material.AIR) {
            requestedQuantity = 1; // Default to 1 item as payment
        } else {
            showTemporaryStatus(sign, barterSign, "\u00A7eHold item", "\u00A7eto trade");
            logger.debug("Trade rejected - player not holding payment item");
            return;
        }

        // Initiate trade session
        Optional<TradeSession> sessionOpt = tradeEngine.initiateTrade(player, barterSign);
        if (sessionOpt.isEmpty()) {
            showTemporaryStatus(sign, barterSign, "\u00A7cTrade", "\u00A7cunavailable");
            logger.debug("Trade rejected - failed to create session");
            return;
        }

        TradeSession session = sessionOpt.get();

        // Configure trade items
        session.setOfferedItem(offeredItem, offeredQuantity);
        session.setRequestedItem(requestedItem, requestedQuantity);
        session.setState(TradeSession.TradeState.AWAITING_BUYER_CONFIRM);

        logger.debug("Trade session created: " + session.getSessionId());

        // Open confirmation GUI
        confirmationGUI.openConfirmation(player, session,
            // On confirm
            (confirmedSession) -> {
                logger.debug("Trade confirmed for session: " + confirmedSession.getSessionId());
                confirmedSession.setState(TradeSession.TradeState.AWAITING_FINAL_CONFIRM);

                tradeEngine.executeTrade(confirmedSession.getSessionId())
                    .thenAccept(result -> {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (result.success()) {
                                player.sendMessage(ChatColor.GREEN + "Trade complete!");
                                logger.info("Trade completed: " + result.transactionId());
                            } else {
                                player.sendMessage(ChatColor.RED + "Trade failed: " + result.message());
                                logger.debug("Trade failed: " + result.message());
                            }
                        });
                    });
            },
            // On cancel
            (cancelledSession) -> {
                logger.debug("Trade cancelled for session: " + cancelledSession.getSessionId());
                tradeEngine.cancelSession(cancelledSession.getSessionId());
                player.sendMessage(ChatColor.YELLOW + "Trade cancelled.");
            }
        );
    }

    // ========== Temporary Status Display ==========

    private void showTemporaryStatus(Sign sign, BarterSign barterSign, String line1, String line2) {
        SignDisplay.displayTemporaryMessage(sign, line1, line2);
        scheduleRevert(sign, barterSign, STATUS_DISPLAY_TICKS);
    }

    // ========== Auto-Revert Scheduling ==========

    private void scheduleRevert(Sign sign, BarterSign barterSign) {
        scheduleRevert(sign, barterSign, REVERT_DELAY_TICKS);
    }

    private void scheduleRevert(Sign sign, BarterSign barterSign, long delayTicks) {
        Location loc = sign.getLocation();
        if (loc == null) return;
        cancelRevert(loc);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                barterSign.setMode(SignMode.BOARD);
                SignDisplay.updateSign(sign, barterSign);
                activeRevertTasks.remove(loc);
            }
        }.runTaskLater(plugin, delayTicks);

        activeRevertTasks.put(loc, task);
    }

    private void cancelRevert(Location loc) {
        if (loc == null) return;
        BukkitTask existing = activeRevertTasks.remove(loc);
        if (existing != null) {
            existing.cancel();
        }
    }

    public void cleanup() {
        activeRevertTasks.values().forEach(BukkitTask::cancel);
        activeRevertTasks.clear();
    }
}
