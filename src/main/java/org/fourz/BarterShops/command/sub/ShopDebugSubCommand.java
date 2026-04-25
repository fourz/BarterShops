package org.fourz.BarterShops.command.sub;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SeedSubCommand;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.IConnectionProvider;
import org.fourz.BarterShops.service.IShopService;
import org.fourz.BarterShops.service.IShopService.ShopUpdateRequest;
import org.fourz.BarterShops.service.impl.ShopServiceImpl;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.BarterShops.util.ChunkLoadUtility;
import org.fourz.rvnkcore.data.FallbackTracker;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Debug command for BarterShops.
 * Provides diagnostic information and access to seed commands.
 *
 * Usage:
 *   /shop debug - Show debug info
 *   /shop debug loglevel [level] - View/change log level
 *   /shop debug seed <action> - Seed test data
 *   /shop debug diagnostics - System diagnostics
 *   /shop debug changeowner <shopId> <playerName> - Transfer shop ownership
 *   /shop debug stock <shopId> [amount] - Inspect or restock shop chest
 *   /shop debug validate [shopId] - Validate shop sign/chest integrity
 *   /shop debug rebind <shopId> <world> <x> <y> <z> - Rebind chest location
 *   /shop debug create <name> <world> <x> <y> <z> [owner] - Create shop programmatically
 */
