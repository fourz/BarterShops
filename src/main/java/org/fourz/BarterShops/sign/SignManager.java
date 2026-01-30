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
import org.bukkit.event.HandlerList;
import java.util.Collections;

import org.fourz.BarterShops.BarterShops;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.BarterShops.shop.ShopManager;
import org.fourz.BarterShops.shop.ShopMode;
import org.fourz.BarterShops.shop.ShopSession;

public class SignManager implements Listener {
    private static final String CLASS_NAME = "SignManager";
    private final BarterShops plugin;
    private final LogManager logger;
    private final Map<Block, BarterSign> barterSigns = new HashMap<>();
    private final SignInteraction signInteraction;
    private final ShopManager shopManager;
    
    public SignManager(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, CLASS_NAME);
        this.signInteraction = new SignInteraction(plugin);
        this.shopManager = plugin.getShopManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        logger.debug("SignManager initialized");
    }

    private void createBarterSign(Player player, Sign sign, Container container) {
        logger.debug("Creating new barter sign for " + player.getName() + " at " + sign.getLocation());
            
        String id = java.util.UUID.randomUUID().toString();
        
        BarterSign barterSign = new BarterSign.Builder()
            .id(id)
            .owner(player.getUniqueId())
            .container(container)
            .mode(SignMode.SETUP)
            .type(SignType.STACKABLE) 
            .signSideDisplayFront(sign.getSide(org.bukkit.block.sign.Side.FRONT))
            .signSideDisplayBack(sign.getSide(org.bukkit.block.sign.Side.BACK))
            .build();
            
        barterSigns.put(sign.getBlock(), barterSign);
        logger.info("Created barter sign (ID: " + id + ") for " + player.getName());
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {        
        if (event.getLine(0).equalsIgnoreCase("[barter]")) {
            try {
                Player player = event.getPlayer();
                Sign sign = (Sign) event.getBlock().getState();
                
                // Check if sign is already registered
                BarterSign existingSign = barterSigns.get(sign.getBlock());
                if (existingSign != null) {
                    logger.debug("Attempted creation of already existing barter sign by " + player.getName());
                    event.setCancelled(true);
                        
                    // If owner, treat as interaction
                    if (existingSign.getOwner().equals(player.getUniqueId())) {
                        logger.debug("Owner attempted recreation - treating as interaction");
                        handleSignInteraction(player, sign, existingSign);
                    } else {
                        player.sendMessage("This is already a barter shop!");
                    }
                    return;
                }

                // Continue with normal creation process for new signs
                if (player.hasPermission("bartershops.create")) {
                    logger.debug("Processing new barter sign creation attempt by " + player.getName());
                    
                    if (qualifySign(sign)) {
                        Container container = findAssociatedContainer(sign);
                        if (container != null) {
                            createBarterSign(player, sign, container);
                            player.sendMessage("Shop created successfully! Punch sign to configure.");
                        } else {
                            player.sendMessage("Error: Could not find associated container!");
                            event.setCancelled(true);
                        }
                    } else {
                        player.sendMessage("Invalid shop location! Place sign on or above a container.");
                        logger.warning("Invalid shop location attempt by " + player.getName());
                        event.setCancelled(true);
                    }
                } else {
                    player.sendMessage("You do not have permission to create a shop!");
                    logger.warning("Permission denied for shop creation: " + player.getName());
                    event.setCancelled(true);
                }
            } catch (Exception e) {
                logger.error("Error creating barter shop: " + e.getMessage(), e);
                event.getPlayer().sendMessage("Error creating shop: " + e.getMessage());
                event.setCancelled(true);
            }
        }
    }

    private Container findAssociatedContainer(Sign sign) {
        Block signBlock = sign.getBlock();
        
        // Check if it's a wall sign
        if (sign.getBlockData() instanceof org.bukkit.block.data.type.WallSign) {
            BlockFace attachedFace = ((org.bukkit.block.data.type.WallSign) sign.getBlockData())
                .getFacing().getOppositeFace();
            Block behind = signBlock.getRelative(attachedFace);
            if (behind.getState() instanceof Container) {
                return (Container) behind.getState();
            }
        }
        
        // Check below for standing signs
        Block below = signBlock.getRelative(BlockFace.DOWN);
        if (below.getState() instanceof Container) {
            return (Container) below.getState();
        }
        
        return null;
    }

    @EventHandler
    public void onSignBreak(BlockBreakEvent event) {
        // Handle shop sign removal
    }

    @EventHandler
    public void onSignClick(PlayerInteractEvent event) {
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || !(clickedBlock.getState() instanceof Sign)) {
            return;
        }

        Player player = event.getPlayer();
        Sign sign = (Sign) clickedBlock.getState();
        BarterSign barterSign = barterSigns.get(clickedBlock);

        try {
            event.setCancelled(true);
            
            if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
                handleLeftClickWithShop(player, sign, barterSign, event);
                return;
            }

            if (barterSign != null) {
                handleRightClickWithShop(player, sign, barterSign);
            }
        } catch (Exception e) {
            logger.error("Error processing sign interaction: " + e.getMessage(), e);
            player.sendMessage("An error occurred while processing your interaction");
        }
    }

    private void handleLeftClickWithShop(Player player, Sign sign, BarterSign barterSign, PlayerInteractEvent event) {
        ShopMode nextMode = calculateNextMode(player, sign, barterSign);
        if (nextMode != null) {
            ShopSession session = shopManager.getSession(player);
            session.setActiveSign(sign);
            shopManager.handleSignInteraction(player, nextMode);
        }
        signInteraction.handleLeftClick(player, sign, barterSign, event);
    }

    private void handleRightClickWithShop(Player player, Sign sign, BarterSign barterSign) {
        ShopMode nextMode = calculateNextMode(player, sign, barterSign);
        if (nextMode != null) {
            ShopSession session = shopManager.getSession(player);
            session.setActiveSign(sign);
            shopManager.handleSignInteraction(player, nextMode);
        }
        signInteraction.handleRightClick(player, sign, barterSign);
    }

    private ShopMode calculateNextMode(Player player, Sign sign, BarterSign barterSign) {
        if (barterSign == null) {
            return null;
        }

        ShopSession session = shopManager.getSession(player);
        ShopMode currentMode = session.getCurrentMode();

        // Mode transition logic
        return switch (currentMode) {
            case SETUP_SELL -> ShopMode.SETUP_STACK;
            case SETUP_STACK -> ShopMode.TYPE;
            case TYPE -> null; // End of setup flow
            case DELETE -> null; // One-time action
            case BOARD_SETUP -> ShopMode.BOARD_DISPLAY;
            case BOARD_DISPLAY -> null;
            case HELP -> null;
            default -> ShopMode.SETUP_SELL; // Start of setup flow
        };
    }

    private boolean qualifySign(Sign sign) {
        logger.debug("Qualifying sign at location: " + sign.getLocation());
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

    private void handleSignInteraction(Player player, Sign sign, BarterSign barterSign) {
        try {
            logger.debug("Delegating sign interaction to SignInteraction handler");
            signInteraction.handleRightClick(player, sign, barterSign);
        } catch (Exception e) {
            logger.error("Error in sign interaction delegation: " + e.getMessage(), e);
            player.sendMessage("An error occurred while processing your interaction");
        }
    }

    public void cleanup() {
        logger.debug("Cleaning up SignManager - clearing " + barterSigns.size() + " registered signs");
        HandlerList.unregisterAll(this);
        barterSigns.clear();
        logger.info("SignManager cleanup completed");
    }
    
    public Map<Block, BarterSign> getBarterSigns() {
        return Collections.unmodifiableMap(barterSigns);
    }
}