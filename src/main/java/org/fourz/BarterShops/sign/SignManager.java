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
import org.fourz.BarterShops.container.ShopContainer;
import org.fourz.BarterShops.container.factory.ShopContainerFactory;
import org.fourz.BarterShops.shop.ShopMode;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.dto.ShopDataDTO.ShopType;
import org.fourz.BarterShops.service.ShopConfigManager;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.UUID;

public class SignManager implements Listener {
    private static final String CLASS_NAME = "SignManager";
    private final BarterShops plugin;
    private final LogManager logger;
    private final Map<Location, BarterSign> barterSigns = new ConcurrentHashMap<>();
    private final SignInteraction signInteraction;
    private final ShopConfigManager configManager;

    public SignManager(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, CLASS_NAME);
        this.signInteraction = new SignInteraction(plugin);
        this.configManager = new ShopConfigManager(plugin, plugin.getShopRepository());
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // INTEGRATION POINT 5: Timer-based validation removed - replaced by event-driven InventoryValidationListener
        // Previously: startChestValidationTask() ran every 250ms to validate in SETUP mode only
        // Now: InventoryValidationListener validates in ALL modes via inventory events (real-time, lower overhead)

        logger.debug("SignManager initialized");
    }

    /**
     * @deprecated REPLACED BY EVENT-DRIVEN VALIDATION (Phase 2 Integration)
     * Replaced by InventoryValidationListener which provides real-time validation in all modes.
     * This method is no longer called but kept for documentation.
     *
     * Previously: Periodic task to validate chest contents for all barter shops.
     * Ran every 5 ticks (250ms) to:
     * 1. Auto-detect shop type from first item placed (in SETUP mode only)
     * 2. Remove invalid items that don't match shop type (in SETUP mode only)
     *
     * New approach: InventoryValidationListener validates in ALL modes via inventory events.
     */
    @Deprecated
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

                        // CRITICAL FIX: Persist the type detection to database immediately
                        // so that typeDetected and lockedItemType survive server restarts and reloads
                        if (barterSign.getShopId() > 0) {
                            configManager.saveSignConfiguration(barterSign, barterSign.getShopId())
                                    .exceptionally(ex -> {
                                        logger.error("Failed to persist auto-detected shop type: " + ex.getMessage());
                                        return null;
                                    });
                        }

                        // INTEGRATION POINT 3: Update ShopContainer validation rules after type detection
                        UUID shopUuid = UUID.fromString(barterSign.getId());
                        ShopContainer shopContainer = createShopContainerFromBarterSign(barterSign, container, shopUuid);
                        plugin.getContainerManager().getValidationListener().registerContainer(shopContainer);
                        barterSign.setShopContainerWrapper(shopContainer); // CRITICAL: Set on BarterSign for TradeValidator access

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

                // Set shop ID for database reference
                barterSign.setShopId(shop.shopId());

                // Load persisted configuration
                configManager.loadSignConfiguration(barterSign, shop);

                // INTEGRATION POINT 2: Register ShopContainer from persisted configuration
                if (container != null) {
                    // Create stable UUID from numeric shop ID (database compatibility)
                    UUID shopUuid = UUID.nameUUIDFromBytes(("bartershop:" + shop.shopId()).getBytes());
                    ShopContainer shopContainer = createShopContainerFromBarterSign(barterSign, container, shopUuid);
                    plugin.getContainerManager().getValidationListener().registerContainer(shopContainer);
                    barterSign.setShopContainerWrapper(shopContainer); // CRITICAL: Set on BarterSign for TradeValidator access
                }

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

        // INTEGRATION POINT 1: Register ShopContainer for real-time validation
        UUID shopUuid = UUID.fromString(id);
        ShopContainer shopContainer = ShopContainerFactory.createContainer(container, shopUuid);
        plugin.getContainerManager().getValidationListener().registerContainer(shopContainer);
        barterSign.setShopContainerWrapper(shopContainer); // CRITICAL: Set on BarterSign for TradeValidator access

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
        BarterSign barterSign = barterSigns.remove(loc);
        if (barterSign == null) return;

        // INTEGRATION POINT 4: Unregister ShopContainer from validation listener
        UUID shopUuid = UUID.fromString(barterSign.getId());
        plugin.getContainerManager().getValidationListener().unregisterContainer(shopUuid);

        int shopId = barterSign.getShopId();
        if (shopId > 0) {
            // Delete from database asynchronously
            plugin.getShopRepository().deleteById(shopId).thenAccept(success -> {
                if (success) {
                    logger.debug("Deleted shop " + shopId + " from database");
                } else {
                    logger.warning("Failed to delete shop " + shopId + " from database");
                }
            }).exceptionally(e -> {
                logger.error("Error deleting shop " + shopId + " from database: " + e.getMessage(), e);
                return null;
            });
        }

        // Clean up PDC data from sign block by removing sign block entirely
        // (Bukkit will handle PDC cleanup when block is broken)
        Block signBlock = loc.getBlock();
        if (signBlock.getType() != Material.AIR && signBlock.getBlockData() instanceof Sign) {
            signBlock.setType(Material.AIR);
        }
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

    /**
     * Saves BarterSign configuration to database.
     * Called after owner makes configuration changes (offering, price, payments).
     * Runs asynchronously to avoid blocking the game thread.
     *
     * @param barterSign The BarterSign with updated configuration
     */
    public void saveSignConfiguration(BarterSign barterSign) {
        if (barterSign.getShopId() <= 0) {
            logger.debug("Cannot save config for sign without valid shop ID");
            return;
        }

        configManager.saveSignConfiguration(barterSign, barterSign.getShopId());
    }

    /**
     * Reload a shop from database and update in-memory cache.
     * Used after ownership changes to sync immutable BarterSign.owner field.
     * Handles ShopContainer wrapper lifecycle (unregister old, register new).
     *
     * @param shopId The shop ID to reload
     * @return CompletableFuture with reloaded BarterSign, or null if not found/error
     */
    public java.util.concurrent.CompletableFuture<BarterSign> reloadShopFromDatabase(int shopId) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                // Step 1: Load fresh DTO from database
                ShopDataDTO shopData = plugin.getShopRepository().findById(shopId)
                    .get(5, java.util.concurrent.TimeUnit.SECONDS)
                    .orElse(null);

                if (shopData == null) {
                    logger.warning("Cannot reload shop " + shopId + " - not found in database");
                    return null;
                }

                // Step 2: Find old BarterSign by location
                Location signLoc = shopData.getSignLocation();
                if (signLoc == null || signLoc.getWorld() == null) {
                    logger.warning("Cannot reload shop " + shopId + " - invalid sign location");
                    return null;
                }

                BarterSign oldSign = barterSigns.get(signLoc);
                if (oldSign == null) {
                    logger.warning("Cannot reload shop " + shopId + " - sign not in cache at " + signLoc);
                    return null;
                }

                // Step 3: Build new BarterSign with updated owner
                BarterSign.Builder builder = new BarterSign.Builder()
                    .id(oldSign.getId())
                    .owner(shopData.ownerUuid())  // NEW OWNER from database
                    .signLocation(signLoc)
                    .container(oldSign.getContainer())
                    .mode(oldSign.getMode())  // Preserve current mode
                    .type(oldSign.getType())
                    .signSideDisplayFront(oldSign.getSignSideDisplayFront())
                    .signSideDisplayBack(oldSign.getSignSideDisplayBack());

                BarterSign newSign = builder.build();
                newSign.setShopId(shopId);

                // Copy configuration from old sign
                newSign.setStackable(oldSign.isStackable());
                if (oldSign.getItemOffering() != null) {
                    newSign.configureStackableShop(oldSign.getItemOffering(), oldSign.getItemOffering().getAmount());
                }
                if (oldSign.getPriceItem() != null) {
                    newSign.configurePrice(oldSign.getPriceItem(), oldSign.getPriceAmount());
                }
                for (ItemStack payment : oldSign.getAcceptedPayments()) {
                    newSign.addPaymentOption(payment, payment.getAmount());
                }
                newSign.setTypeDetected(oldSign.isTypeDetected());

                // Step 4: CRITICAL - Unregister old ShopContainer wrapper
                if (oldSign.getShopContainerWrapper() != null) {
                    UUID oldShopUuid = java.util.UUID.fromString(oldSign.getId());
                    plugin.getContainerManager().getValidationListener()
                        .unregisterContainer(oldShopUuid);
                }

                // Step 5: Rebuild ShopContainer wrapper with new BarterSign
                Container container = oldSign.getContainer();
                if (container != null) {
                    UUID shopUuid = java.util.UUID.fromString(newSign.getId());
                    ShopContainer newContainer = createShopContainerFromBarterSign(newSign, container, shopUuid);
                    newSign.setShopContainerWrapper(newContainer);

                    // Register new wrapper with validation listener
                    plugin.getContainerManager().getValidationListener()
                        .registerContainer(newContainer);
                }

                // Step 6: Update cache
                barterSigns.put(signLoc, newSign);

                logger.info("Reloaded shop " + shopId + " with new owner: " + shopData.ownerUuid());
                return newSign;

            } catch (Exception e) {
                logger.error("Failed to reload shop " + shopId + " from database", e);
                return null;
            }
        });
    }

    /**
     * Creates and registers a ShopContainer wrapper for a BarterSign.
     * Applies appropriate validation rules based on shop type and configuration.
     * Used during shop creation, database hydration, and type detection.
     *
     * UNIFIED: Sets bidirectional reference between BarterSign and ShopContainer
     * so validation can access shop configuration (owner, offerings, payments).
     *
     * @param barterSign The BarterSign to create a container for
     * @param container The Bukkit Container block
     * @param shopUuid The UUID of the shop
     * @return ShopContainer with appropriate validation rules
     */
    private ShopContainer createShopContainerFromBarterSign(BarterSign barterSign, Container container, UUID shopUuid) {
        ShopContainer shopContainer;

        if (barterSign.isTypeDetected()) {
            if (barterSign.isStackable()) {
                // Stackable: use dynamic multi-type validation (offering + payments)
                var allowedTypes = barterSign.getAllowedChestTypes();
                if (!allowedTypes.isEmpty()) {
                    shopContainer = ShopContainerFactory.createStackableMultiTypeLocked(
                        container, shopUuid, allowedTypes
                    );
                } else {
                    shopContainer = ShopContainerFactory.createContainer(container, shopUuid);
                }
            } else {
                // Unstackable only: create unstackable container
                shopContainer = ShopContainerFactory.createUnstackableOnly(container, shopUuid);
            }
        } else {
            // No type detected or no special rules: create base container
            shopContainer = ShopContainerFactory.createContainer(container, shopUuid);
        }

        // UNIFIED: Set bidirectional reference for user-aware validation context
        shopContainer.setBarterSign(barterSign);
        return shopContainer;
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

    /**
     * Finds a BarterSign by its associated shop container location.
     * Used to check if a container being broken is part of a barter shop.
     */
    public BarterSign findSignByContainerLocation(Location containerLocation) {
        if (containerLocation == null) return null;

        for (BarterSign sign : barterSigns.values()) {
            Container shopContainer = sign.getShopContainer();
            if (shopContainer == null) {
                shopContainer = sign.getContainer();
            }

            if (shopContainer != null && shopContainer.getLocation().equals(containerLocation)) {
                return sign;
            }
        }
        return null;
    }
}
