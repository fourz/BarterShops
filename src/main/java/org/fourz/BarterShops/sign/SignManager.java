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
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.event.HandlerList;

import org.fourz.BarterShops.Main;
import org.fourz.BarterShops.util.Debug;

public class SignManager implements Listener {
    private static final String CLASS_NAME = "SignManager";
    private final Main plugin;
    private final Debug debug;
    private final Map<Location, BarterSign> barterSigns = new HashMap<>();
    
    public SignManager(Main plugin) {
        this.plugin = plugin;
        this.debug = new Debug(plugin, CLASS_NAME, plugin.getDebugger().getLogLevel()) {};
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        debug.debug("SignManager initialized");
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {        
        if (event.getLine(0).equalsIgnoreCase("[barter]")) {
            try {
                if (event.getPlayer().hasPermission("bartershops.create")) {
                    Sign sign = (Sign) event.getBlock().getState();
                    debug.debug("Processing barter sign creation attempt by " + event.getPlayer().getName());
                    
                    if (qualifySign(sign)) {
                        event.getPlayer().sendMessage("Shop created successfully!");
                        debug.info("Barter shop created by " + event.getPlayer().getName());
                    } else {
                        event.getPlayer().sendMessage("Invalid shop location! Place sign on or above a container.");
                        debug.warning("Invalid shop location attempt by " + event.getPlayer().getName());
                        event.setCancelled(true);
                    }
                } else {
                    event.getPlayer().sendMessage("You do not have permission to create a shop!");
                    debug.warning("Permission denied for shop creation: " + event.getPlayer().getName());
                    event.setCancelled(true);
                }
            } catch (Exception e) {
                debug.error("Error creating barter shop: " + e.getMessage(), e);
                event.getPlayer().sendMessage("Error creating shop: " + e.getMessage());
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onSignBreak(BlockBreakEvent event) {
        // Handle shop sign removal
    }

    @EventHandler
    public void onSignClick(PlayerInteractEvent event) {
        // Early validation of sign click
        if (event.getClickedBlock() == null || !(event.getClickedBlock().getState() instanceof Sign)) {
            debug.debug("Click event ignored - not a sign");
            return;
        }

        Player player = event.getPlayer();
        Sign sign = (Sign) event.getClickedBlock().getState();
        debug.debug(String.format("Sign interaction by %s at %s", player.getName(), sign.getLocation()));

        // Handle punch (left-click) interactions
        if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
            handleSignPunch(player, sign, event);
            return;
        }

        // Handle right-click interactions
        BarterSign barterSign = barterSigns.get(sign.getLocation());
        if (barterSign == null) {
            debug.debug("Right-click ignored - not a registered barter sign");
            return;
        }

        try {
            handleSignInteraction(player, sign, barterSign);
        } catch (Exception e) {
            debug.error("Error processing sign interaction: " + e.getMessage(), e);
            player.sendMessage("An error occurred while processing your interaction");
        }
    }

    private void handleSignPunch(Player player, Sign sign, PlayerInteractEvent event) {
        if (!player.hasPermission("bartershops.configure")) {
            debug.debug("Punch ignored - player lacks configure permission");
            return;
        }

        BarterSign barterSign = barterSigns.get(sign.getLocation());
        if (barterSign != null && barterSign.getOwner().equals(player.getUniqueId())) {
            debug.debug("Owner punch detected - entering configuration mode");
            barterSign.setMode(SignMode.SETUP);
            SignDisplay.updateSign(sign, barterSign);
            player.sendMessage("Entering shop configuration mode");
            event.setCancelled(true);
        }
    }

    private void handleSignInteraction(Player player, Sign sign, BarterSign barterSign) {
        debug.debug(String.format("Processing %s interaction in mode: %s", 
            player.getName(), barterSign.getMode()));

        switch (barterSign.getMode()) {
            case SETUP -> {
                debug.debug("Handling SETUP mode");
                handleSetupClick(player, sign, barterSign);
            }
            case DISPLAY -> {
                debug.debug("Handling DISPLAY mode");
                handleMainClick(player, sign, barterSign);
            }
            case TYPE -> {
                debug.debug("Handling TYPE mode");
                handleTypeClick(player, sign, barterSign);
            }
            case HELP -> {
                debug.debug("Handling HELP mode");
                handleHelpClick(player, sign, barterSign);
            }
            case DELETE -> {
                debug.debug("Handling DELETE mode");
                handleDeleteClick(player, sign, barterSign);
            }
            default -> debug.warning("Unknown sign mode: " + barterSign.getMode());
        }

        debug.debug("Updating sign display after interaction");
        SignDisplay.updateSign(sign, barterSign);
    }

    private boolean qualifySign(Sign sign) {
        debug.debug("Qualifying sign at location: " + sign.getLocation());
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
        debug.debug("Cleaning up SignManager...");
        HandlerList.unregisterAll(this);
        barterSigns.clear();
        debug.info("SignManager cleanup completed");
    }
}