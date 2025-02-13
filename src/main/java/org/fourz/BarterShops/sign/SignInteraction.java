package org.fourz.BarterShops.sign;

import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.fourz.BarterShops.Main;
import org.fourz.BarterShops.util.Debug;
//import org.fourz.BarterShops.util.ChatColor


public class SignInteraction {
    private static final String CLASS_NAME = "SignInteraction";
    private final Main plugin;
    private final Debug debug;

    public SignInteraction(Main plugin) {
        this.plugin = plugin;
        this.debug = new Debug(plugin, CLASS_NAME, plugin.getDebugger().getLogLevel()) {};
    }

    public void handleLeftClick(Player player, Sign sign, BarterSign barterSign, PlayerInteractEvent event) {
        if (!player.hasPermission("bartershops.configure")) {
            debug.debug("Punch ignored - player lacks configure permission");
            return;
        }

        if (barterSign != null && barterSign.getOwner().equals(player.getUniqueId())) {
            debug.debug("Owner punch detected - entering configuration mode");
            barterSign.setMode(SignMode.SETUP);
            SignDisplay.updateSign(sign, barterSign);
            player.sendMessage("Entering shop configuration mode");
            event.setCancelled(true);
        }
    }

    public void handleRightClick(Player player, Sign sign, BarterSign barterSign) {
        debug.debug(String.format("Processing %s interaction in mode: %s", 
            player.getName(), barterSign.getMode()));

        if (barterSign.getOwner().equals(player.getUniqueId())) {
            handleOwnerRightClick(player, sign, barterSign);
        } else {
            handleCustomerRightClick(player, sign, barterSign);
        }
    }

    private void handleOwnerRightClick(Player player, Sign sign, BarterSign barterSign) {
        debug.debug("Processing owner interaction");
        
        switch (barterSign.getMode()) {
            case SETUP -> {
                debug.debug("Owner: SETUP -> TYPE");
                barterSign.setMode(SignMode.TYPE);
                player.sendMessage("Click to toggle shop type");
            }
            case TYPE -> {
                debug.debug("Owner: TYPE -> BOARD");
                barterSign.setMode(SignMode.BOARD);
                player.sendMessage("Click to edit the shop display");
            }
            case BOARD -> {                
                debug.debug("Owner: BOARD -> DELETE");
                barterSign.setMode(SignMode.DELETE);
                player.sendMessage("Break sign to confirm deletion");
            }
            case DELETE -> {
                debug.debug("Owner: DELETE -> SETUP");
                barterSign.setMode(SignMode.SETUP);
                player.sendMessage("Right-click sign with payment item to configure");
            }
            default -> {
                debug.warning("Unknown mode encountered: " + barterSign.getMode());
                barterSign.setMode( SignMode.BOARD);
            }
        }
        
        SignDisplay.updateSign(sign, barterSign);
    }

    private void handleCustomerRightClick(Player player, Sign sign, BarterSign barterSign) {
        debug.debug("Processing customer interaction");
        
        if (barterSign.getMode() != SignMode.BOARD) {
            debug.debug("Customer tried to interact with non-BOARD mode shop");
            player.sendMessage("This shop is currently being configured");
            return;
        }

        processTrade(player, sign, barterSign);
    }

    private void processTrade(Player player, Sign sign, BarterSign barterSign) {
        // TODO: Implement trade processing logic
        debug.debug("Processing trade for player: " + player.getName());
    }
}
