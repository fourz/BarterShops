package org.fourz.BarterShops.sign;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.shop.ShopMode;
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

        // Start periodic chest validation task
        startChestValidationTask();

        logger.debug("SignManager initialized");
    }

    /**
     * Starts a periodic task to validate chest contents for all barter shops.
     * Runs every 5 ticks (250ms) to:
     * 1. Auto-detect shop type from first item placed
     * 2. Remove invalid items that don't match shop type
     */
    private void startChestValidationTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (BarterSign barterSign : barterSigns.values()) {
                // Only process shops in SETUP mode
                if (barterSign.getMode() != ShopMode.SETUP) {
                    continue;
                }

                Container container = barterSign.getShopContainer();
                if (container == null) {
                    container = barterSign.getContainer();
                }
                if (container == null) {
                    continue;
                }

                Location signLoc = barterSign.getSignLocation();
                Location containerLoc = container.getLocation();

                // Try to auto-detect type if not yet detected
                if (!barterSign.isTypeDetected()) {
                    if (barterSign.detectAndSetTypeFromChest()) {
                        logger.debug("Auto-detected shop type from chest contents at " + containerLoc);

                        // Notify owner
                        for (org.bukkit.entity.Player player : signLoc.getWorld().getPlayers()) {
                            if (player.getLocation().distance(signLoc) <= 15 &&
                                player.getUniqueId().equals(barterSign.getOwner())) {
                                String typeText = barterSign.isStackable() ? "stackable" : "unstackable";
                                player.sendMessage(ChatColor.GREEN + "+ Shop type auto-set to " + typeText +
                                                 ChatColor.GRAY + " (locked until deletion)");
                            }
                        }

                        // Update sign display
                        Block signBlock = signLoc.getBlock();
                        if (signBlock.getState() instanceof Sign sign) {
                            SignDisplay.updateSign(sign, barterSign);
                        }
                    }
                } else {
                    // Type already detected - validate and clean invalid items
                    validateInventoryContents(container, barterSign);
                }
            }
        }, 0L, 5L); // Run every 5 ticks (250ms)
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
                    .mode(ShopMode.BOARD)
                    .type(SignType.BARTER)
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
            .mode(ShopMode.SETUP)
            .type(SignType.BARTER)
            .signSideDisplayFront(sign.getSide(org.bukkit.block.sign.Side.FRONT))
            .signSideDisplayBack(sign.getSide(org.bukkit.block.sign.Side.BACK))
            .build();

        barterSigns.put(signLocation, barterSign);

        logger.info("Created barter sign (ID: " + id + ") for " + player.getName());

        // Display the sign in SETUP mode so it's not empty
        // Schedule on next tick to ensure block state is ready
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            org.bukkit.block.Block block = signLocation.getBlock();
            if (block.getState() instanceof Sign updatedSign) {
                SignDisplay.updateSign(updatedSign, barterSign);
                logger.debug("Sign display updated for newly created shop");
            }
        });

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
        if (barterSign.getMode() != ShopMode.DELETE
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

    // ========== Inventory Auto-Detection via Periodic Validation ==========
    // Type detection and validation now handled by startChestValidationTask() which runs every 5 ticks

    /**
     * Validates inventory contents based on shop type.
     * Stackable shops: only allow items matching the locked item type
     * Unstackable shops: only allow unstackable items (any of them)
     */
    private void validateInventoryContents(Container container, BarterSign barterSign) {
        // Only validate if type has been detected
        if (!barterSign.isTypeDetected()) {
            return;
        }

        Location signLoc = barterSign.getSignLocation();
        java.util.List<ItemStack> invalidItems = new java.util.ArrayList<>();

        if (barterSign.isStackable()) {
            // STACKABLE SHOP: only allow items matching locked type
            invalidItems = collectInvalidStackableItems(container, barterSign);
        } else {
            // UNSTACKABLE SHOP: only allow unstackable items
            invalidItems = collectInvalidUnstackableItems(container, barterSign);
        }

        // Process and return invalid items
        if (!invalidItems.isEmpty()) {
            removeAndReturnItems(container, invalidItems, signLoc, barterSign);
        }
    }

    /**
     * Collects invalid items from stackable shop
     */
    private java.util.List<ItemStack> collectInvalidStackableItems(Container container, BarterSign barterSign) {
        java.util.List<ItemStack> invalid = new java.util.ArrayList<>();
        Material lockedType = barterSign.getLockedItemType();

        for (ItemStack item : container.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                boolean isInvalid = false;

                // Check if unstackable (should not be in stackable shop)
                if (!BarterSign.isItemStackable(item)) {
                    isInvalid = true;
                }
                // Check if different item type
                else if (lockedType != null && !item.getType().equals(lockedType)) {
                    isInvalid = true;
                }

                if (isInvalid) {
                    invalid.add(item.clone());
                }
            }
        }

        return invalid;
    }

    /**
     * Collects invalid items from unstackable shop
     */
    private java.util.List<ItemStack> collectInvalidUnstackableItems(Container container, BarterSign barterSign) {
        java.util.List<ItemStack> invalid = new java.util.ArrayList<>();

        for (ItemStack item : container.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                // Check if stackable (should not be in unstackable shop)
                if (BarterSign.isItemStackable(item)) {
                    invalid.add(item.clone());
                }
            }
        }

        return invalid;
    }

    /**
     * Removes invalid items from container and returns them to nearby players or drops them
     * Uses proper inventory slot updates via setItem() to ensure items are actually removed
     */
    private void removeAndReturnItems(Container container, java.util.List<ItemStack> invalidItems,
                                      Location signLoc, BarterSign barterSign) {
        Location chestLoc = container.getLocation();
        java.util.List<ItemStack> itemsSuccessfullyRemoved = new java.util.ArrayList<>();

        // First pass: Remove invalid items from container using proper slot updates
        for (ItemStack invalid : invalidItems) {
            int amountToRemove = invalid.getAmount();

            // Iterate through inventory slots to properly remove items
            for (int slot = 0; slot < container.getInventory().getSize(); slot++) {
                ItemStack slotItem = container.getInventory().getItem(slot);

                if (slotItem != null && slotItem.isSimilar(invalid) && amountToRemove > 0) {
                    int toRemove = Math.min(amountToRemove, slotItem.getAmount());

                    // Create a copy of the removed item for tracking
                    ItemStack removedStack = slotItem.clone();
                    removedStack.setAmount(toRemove);
                    itemsSuccessfullyRemoved.add(removedStack);

                    // Update the slot (reduce amount or set to null)
                    slotItem.setAmount(slotItem.getAmount() - toRemove);
                    if (slotItem.getAmount() <= 0) {
                        container.getInventory().setItem(slot, null);
                    } else {
                        container.getInventory().setItem(slot, slotItem);
                    }

                    amountToRemove -= toRemove;
                    if (amountToRemove <= 0) {
                        break;
                    }
                }
            }
        }

        // Second pass: Return successfully removed items to player or drop them
        for (ItemStack removed : itemsSuccessfullyRemoved) {
            if (removed.getAmount() <= 0) {
                continue;
            }

            String itemName = removed.getType().name();
            String reason = barterSign.isStackable() ? "wrong item type" : "stackable items not allowed";

            logger.debug("Returning invalid item " + removed.getAmount() + "x " + itemName + " at " + chestLoc);

            boolean returned = false;

            // Try to give to owner if nearby
            for (org.bukkit.entity.Player player : signLoc.getWorld().getPlayers()) {
                if (player.getUniqueId().equals(barterSign.getOwner()) &&
                    player.getLocation().distance(signLoc) <= 30) {
                    // Try to add to player inventory
                    java.util.HashMap<Integer, ItemStack> couldNotFit = player.getInventory().addItem(removed.clone());

                    if (couldNotFit.isEmpty()) {
                        // Item successfully added to inventory
                        notifyOwner(signLoc, barterSign, "Returned " + removed.getAmount() + "x " + itemName +
                                   " (" + reason + ")");
                        returned = true;
                        break;
                    }
                }
            }

            // If owner not available or inventory full, drop at chest location
            if (!returned) {
                chestLoc.getWorld().dropItem(chestLoc.add(0.5, 1.0, 0.5), removed.clone());
                logger.debug("Dropped invalid item " + removed.getAmount() + "x " + itemName + " at chest");
            }
        }
    }

    /**
     * Notifies shop owner about validation issue
     */
    private void notifyOwner(Location signLoc, BarterSign barterSign, String message) {
        for (org.bukkit.entity.Player player : signLoc.getWorld().getPlayers()) {
            if (player.getLocation().distance(signLoc) <= 15 &&
                player.getUniqueId().equals(barterSign.getOwner())) {
                player.sendMessage(ChatColor.YELLOW + "⚠ " + message);
                break; // Only notify once per validation
            }
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
