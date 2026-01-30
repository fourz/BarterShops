package org.fourz.BarterShops.sign;

import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.fourz.BarterShops.BarterShops;
import org.fourz.rvnkcore.util.log.LogManager;


public class SignInteraction {
    private static final String CLASS_NAME = "SignInteraction";
    private final BarterShops plugin;
    private final LogManager logger;

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
            barterSign.setMode(SignMode.SETUP);
            SignDisplay.updateSign(sign, barterSign);
            player.sendMessage("Entering shop configuration mode");
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
        
        switch (barterSign.getMode()) {
            case SETUP -> {
                logger.debug("Owner: SETUP -> TYPE");
                barterSign.setMode(SignMode.TYPE);
                player.sendMessage("Click to toggle shop type");
            }
            case TYPE -> {
                logger.debug("Owner: TYPE -> BOARD");
                barterSign.setMode(SignMode.BOARD);
                player.sendMessage("Click to edit the shop display");
            }
            case BOARD -> {                
                logger.debug("Owner: BOARD -> DELETE");
                barterSign.setMode(SignMode.DELETE);
                player.sendMessage("Break sign to confirm deletion");
            }
            case DELETE -> {
                logger.debug("Owner: DELETE -> SETUP");
                barterSign.setMode(SignMode.SETUP);
                player.sendMessage("Right-click sign with payment item to configure");
            }
            default -> {
                logger.warning("Unknown mode encountered: " + barterSign.getMode());
                barterSign.setMode( SignMode.BOARD);
            }
        }
        
        SignDisplay.updateSign(sign, barterSign);
    }

    private void handleCustomerRightClick(Player player, Sign sign, BarterSign barterSign) {
        logger.debug("Processing customer interaction");
        
        if (barterSign.getMode() != SignMode.BOARD) {
            logger.debug("Customer tried to interact with non-BOARD mode shop");
            player.sendMessage("This shop is currently being configured");
            return;
        }

        processTrade(player, sign, barterSign);
    }

    private void processTrade(Player player, Sign sign, BarterSign barterSign) {
        // TODO: Implement trade processing logic
        logger.debug("Processing trade for player: " + player.getName());
    }
}