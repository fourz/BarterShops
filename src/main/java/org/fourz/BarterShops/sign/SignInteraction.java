package org.fourz.BarterShops.sign;

import org.bukkit.Color;
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

    public void handlePunch(Player player, Sign sign, BarterSign barterSign, PlayerInteractEvent event) {
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

    public void handleClick(Player player, Sign sign, BarterSign barterSign) {
        debug.debug(String.format("Processing %s interaction in mode: %s", 
            player.getName(), barterSign.getMode()));

        if (barterSign.getOwner().equals(player.getUniqueId())) {
            handleOwnerClick(player, sign, barterSign);
        } else {
            handleCustomerClick(player, sign, barterSign);
        }
    }

    private void handleOwnerClick(Player player, Sign sign, BarterSign barterSign) {
        debug.debug("Processing owner interaction");
        
        switch (barterSign.getMode()) {
            case SETUP -> {
                debug.debug("Owner: SETUP -> TYPE");
                barterSign.setMode(SignMode.TYPE);
                player.sendMessage("Select shop type");
            }
            case TYPE -> {
                debug.debug("Owner: TYPE -> DISPLAY");
                barterSign.setMode(SignMode.DISPLAY);
                player.sendMessage(Color.GREEN + "Shop created successfully!");
            }
            case DISPLAY -> {                
                debug.debug("Owner: DISPLAY -> DELETE");
                barterSign.setMode(SignMode.DELETE);
                player.sendMessage(Color.RED + "Break sign to confirm deletion");
            }
            case DELETE -> {
                debug.debug("Owner: DELETE -> SETUP");
                barterSign.setMode(SignMode.TYPE);
                player.sendMessage("Select shop type");
            }
            default -> {
                debug.warning("Unknown mode encountered: " + barterSign.getMode());
                barterSign.setMode(SignMode.DISPLAY);
            }
        }
        
        SignDisplay.updateSign(sign, barterSign);
    }

    private void handleCustomerClick(Player player, Sign sign, BarterSign barterSign) {
        debug.debug("Processing customer interaction");
        
        if (barterSign.getMode() != SignMode.DISPLAY) {
            debug.debug("Customer tried to interact with non-DISPLAY mode shop");
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
