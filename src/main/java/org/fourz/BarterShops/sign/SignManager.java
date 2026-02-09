package org.fourz.BarterShops.sign;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.HandlerList;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.dto.ShopDataDTO.ShopType;
import org.fourz.rvnkcore.util.log.LogManager;

public class SignManager implements Listener {
    private static final String CLASS_NAME = "SignManager";
    private final BarterShops plugin;
    private final LogManager logger;
    private final Map<Location, BarterSign> barterSigns = new ConcurrentHashMap<>();
    private final SignInteraction signInteraction;

    public SignManager(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, CLASS_NAME);
        this.signInteraction = new SignInteraction(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        logger.debug("SignManager initialized");
    }

    /**
     * Loads persisted signs from the database and populates the in-memory map.
     * Call after database layer is initialized.
     */
    public void loadSignsFromDatabase() {
        if (plugin.getShopRepository() == null) {
            logger.debug("Shop repository not available - skipping sign hydration");
            return;
        }

        try {
            // Synchronous during startup: DB query blocks briefly, then block access
            // runs on main thread (required for Bukkit API safety)
            java.util.List<ShopDataDTO> shops = plugin.getShopRepository().findAllActive().join();
            int loaded = 0;
            int skippedNoLocation = 0;
            int skippedNoSign = 0;
            for (ShopDataDTO shop : shops) {
                Location signLoc = shop.getSignLocation();
                if (signLoc == null || signLoc.getWorld() == null) {
                    skippedNoLocation++;
                    continue;
                }

                Block block = signLoc.getBlock();
                if (!(block.getState() instanceof Sign)) {
                    skippedNoSign++;
                    continue;
                }

                Sign sign = (Sign) block.getState();
                Container container = findAssociatedContainer(sign);

                BarterSign barterSign = new BarterSign.Builder()
                    .id(shop.getShopIdString())
                    .owner(shop.ownerUuid())
                    .signLocation(signLoc)
                    .container(container)
                    .mode(SignMode.BOARD)
                    .type(SignType.STACKABLE)
                    .signSideDisplayFront(sign.getSide(org.bukkit.block.sign.Side.FRONT))
                    .signSideDisplayBack(sign.getSide(org.bukkit.block.sign.Side.BACK))
                    .build();

                barterSigns.put(signLoc, barterSign);
                loaded++;
            }
            logger.info("Hydrated " + loaded + "/" + shops.size() + " barter signs from database"
                + (skippedNoLocation > 0 ? " (skipped " + skippedNoLocation + " no location)" : "")
                + (skippedNoSign > 0 ? " (skipped " + skippedNoSign + " no sign block)" : ""));
        } catch (Exception ex) {
            logger.error("Failed to load signs from database", ex);
        }
    }

    private void createBarterSign(Player player, Sign sign, Container container) {
        logger.debug("Creating new barter sign for " + player.getName() + " at " + sign.getLocation());

        String id = java.util.UUID.randomUUID().toString();
        Location signLocation = sign.getBlock().getLocation();

        BarterSign barterSign = new BarterSign.Builder()
            .id(id)
            .owner(player.getUniqueId())
            .signLocation(signLocation)
            .container(container)
            .mode(SignMode.SETUP)
            .type(SignType.STACKABLE)
            .signSideDisplayFront(sign.getSide(org.bukkit.block.sign.Side.FRONT))
            .signSideDisplayBack(sign.getSide(org.bukkit.block.sign.Side.BACK))
            .build();

        barterSigns.put(signLocation, barterSign);
        logger.info("Created barter sign (ID: " + id + ") for " + player.getName());

        // FIX Bug #b9171695: Persist the sign to database
        persistBarterSign(barterSign, container);
    }

