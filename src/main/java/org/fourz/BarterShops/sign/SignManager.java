package org.fourz.BarterShops.sign;

import org.bukkit.block.Sign;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.fourz.BarterShops.Main;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.event.HandlerList;

public class SignManager implements Listener {
    private final Main plugin;
    private final Map<Location, BarterSign> barterSigns = new HashMap<>();
    
    public SignManager(Main plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {        
        // Handle sign edits
        // Check to see if top line is '[barter]'
        if (event.getLine(0).equalsIgnoreCase("[barter]")) {
            //check to see if the player has permission to create a shop
            if (event.getPlayer().hasPermission("bartershops.create")) {
                //check to see if the sign is valid
                if (qualifySign((Sign) event.getBlock().getState())) {
                    //save the shop to the database
                    //saveShop(event.getBlock().getLocation(), event.getPlayer().getUniqueId());
                    event.getPlayer().sendMessage("Shop created successfully!");
                } else {
                    event.getPlayer().sendMessage("Invalid shop sign!");
                }
            } else {
                event.getPlayer().sendMessage("You do not have permission to create a shop!");
            }
        }

    }

    @EventHandler
    public void onSignBreak(BlockBreakEvent event) {
        // Handle shop sign removal
    }

    @EventHandler
    public void onSignClick(PlayerInteractEvent event) {
        if (!(event.getClickedBlock() != null && event.getClickedBlock().getState() instanceof Sign)) {
            return;
        }

        Sign sign = (Sign) event.getClickedBlock().getState();
        BarterSign barterSign = barterSigns.get(sign.getLocation());
        
        if (barterSign == null) return;

        // Handle click based on current mode
        switch (barterSign.getMode()) {
            case SETUP:
                handleSetupClick(event.getPlayer(), sign, barterSign);
                break;
            case DISPLAY:
                handleMainClick(event.getPlayer(), sign, barterSign);
                break;
            case TYPE:
                handleTypeClick(event.getPlayer(), sign, barterSign);
                break;
            case HELP:
                handleHelpClick(event.getPlayer(), sign, barterSign);
                break;
            case DELETE:
                handleDeleteClick(event.getPlayer(), sign, barterSign);
                break;
        }
        
        // Update sign display after handling click
        SignDisplay.updateSign(sign, barterSign);
    }

    private boolean qualifySign(Sign sign) {
        Block signBlock = sign.getBlock();
        BlockFace attachedFace = ((org.bukkit.block.data.type.WallSign) sign.getBlockData()).getFacing().getOppositeFace();
        
        // Check block behind sign
        Block behindBlock = signBlock.getRelative(attachedFace);
        if (behindBlock.getState() instanceof Container) {
            return true;
        }
        
        // Check block below sign
        Block belowBlock = signBlock.getRelative(BlockFace.DOWN);
        if (belowBlock.getState() instanceof Container) {
            return true;
        }
        
        return false;
    }

    private void handleTrade(Player player, Sign sign) {
        // Process the actual trade
    }

    private void handleSetupClick(Player player, Sign sign, BarterSign barterSign) {
        // Handle setup mode interactions
        barterSign.setMode(SignMode.DISPLAY);
    }

    private void handleMainClick(Player player, Sign sign, BarterSign barterSign) {
        // Handle main mode interactions
    }

    private void handleTypeClick(Player player, Sign sign, BarterSign barterSign) {
        // Handle type selection mode interactions
    }

    private void handleHelpClick(Player player, Sign sign, BarterSign barterSign) {
        // Handle help mode interactions
    }

    private void handleDeleteClick(Player player, Sign sign, BarterSign barterSign) {
        // Handle delete confirmation mode interactions
    }
    
    public void cleanup() {
        HandlerList.unregisterAll(this);
        barterSigns.clear();
    }
}