public class ShopDebugSubCommand implements SubCommand {

    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "loglevel", "seed", "diagnostics", "changeowner",
            "stock", "validate", "rebind", "create");
    private static final List<String> LOG_LEVELS = Arrays.asList("DEBUG", "INFO", "WARN", "OFF");

    /** Container material types recognised as valid shop chests. */
    private static final Set<Material> CONTAINER_TYPES = EnumSet.of(
            Material.CHEST,
            Material.TRAPPED_CHEST,
            Material.BARREL,
            Material.SHULKER_BOX,
            Material.WHITE_SHULKER_BOX,
            Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX,
            Material.LIGHT_BLUE_SHULKER_BOX,
            Material.YELLOW_SHULKER_BOX,
            Material.LIME_SHULKER_BOX,
            Material.PINK_SHULKER_BOX,
            Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX,
            Material.CYAN_SHULKER_BOX,
            Material.PURPLE_SHULKER_BOX,
            Material.BLUE_SHULKER_BOX,
            Material.BROWN_SHULKER_BOX,
            Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX,
            Material.BLACK_SHULKER_BOX);

    /** Console owner UUID used when no player name is specified for {@code create}. */
    private static final UUID CONSOLE_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final BarterShops plugin;
    private final LogManager logger;
    private final SeedSubCommand seedCommand;
    private final IShopService shopService;

    public ShopDebugSubCommand(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ShopDebugSubCommand");
        this.seedCommand = new SeedSubCommand(plugin);
        this.shopService = new ShopServiceImpl(plugin, plugin.getShopRepository(), plugin.getServiceRegistry());
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        // Check for subcommands
        if (args.length > 0) {
            String subCommand = args[0].toLowerCase();
            String[] subArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];

            switch (subCommand) {
                case "loglevel":
                    return handleLogLevel(sender, subArgs);
                case "seed":
                    return seedCommand.execute(sender, subArgs);
                case "diagnostics":
                    return handleDiagnostics(sender);
                case "changeowner":
                    return handleChangeOwner(sender, subArgs);
                case "stock":
                    return handleStock(sender, subArgs);
                case "validate":
                    return handleValidate(sender, subArgs);
                case "rebind":
                    return handleRebind(sender, subArgs);
                case "create":
                    return handleCreate(sender, subArgs);
                default:
                    sender.sendMessage(ChatColor.RED + "Unknown debug subcommand: " + subCommand);
                    showUsage(sender);
                    return true;
            }
        }

        // Default: show debug info
        showDebugInfo(sender);
        return true;
    }

    // =========================================================
    // Existing handlers
    // =========================================================

    /**
     * Shows general debug information.
     */
    private void showDebugInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "=== BarterShops Debug Info ===");

        // Database info
        IConnectionProvider connProvider = plugin.getConnectionProvider();
        if (connProvider != null) {
            sender.sendMessage(ChatColor.GOLD + "Database Type: " +
                ChatColor.WHITE + connProvider.getDatabaseType());
        } else {
            sender.sendMessage(ChatColor.GOLD + "Database Type: " +
                ChatColor.RED + "Not available");
        }

        // Fallback status
        FallbackTracker fallback = plugin.getFallbackTracker();
        if (fallback != null) {
            sender.sendMessage(ChatColor.GOLD + "Fallback Mode: " +
                (fallback.isInFallbackMode() ? ChatColor.YELLOW + "Active" : ChatColor.GREEN + "No"));
            if (fallback.isInFallbackMode()) {
                sender.sendMessage(ChatColor.GOLD + "Fallback Reason: " +
                    ChatColor.GRAY + fallback.getFallbackReason());
            }
        }

        // RVNKCore integration
        sender.sendMessage(ChatColor.GOLD + "RVNKCore Integration: " +
            (plugin.isRVNKCoreAvailable() ? ChatColor.GREEN + "Enabled" : ChatColor.GRAY + "Standalone"));

        // Uptime
        long uptimeMs = System.currentTimeMillis() - plugin.getStartTime();
        long uptimeSec = uptimeMs / 1000;
        long uptimeMin = uptimeSec / 60;
        long uptimeHrs = uptimeMin / 60;
        sender.sendMessage(ChatColor.GOLD + "Uptime: " +
            ChatColor.WHITE + String.format("%dh %dm %ds", uptimeHrs, uptimeMin % 60, uptimeSec % 60));

        // Log level
        String currentLevel = plugin.getConfig().getString("general.logLevel", "INFO");
        sender.sendMessage(ChatColor.GOLD + "Log Level: " + ChatColor.WHITE + currentLevel);

        // Available subcommands
        sender.sendMessage(ChatColor.GRAY + "Subcommands: /shop debug loglevel|seed|diagnostics|changeowner|stock|validate|rebind|create");
    }

    /**
     * Handle the loglevel subcommand.
     * Usage: /shop debug loglevel [DEBUG|INFO|WARN|OFF]
     */
    private boolean handleLogLevel(CommandSender sender, String[] args) {
        if (args.length == 0) {
            // Show current log level from config
            String currentLevel = plugin.getConfig().getString("general.logLevel", "INFO");
            sender.sendMessage(ChatColor.GOLD + "Current log level: " +
                ChatColor.WHITE + currentLevel);
            sender.sendMessage(ChatColor.GRAY + "Usage: /shop debug loglevel <DEBUG|INFO|WARN|OFF>");
            return true;
        }

        String levelStr = args[0].toUpperCase();
        Level level = LogManager.parseLevel(levelStr);

        // Set log level for all BarterShops loggers
        LogManager.setPluginLogLevel(plugin, level);

        // Update config for persistence
        plugin.getConfig().set("general.logLevel", levelStr);
        plugin.saveConfig();

        sender.sendMessage(ChatColor.GREEN + "Log level set to: " + ChatColor.WHITE + levelStr);
        sender.sendMessage(ChatColor.GRAY + "(Saved to config.yml)");

        return true;
    }

    /**
     * Handle the diagnostics subcommand.
     * Shows detailed system diagnostics.
     */
    private boolean handleDiagnostics(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "=== BarterShops Diagnostics ===");

        // Memory usage
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        sender.sendMessage(ChatColor.GOLD + "Memory: " +
            ChatColor.WHITE + usedMemory + "MB / " + maxMemory + "MB");

        // Managers status
        sender.sendMessage(ChatColor.GOLD + "--- Manager Status ---");
        sender.sendMessage(ChatColor.GRAY + "ShopManager: " +
            (plugin.getShopManager() != null ? ChatColor.GREEN + "Active" : ChatColor.RED + "Null"));
        sender.sendMessage(ChatColor.GRAY + "SignManager: " +
            (plugin.getSignManager() != null ? ChatColor.GREEN + "Active" : ChatColor.RED + "Null"));
        sender.sendMessage(ChatColor.GRAY + "ContainerManager: " +
            (plugin.getContainerManager() != null ? ChatColor.GREEN + "Active" : ChatColor.RED + "Null"));
        sender.sendMessage(ChatColor.GRAY + "TradeEngine: " +
            (plugin.getTradeEngine() != null ? ChatColor.GREEN + "Active" : ChatColor.RED + "Null"));
        sender.sendMessage(ChatColor.GRAY + "TemplateManager: " +
            (plugin.getTemplateManager() != null ? ChatColor.GREEN + "Active" : ChatColor.RED + "Null"));
        sender.sendMessage(ChatColor.GRAY + "ProtectionManager: " +
            (plugin.getProtectionManager() != null ? ChatColor.GREEN + "Active" : ChatColor.RED + "Null"));

        // Services status
        sender.sendMessage(ChatColor.GOLD + "--- Service Status ---");
        sender.sendMessage(ChatColor.GRAY + "RatingService: " +
            (plugin.getRatingService() != null ? ChatColor.GREEN + "Registered" : ChatColor.GRAY + "Not available"));
        sender.sendMessage(ChatColor.GRAY + "StatsService: " +
            (plugin.getStatsService() != null ? ChatColor.GREEN + "Registered" : ChatColor.GRAY + "Not available"));

        // Database layer
        sender.sendMessage(ChatColor.GOLD + "--- Database Layer ---");
        sender.sendMessage(ChatColor.GRAY + "ConnectionProvider: " +
            (plugin.getConnectionProvider() != null ? ChatColor.GREEN + "Active" : ChatColor.RED + "Null"));
        sender.sendMessage(ChatColor.GRAY + "ShopRepository: " +
            (plugin.getShopRepository() != null ? ChatColor.GREEN + "Active" : ChatColor.RED + "Null"));

        FallbackTracker fallback = plugin.getFallbackTracker();
        if (fallback != null) {
            sender.sendMessage(ChatColor.GRAY + "FallbackTracker: " + ChatColor.GREEN + "Active");
            sender.sendMessage(ChatColor.GRAY + "  Failure Count: " + ChatColor.WHITE + fallback.getFailureCount() + "/" + fallback.getMaxFailures());
        }

        return true;
    }

    /**
     * Handle the changeowner subcommand.
     * Usage: /shop debug changeowner <shopId> <playerName>
     * Delegates to IShopOwnershipService for real-time ownership transfer.
     */
    private boolean handleChangeOwner(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /shop debug changeowner <shopId> <playerName>");
            return true;
        }

        int shopId;
        try {
            shopId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid shop ID: " + args[0]);
            return true;
        }

        String playerName = args[1];

        // Resolve player name to UUID
        Player targetPlayer = Bukkit.getPlayer(playerName);
        UUID newOwnerUUID;

        if (targetPlayer != null) {
            // Player is online
            newOwnerUUID = targetPlayer.getUniqueId();
        } else {
            // Try offline player lookup — getUniqueId() is NEVER null (generates offline UUID for unknown names).
            // Must check hasPlayedBefore() to reject completely unknown players.
            var offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            if (!offlinePlayer.hasPlayedBefore()) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + playerName);
                sender.sendMessage(ChatColor.GRAY + "(Player has never joined this server)");
                return true;
            }
            newOwnerUUID = offlinePlayer.getUniqueId();
        }

        // Delegate to ownership service
        plugin.getOwnershipService().transferOwnership(shopId, newOwnerUUID, sender)
            .thenAccept(result -> {
                if (result.success()) {
                    sender.sendMessage(ChatColor.GREEN + "✓ " + result.message());
                    sender.sendMessage(ChatColor.GRAY + "  Shop: " + ChatColor.WHITE + "#" + shopId);
                    sender.sendMessage(ChatColor.GRAY + "  Old owner: " + ChatColor.WHITE + result.oldOwner());
                    sender.sendMessage(ChatColor.GRAY + "  New owner: " + ChatColor.WHITE + result.newOwner());
                    sender.sendMessage(ChatColor.GRAY + "  Sessions invalidated: " + ChatColor.YELLOW +
                        result.sessionsInvalidated());
                } else {
                    sender.sendMessage(ChatColor.RED + "✗ " + result.message());
                }
            })
            .exceptionally(ex -> {
                sender.sendMessage(ChatColor.RED + "✗ Error: " + ex.getMessage());
                logger.error("Error during ownership transfer", ex);
                return null;
            });

        sender.sendMessage(ChatColor.YELLOW + "* Changing shop owner...");
        return true;
    }

    // =========================================================
    // New handlers
    // =========================================================

    /**
     * Handle the stock subcommand.
     * Usage: /shop debug stock <shopId> [amount]
     *
     * <p>With no amount (or amount 0): reports inventory contents summary.</p>
     * <p>With amount > 0: attempts to restock the chest with the shop's configured offering item.</p>
     */
    private boolean handleStock(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "> Usage: /shop debug stock <shopId> [amount]");
            return true;
        }

        String shopIdStr = args[0];
        int restockAmount = 0;
        if (args.length >= 2) {
            try {
                restockAmount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "x Invalid amount: " + args[1]);
                return true;
            }
        }

        final int finalAmount = restockAmount;

        shopService.getShopById(shopIdStr).thenAccept(optShop -> {
            if (optShop.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "x Shop not found: #" + shopIdStr);
                return;
            }

            ShopDataDTO shop = optShop.get();

            if (shop.chestLocationWorld() == null) {
                sender.sendMessage(ChatColor.RED + "x Shop #" + shopIdStr + " has no chest bound.");
                return;
            }

            World chestWorld = Bukkit.getWorld(shop.chestLocationWorld());
            if (chestWorld == null) {
                sender.sendMessage(ChatColor.RED + "x Chest world '" + shop.chestLocationWorld() + "' is not loaded.");
                return;
            }

            Location chestLoc = new Location(chestWorld,
                    shop.chestLocationX(), shop.chestLocationY(), shop.chestLocationZ());

            ChunkLoadUtility.loadChunkForBlock(chestLoc).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Block block = chestLoc.getBlock();

                    if (!CONTAINER_TYPES.contains(block.getType())) {
                        sender.sendMessage(ChatColor.RED + "x Block at chest location is not a container: "
                                + block.getType().name());
                        return;
                    }

                    BlockState state = block.getState();
                    if (!(state instanceof InventoryHolder)) {
                        sender.sendMessage(ChatColor.RED + "x Block state is not an InventoryHolder.");
                        return;
                    }

                    Inventory inv = ((InventoryHolder) state).getInventory();

                    if (finalAmount <= 0) {
                        // Report only — do not modify
                        reportInventoryContents(sender, shop, inv);
                    } else {
                        // Attempt restock
                        ItemStack offering = shop.getConfiguredOffering();
                        if (offering == null) {
                            sender.sendMessage(ChatColor.YELLOW + "! Shop #" + shopIdStr
                                    + " has no configured offering — reporting contents only.");
                            reportInventoryContents(sender, shop, inv);
                            return;
                        }

                        int placed = fillInventory(inv, offering, finalAmount);
                        sender.sendMessage(ChatColor.GREEN + "+ Restocked shop #" + shopIdStr
                                + " with " + placed + "x " + offering.getType().name()
                                + " (requested " + finalAmount + ").");
                        logger.info("Restocked shop #" + shopIdStr + " with " + placed
                                + "x " + offering.getType().name());
                    }
                })
            ).exceptionally(ex -> {
                sender.sendMessage(ChatColor.RED + "x Error loading chunk: " + ex.getMessage());
                logger.error("ChunkLoadUtility error in stock handler", ex);
                return null;
            });
        }).exceptionally(ex -> {
            sender.sendMessage(ChatColor.RED + "x Error fetching shop: " + ex.getMessage());
            logger.error("Error in handleStock", ex);
            return null;
        });

        sender.sendMessage(ChatColor.YELLOW + "* Inspecting shop chest...");
        return true;
    }

    /**
     * Reports a human-readable inventory summary to the sender.
     */
    private void reportInventoryContents(CommandSender sender, ShopDataDTO shop, Inventory inv) {
        sender.sendMessage(ChatColor.GOLD + "--- Chest Contents: " + shop.shopName()
                + " (#" + shop.shopId() + ") ---");

        int totalItems = 0;
        int emptySlots = 0;

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType() == Material.AIR) {
                emptySlots++;
            } else {
                sender.sendMessage(ChatColor.GRAY + "  Slot " + i + ": "
                        + ChatColor.WHITE + stack.getType().name()
                        + ChatColor.GRAY + " x" + stack.getAmount());
                totalItems += stack.getAmount();
            }
        }

        sender.sendMessage(ChatColor.GOLD + "Total items: " + ChatColor.WHITE + totalItems
                + ChatColor.GRAY + " | Empty slots: " + emptySlots + "/" + inv.getSize());
    }

    /**
     * Fills up to {@code amount} items of the given stack type into empty inventory slots.
     *
     * @param inv      Target inventory
     * @param template Item stack to use as template (type and meta)
     * @param amount   Maximum number of individual items to place
     * @return Number of items actually placed
     */
    private int fillInventory(Inventory inv, ItemStack template, int amount) {
        int remaining = amount;
        int maxStack = template.getMaxStackSize();

        for (int i = 0; i < inv.getSize() && remaining > 0; i++) {
            ItemStack existing = inv.getItem(i);
            if (existing == null || existing.getType() == Material.AIR) {
                int toPlace = Math.min(remaining, maxStack);
                ItemStack fill = template.clone();
                fill.setAmount(toPlace);
                inv.setItem(i, fill);
                remaining -= toPlace;
            }
        }

        return amount - remaining;
    }

    /**
     * Handle the validate subcommand.
     * Usage: /shop debug validate [shopId]
     *
     * <p>Validates sign and chest integrity for one or all shops.</p>
     */
    private boolean handleValidate(CommandSender sender, String[] args) {
        if (args.length >= 1) {
            // Validate single shop
            String shopIdStr = args[0];
            shopService.getShopById(shopIdStr).thenAccept(optShop -> {
                if (optShop.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "x Shop not found: #" + shopIdStr);
                    return;
                }
                sender.sendMessage(ChatColor.GOLD + "--- Validating Shop #" + shopIdStr + " ---");
                validateShop(sender, optShop.get(), passed ->
                    sender.sendMessage(ChatColor.GRAY + "Validation complete."));
            }).exceptionally(ex -> {
                sender.sendMessage(ChatColor.RED + "x Error fetching shop: " + ex.getMessage());
                logger.error("Error in handleValidate (single)", ex);
                return null;
            });
        } else {
            // Validate all shops
            shopService.getAllShops().thenAccept(shops -> {
                if (shops.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "! No shops found.");
                    return;
                }

                sender.sendMessage(ChatColor.GOLD + "--- Validating " + shops.size() + " shops ---");

                // Counter arrays shared across async completions (all writes on main thread via validateShop)
                int[] valid = {0};
                int[] validated = {0};
                int total = shops.size();

                for (ShopDataDTO shop : shops) {
                    validateShop(sender, shop, passed -> {
                        if (Boolean.TRUE.equals(passed)) {
                            valid[0]++;
                        }
                        validated[0]++;
                        if (validated[0] >= total) {
                            sender.sendMessage(ChatColor.GOLD + "Summary: "
                                    + ChatColor.GREEN + valid[0]
                                    + ChatColor.GOLD + "/" + total + " shops valid.");
                        }
                    });
                }
            }).exceptionally(ex -> {
                sender.sendMessage(ChatColor.RED + "x Error fetching shops: " + ex.getMessage());
                logger.error("Error in handleValidate (all)", ex);
                return null;
            });
        }

        sender.sendMessage(ChatColor.YELLOW + "* Validating shop(s)...");
        return true;
    }

    /**
     * Validates a single shop and reports issues to the sender.
     *
     * <p>All block checks run on the main thread (via Bukkit scheduler).
     * The {@code onComplete} consumer is always called on the main thread
     * with {@code true} if the shop passed all checks, {@code false} otherwise.</p>
     *
     * @param sender     The command sender receiving output
     * @param shop       The shop DTO to validate
     * @param onComplete Consumer called on main thread with pass/fail boolean
     */
    private void validateShop(CommandSender sender, ShopDataDTO shop, Consumer<Boolean> onComplete) {
        List<String> issues = new ArrayList<>();

        // Check sign world exists
        if (shop.locationWorld() == null) {
            issues.add("sign world is null");
        } else {
            World signWorld = Bukkit.getWorld(shop.locationWorld());
            if (signWorld == null) {
                issues.add("sign world '" + shop.locationWorld() + "' not loaded");
            }
        }

        // Check chest location is set
        if (shop.chestLocationWorld() == null) {
            issues.add("no chest bound");
        }

        // Check active flag
        if (!shop.isActive()) {
            issues.add("shop is inactive");
        }

        // If sign world is loaded, check sign block type (requires chunk load + main thread)
        if (shop.locationWorld() != null) {
            World signWorld = Bukkit.getWorld(shop.locationWorld());
            if (signWorld != null) {
                Location signLoc = new Location(signWorld,
                        shop.locationX(), shop.locationY(), shop.locationZ());

                ChunkLoadUtility.loadChunkForBlock(signLoc).thenRun(() ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Block signBlock = signLoc.getBlock();
                        if (!signBlock.getType().name().contains("SIGN")) {
                            issues.add("sign block is " + signBlock.getType().name() + " (expected SIGN)");
                        }

                        // Check chest block if chest world is loaded
                        if (shop.chestLocationWorld() != null) {
                            World chestWorld = Bukkit.getWorld(shop.chestLocationWorld());
                            if (chestWorld == null) {
                                issues.add("chest world '" + shop.chestLocationWorld() + "' not loaded");
                                boolean passed = reportValidationResult(sender, shop, issues);
                                onComplete.accept(passed);
                                return;
                            }

                            Location chestLoc = new Location(chestWorld,
                                    shop.chestLocationX(), shop.chestLocationY(), shop.chestLocationZ());

                            ChunkLoadUtility.loadChunkForBlock(chestLoc).thenRun(() ->
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    Block chestBlock = chestLoc.getBlock();
                                    if (!CONTAINER_TYPES.contains(chestBlock.getType())) {
                                        issues.add("chest block is " + chestBlock.getType().name()
                                                + " (expected container)");
                                    }
                                    boolean passed = reportValidationResult(sender, shop, issues);
                                    onComplete.accept(passed);
                                })
                            ).exceptionally(ex -> {
                                issues.add("error loading chest chunk: " + ex.getMessage());
                                boolean passed = reportValidationResult(sender, shop, issues);
                                onComplete.accept(passed);
                                return null;
                            });
                        } else {
                            // No chest bound — already captured in issues
                            boolean passed = reportValidationResult(sender, shop, issues);
                            onComplete.accept(passed);
                        }
                    })
                ).exceptionally(ex -> {
                    issues.add("error loading sign chunk: " + ex.getMessage());
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        boolean passed = reportValidationResult(sender, shop, issues);
                        onComplete.accept(passed);
                    });
                    return null;
                });
            } else {
                // Sign world not loaded — issues already captured; complete synchronously
                boolean passed = reportValidationResult(sender, shop, issues);
                Bukkit.getScheduler().runTask(plugin, () -> onComplete.accept(passed));
            }
        } else {
            // Sign world null — already captured
            boolean passed = reportValidationResult(sender, shop, issues);
            Bukkit.getScheduler().runTask(plugin, () -> onComplete.accept(passed));
        }
    }

    /**
     * Sends the validation result line for a single shop to the sender.
     *
     * @return {@code true} if the shop has no issues, {@code false} otherwise
     */
    private boolean reportValidationResult(CommandSender sender, ShopDataDTO shop, List<String> issues) {
        if (issues.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "✓ " + shop.shopName()
                    + " (#" + shop.shopId() + ") " + ChatColor.GRAY + "— OK");
            return true;
        } else {
            sender.sendMessage(ChatColor.RED + "✗ " + shop.shopName()
                    + " (#" + shop.shopId() + ") "
                    + ChatColor.GRAY + "— " + String.join(", ", issues));
            return false;
        }
    }

    /**
     * Handle the rebind subcommand.
     * Usage: /shop debug rebind <shopId> <world> <x> <y> <z>
     *
     * <p>Rebinds the chest location of an existing shop.</p>
     */
    private boolean handleRebind(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "> Usage: /shop debug rebind <shopId> <world> <x> <y> <z>");
            return true;
        }

        String shopIdStr = args[0];
        String worldName = args[1];

        int bx, by, bz;
        try {
            bx = Integer.parseInt(args[2]);
            by = Integer.parseInt(args[3]);
            bz = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "x Coordinates must be integers.");
            return true;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "x World '" + worldName + "' is not loaded.");
            return true;
        }

        Location newLoc = new Location(world, bx, by, bz);

        ChunkLoadUtility.loadChunkForBlock(newLoc).thenRun(() ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                Block block = newLoc.getBlock();

                if (!CONTAINER_TYPES.contains(block.getType())) {
                    sender.sendMessage(ChatColor.RED + "x Block at " + worldName + ":" + bx + "," + by + "," + bz
                            + " is not a container: " + block.getType().name());
                    return;
                }

                ShopUpdateRequest update = ShopUpdateRequest.builder()
                        .chestLocation(newLoc)
                        .build();

                shopService.updateShop(shopIdStr, update).thenAccept(success -> {
                    if (success) {
                        sender.sendMessage(ChatColor.GREEN + "✓ Shop #" + shopIdStr
                                + " chest rebound to " + worldName + ":" + bx + "," + by + "," + bz);
                        logger.info("Rebound shop #" + shopIdStr + " chest to "
                                + worldName + ":" + bx + "," + by + "," + bz);
                    } else {
                        sender.sendMessage(ChatColor.RED + "x Shop #" + shopIdStr
                                + " not found or update failed.");
                    }
                }).exceptionally(ex -> {
                    sender.sendMessage(ChatColor.RED + "x Error updating shop: " + ex.getMessage());
                    logger.error("Error in handleRebind updateShop", ex);
                    return null;
                });
            })
        ).exceptionally(ex -> {
            sender.sendMessage(ChatColor.RED + "x Error loading chunk: " + ex.getMessage());
            logger.error("ChunkLoadUtility error in rebind handler", ex);
            return null;
        });

        sender.sendMessage(ChatColor.YELLOW + "* Rebinding chest location...");
        return true;
    }

    /**
     * Handle the create subcommand.
     * Usage: /shop debug create <name> <world> <x> <y> <z> [ownerName]
     *
     * <p>Programmatically creates a shop. The sign is placed at (x, y+1, z) as a standing OAK_SIGN.
     * If no owner is specified the console UUID ({@code 00000000-...}) is used.</p>
     */
    private boolean handleCreate(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "> Usage: /shop debug create <name> <world> <x> <y> <z> [ownerName]");
            return true;
        }

        String shopName = args[0];
        String worldName = args[1];

        int cx, cy, cz;
        try {
            cx = Integer.parseInt(args[2]);
            cy = Integer.parseInt(args[3]);
            cz = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "x Coordinates must be integers.");
            return true;
        }

        // Resolve owner
        UUID ownerUuid;
        if (args.length >= 6) {
            String ownerName = args[5];
            Player onlineOwner = Bukkit.getPlayer(ownerName);
            if (onlineOwner != null) {
                ownerUuid = onlineOwner.getUniqueId();
            } else {
                @SuppressWarnings("deprecation")
                var offline = Bukkit.getOfflinePlayer(ownerName);
                if (!offline.hasPlayedBefore()) {
                    sender.sendMessage(ChatColor.RED + "x Player '" + ownerName + "' has never joined this server.");
                    return true;
                }
                ownerUuid = offline.getUniqueId();
            }
        } else {
            ownerUuid = CONSOLE_UUID;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "x World '" + worldName + "' is not loaded.");
            return true;
        }

        Location chestLoc = new Location(world, cx, cy, cz);
        Location signLoc = new Location(world, cx, cy + 1, cz);

        final UUID finalOwnerUuid = ownerUuid;
        final String truncatedName = shopName.length() > 15 ? shopName.substring(0, 15) : shopName;

        ChunkLoadUtility.loadChunkForBlock(chestLoc).thenRun(() ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Place or use existing chest block
                Block chestBlock = chestLoc.getBlock();
                if (chestBlock.getType() == Material.AIR) {
                    chestBlock.setType(Material.CHEST);
                } else if (!CONTAINER_TYPES.contains(chestBlock.getType())) {
                    sender.sendMessage(ChatColor.RED + "x Block at " + worldName + ":" + cx + "," + cy + "," + cz
                            + " is not AIR or a container: " + chestBlock.getType().name());
                    return;
                }

                // Place or use existing sign block
                Block signBlock = signLoc.getBlock();
                if (signBlock.getType() == Material.AIR) {
                    signBlock.setType(Material.OAK_SIGN);
                }

                // setLine is deprecated in Paper but is the correct API on spigot-api 1.21
                if (signBlock.getState() instanceof Sign signState) {
                    @SuppressWarnings("deprecation")
                    Sign s = signState;
                    s.setLine(0, truncatedName);
                    s.setLine(1, "[Barter]");
                    s.update();
                }

                // Create shop record via service (async)
                shopService.createShop(finalOwnerUuid, signLoc, shopName)
                    .thenAccept(createdShop -> {
                        // Bind chest location
                        ShopUpdateRequest update = ShopUpdateRequest.builder()
                                .chestLocation(chestLoc)
                                .build();

                        shopService.updateShop(String.valueOf(createdShop.shopId()), update)
                            .thenRun(() ->
                                Bukkit.getScheduler().runTask(plugin, () ->
                                    sender.sendMessage(ChatColor.GREEN + "+ Shop '"
                                            + shopName + "' created (#" + createdShop.shopId()
                                            + ") at " + worldName + ":" + cx + "," + cy + "," + cz
                                            + " — sign at " + cx + "," + (cy + 1) + "," + cz)
                                )
                            ).exceptionally(ex -> {
                                sender.sendMessage(ChatColor.RED + "x Shop created but chest bind failed: "
                                        + ex.getMessage());
                                logger.error("Error binding chest in handleCreate", ex);
                                return null;
                            });
                    })
                    .exceptionally(ex -> {
                        sender.sendMessage(ChatColor.RED + "x Failed to create shop: " + ex.getMessage());
                        logger.error("Error in handleCreate createShop", ex);
                        return null;
                    });
            })
        ).exceptionally(ex -> {
            sender.sendMessage(ChatColor.RED + "x Error loading chunk: " + ex.getMessage());
            logger.error("ChunkLoadUtility error in create handler", ex);
            return null;
        });

        sender.sendMessage(ChatColor.YELLOW + "* Creating shop...");
        return true;
    }

    // =========================================================
    // Usage / metadata
    // =========================================================

    private void showUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Shop Debug Commands ===");
        sender.sendMessage(ChatColor.GRAY + "/shop debug" + ChatColor.DARK_GRAY + " - Show debug info");
        sender.sendMessage(ChatColor.GRAY + "/shop debug loglevel [level]" + ChatColor.DARK_GRAY + " - View/change log level");
        sender.sendMessage(ChatColor.GRAY + "/shop debug seed <action>" + ChatColor.DARK_GRAY + " - Seed test data");
        sender.sendMessage(ChatColor.GRAY + "/shop debug diagnostics" + ChatColor.DARK_GRAY + " - System diagnostics");
        sender.sendMessage(ChatColor.GRAY + "/shop debug changeowner <shopId> <playerName>" + ChatColor.DARK_GRAY + " - Change shop owner");
        sender.sendMessage(ChatColor.GRAY + "/shop debug stock <shopId> [amount]" + ChatColor.DARK_GRAY + " - Inspect/restock chest");
        sender.sendMessage(ChatColor.GRAY + "/shop debug validate [shopId]" + ChatColor.DARK_GRAY + " - Validate shop integrity");
        sender.sendMessage(ChatColor.GRAY + "/shop debug rebind <shopId> <world> <x> <y> <z>" + ChatColor.DARK_GRAY + " - Rebind chest");
        sender.sendMessage(ChatColor.GRAY + "/shop debug create <name> <world> <x> <y> <z> [owner]" + ChatColor.DARK_GRAY + " - Create shop");
    }

    @Override
    public String getDescription() {
        return "Debug and diagnostic commands";
    }

    @Override
    public String getUsage() {
        return "/shop debug [loglevel|seed|diagnostics|stock|validate|rebind|create]";
    }

    @Override
    public String getPermission() {
        return "bartershops.admin";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.hasPermission("bartershops.admin");
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String cmd : SUB_COMMANDS) {
                if (cmd.startsWith(partial)) {
                    completions.add(cmd);
                }
            }
            return completions;
        }

        if (args.length < 2) {
            return completions;
        }

        String subCmd = args[0].toLowerCase();
        String partial = args[1].toLowerCase();

        switch (subCmd) {
            case "loglevel": {
                String upper = args[1].toUpperCase();
                for (String lvl : LOG_LEVELS) {
                    if (lvl.startsWith(upper)) {
                        completions.add(lvl);
                    }
                }
                break;
            }
            case "changeowner": {
                if (args.length == 2) {
                    addShopIdCompletions(completions, partial);
                } else if (args.length == 3) {
                    String playerPartial = args[2].toLowerCase();
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(playerPartial)) {
                            completions.add(player.getName());
                        }
                    }
                }
                break;
            }
            case "seed":
                return seedCommand.getTabCompletions(sender, Arrays.copyOfRange(args, 1, args.length));

            case "stock": {
                if (args.length == 2) {
                    addShopIdCompletions(completions, partial);
                }
                break;
            }
            case "validate": {
                if (args.length == 2) {
                    addShopIdCompletions(completions, partial);
                }
                break;
            }
            case "rebind": {
                if (args.length == 2) {
                    addShopIdCompletions(completions, partial);
                } else if (args.length == 3) {
                    addWorldNameCompletions(completions, args[2].toLowerCase());
                }
                break;
            }
            case "create": {
                if (args.length == 3) {
                    addWorldNameCompletions(completions, args[2].toLowerCase());
                } else if (args.length == 7) {
                    String playerPartial = args[6].toLowerCase();
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(playerPartial)) {
                            completions.add(player.getName());
                        }
                    }
                }
                break;
            }
            default:
                break;
        }

        // Passthrough for seed deeper args
        if (subCmd.equals("seed") && args.length > 2) {
            return seedCommand.getTabCompletions(sender, Arrays.copyOfRange(args, 1, args.length));
        }

        return completions;
    }

    /**
     * Appends matching shop IDs (from the sign cache) to the completions list.
     */
    private void addShopIdCompletions(List<String> completions, String partial) {
        if (plugin.getSignManager() == null) {
            return;
        }
        plugin.getSignManager().getBarterSigns().values().stream()
                .filter(s -> s.getShopId() > 0)
                .map(s -> String.valueOf(s.getShopId()))
                .sorted((a, b) -> Integer.compare(Integer.parseInt(b), Integer.parseInt(a)))
                .filter(id -> id.startsWith(partial))
                .forEach(completions::add);
    }

    /**
     * Appends matching world names to the completions list.
     */
    private void addWorldNameCompletions(List<String> completions, String partial) {
        for (World w : Bukkit.getWorlds()) {
            if (w.getName().toLowerCase().startsWith(partial)) {
                completions.add(w.getName());
            }
        }
    }

    @Override
    public boolean requiresPlayer() {
        return false; // Console-compatible
    }
}