    /**
     * Persists a newly created barter sign to the database.
     * This ensures the sign survives server restarts.
     * FIX Bug #b9171695: Persist shops created via [barter] signs
     */
    private void persistBarterSign(BarterSign barterSign, Container container) {
        if (plugin.getShopRepository() == null) {
            logger.warning("Shop repository not available - barter sign will not persist after restart");
            return;
        }

        Location signLoc = barterSign.getSignLocation();
        Location containerLoc = container != null ? container.getLocation() : signLoc;

        // Create DTO for persistence
        ShopDataDTO shopDTO = ShopDataDTO.builder()
            .shopId(0) // ID will be auto-generated by database
            .ownerUuid(barterSign.getOwner())
            .shopName("Barter Shop")
            .shopType(ShopType.BARTER)
            .signLocation(signLoc)
            .chestLocation(containerLoc)
            .isActive(true)
            .metadata(Collections.emptyMap())
            .build();

        // Save asynchronously - don't block the sign creation event
        plugin.getShopRepository().save(shopDTO)
            .thenAccept(savedDTO -> {
                logger.debug("Barter sign persisted to database with shop ID: " + savedDTO.shopId());
            })
            .exceptionally(ex -> {
                logger.error("Failed to persist barter sign: " + ex.getMessage());
                return null;
            });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (event.getLine(0).equalsIgnoreCase("[barter]")) {
            try {
                Player player = event.getPlayer();
                Sign sign = (Sign) event.getBlock().getState();

                // Check if sign is already registered
                BarterSign existingSign = barterSigns.get(sign.getBlock().getLocation());
                if (existingSign != null) {
                    logger.debug("Attempted creation of already existing barter sign by " + player.getName());
                    event.setCancelled(true);

                    // If owner, treat as interaction
                    if (existingSign.getOwner().equals(player.getUniqueId())) {
                        logger.debug("Owner attempted recreation - treating as interaction");
                        handleSignInteraction(player, sign, existingSign);
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
                            player.sendMessage(ChatColor.GREEN + "Shop created!");
                        } else {
                            player.sendMessage(ChatColor.RED + "No container found! Place sign on a chest.");
                            event.setCancelled(true);
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "Invalid location! Place sign on or above a chest.");
                        logger.warning("Invalid shop location attempt by " + player.getName());
                        event.setCancelled(true);
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "You don't have permission to create shops.");
                    logger.warning("Permission denied for shop creation: " + player.getName());
                    event.setCancelled(true);
                }
            } catch (Exception e) {
                logger.error("Error creating barter shop: " + e.getMessage(), e);
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

    // ========== Sign Protection Handlers ==========

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        BarterSign barterSign = barterSigns.get(loc);
        if (barterSign == null) return;

        Player player = event.getPlayer();

        // Only owner or admin can break
        if (!barterSign.getOwner().equals(player.getUniqueId())
                && !player.hasPermission("bartershops.admin")) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot break this shop sign.");
            return;
        }

        // Must be in DELETE mode to break (or admin)
        if (barterSign.getMode() != SignMode.DELETE
                && !player.hasPermission("bartershops.admin")) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.YELLOW + "Right-click to enter delete mode first.");
            return;
        }

        // Authorized break - cleanup
        removeBarterSign(loc);
        player.sendMessage(ChatColor.GREEN + "Shop removed.");
        logger.info("Shop sign removed by " + player.getName() + " at " + loc);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> barterSigns.containsKey(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> barterSigns.containsKey(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (barterSigns.containsKey(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (barterSigns.containsKey(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (barterSigns.containsKey(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    // ========== Sign Interaction Handlers ==========

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignClick(PlayerInteractEvent event) {
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || !(clickedBlock.getState() instanceof Sign)) {
            return;
        }

        Player player = event.getPlayer();
        Sign sign = (Sign) clickedBlock.getState();
        BarterSign barterSign = barterSigns.get(clickedBlock.getLocation());

        // Only intercept registered barter signs
        if (barterSign == null) {
            return;
        }

        try {
            event.setCancelled(true);

            if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
                signInteraction.handleLeftClick(player, sign, barterSign, event);
                return;
            }

            signInteraction.handleRightClick(player, sign, barterSign);
        } catch (Exception e) {
            logger.error("Error processing sign interaction: " + e.getMessage(), e);
        }
    }

    // ========== Utility Methods ==========

    private boolean qualifySign(Sign sign) {
        logger.debug("Qualifying sign at location: " + sign.getLocation());
        Block signBlock = sign.getBlock();

        // Wall signs: check block behind (attached face)
        if (sign.getBlockData() instanceof org.bukkit.block.data.type.WallSign wallSign) {
            BlockFace attachedFace = wallSign.getFacing().getOppositeFace();
            Block behindBlock = signBlock.getRelative(attachedFace);
            if (behindBlock.getState() instanceof Container) {
                return true;
            }
        }

        // Standing signs (or wall sign with no container behind): check block below
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
        }
    }

    public void removeBarterSign(Location loc) {
        barterSigns.remove(loc);
    }

    /**
     * Clears all in-memory barter signs.
     * FIX Bug #8df42120: Used by /shop reload to refresh sign cache.
     */
    public void clearSigns() {
        int count = barterSigns.size();
        barterSigns.clear();
        logger.debug("Cleared " + count + " barter signs from memory");
    }

    public void cleanup() {
        logger.debug("Cleaning up SignManager - clearing " + barterSigns.size() + " registered signs");
        signInteraction.cleanup();
        HandlerList.unregisterAll(this);
        barterSigns.clear();
        logger.info("SignManager cleanup completed");
    }

    public Map<Location, BarterSign> getBarterSigns() {
        return Collections.unmodifiableMap(barterSigns);
    }

    public SignInteraction getSignInteraction() {
        return signInteraction;
    }
}
