package org.fourz.BarterShops.command.sub;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.inspection.ShopInfoDisplayHelper;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.BarterShops.trade.TradeValidator;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Subcommand for showing shop details from in-memory SignManager data.
 * Supports lookup by shop ID (1-based index) or username.
 * Usage: /shop info <id|username>
 * Console-friendly: Yes
 */
public class ShopInfoSubCommand implements SubCommand {

    private final BarterShops plugin;
    private final TradeValidator tradeValidator;

    public ShopInfoSubCommand(BarterShops plugin) {
        this.plugin = plugin;
        this.tradeValidator = new TradeValidator(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: " + getUsage());
            return true;
        }

        String shopArg = args[0];

        // Find shop by ID or username from in-memory SignManager
        Optional<Map.Entry<Location, BarterSign>> shopEntry = findShopByIdOrUsername(shopArg);

        if (shopEntry.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Shop not found: " + shopArg);
            sender.sendMessage(ChatColor.GRAY + "Use /shop list to see available shops.");
            return true;
        }

        Location location = shopEntry.get().getKey();
        BarterSign shop = shopEntry.get().getValue();

        // Use permission-aware display helper for players
        if (sender instanceof Player player) {
            ShopInfoDisplayHelper helper = plugin.getShopInfoDisplayHelper();
            helper.displayShopInfo(player, shop, location, ShopInfoDisplayHelper.InfoDisplayContext.COMMAND);
        } else {
            // Console: Always show full details
            displayConsoleInfo(sender, shop, location);
        }

        return true;
    }

    /**
     * Finds a shop by numeric ID (1-based index) or by owner username.
     *
     * @param arg Shop ID (numeric 1-based) or owner username
     * @return Optional containing the shop entry if found
     */
    private Optional<Map.Entry<Location, BarterSign>> findShopByIdOrUsername(String arg) {
        Map<Location, BarterSign> shops = plugin.getSignManager().getBarterSigns();

        // Try numeric shop ID first (1-based index)
        try {
            int index = Integer.parseInt(arg) - 1;
            List<Map.Entry<Location, BarterSign>> shopList = new ArrayList<>(shops.entrySet());
            if (index >= 0 && index < shopList.size()) {
                return Optional.of(shopList.get(index));
            }
        } catch (NumberFormatException ignored) {
        }

        // Try username lookup (O(n) scan)
        String lowerArg = arg.toLowerCase();
        return shops.entrySet().stream()
                .filter(entry -> {
                    String ownerName = plugin.getPlayerLookup().getPlayerName(entry.getValue().getOwner());
                    return ownerName.equalsIgnoreCase(lowerArg);
                })
                .findFirst();
    }

    /**
     * Calculates the current stock of the offered item in the shop container.
     *
     * @param shop BarterSign with shop data
     * @param offeredItem The item being offered for sale
     * @return Number of items available, or -1 if no container
     */
    private int calculateStock(BarterSign shop, ItemStack offeredItem) {
        if (shop.isAdmin()) {
            return -1; // Admin shops don't have physical inventory
        }

        // Use wrapper if available (Phase 2), otherwise fallback to plain container
        var inv = shop.getShopContainerWrapper() != null ?
                shop.getShopContainerWrapper().getInventory() :
                (shop.getShopContainer() != null ? shop.getShopContainer().getInventory() : null);

        if (inv == null) {
            return -1;
        }

        return tradeValidator.countItems(inv, offeredItem);
    }

    @Override
    public String getDescription() {
        return "Show detailed information about a shop";
    }

    @Override
    public String getUsage() {
        return "/shop info <id|username>";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.isOp();
    }

    @Override
    public String getPermission() {
        return "bartershops.use";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            Map<Location, BarterSign> shops = plugin.getSignManager().getBarterSigns();

            // Suggest shop IDs (1-based index)
            for (int i = 1; i <= Math.min(shops.size(), 20); i++) {
                String id = String.valueOf(i);
                if (id.startsWith(partial)) {
                    completions.add(id);
                }
            }

            // Suggest owner usernames
            Set<String> ownerNames = shops.values().stream()
                    .map(shop -> plugin.getPlayerLookup().getPlayerName(shop.getOwner()))
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toSet());
            completions.addAll(ownerNames);
        }

        return completions;
    }

    /**
     * Displays full shop info for console (no permission restrictions).
     */
    private void displayConsoleInfo(CommandSender sender, BarterSign shop, Location location) {
        sender.sendMessage(ChatColor.GOLD + "===== Shop Info (Console) =====");
        sender.sendMessage(ChatColor.YELLOW + "ID: " + ChatColor.WHITE + shop.getShopId());
        sender.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE +
                plugin.getPlayerLookup().getPlayerName(shop.getOwner()));
        sender.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + shop.getType().name());
        sender.sendMessage(ChatColor.YELLOW + "Mode: " + ChatColor.WHITE + shop.getMode().name());

        if (location.getWorld() != null) {
            sender.sendMessage(ChatColor.YELLOW + "Location: " + ChatColor.WHITE +
                    String.format("%s: %d, %d, %d",
                            location.getWorld().getName(),
                            location.getBlockX(), location.getBlockY(), location.getBlockZ()));
        }

        // Show offering item
        if (shop.getItemOffering() != null) {
            ItemStack offering = shop.getItemOffering();
            sender.sendMessage(ChatColor.YELLOW + "Offering: " + ChatColor.WHITE +
                    offering.getAmount() + "x " + offering.getType().name());
        }

        // Show payment options
        List<ItemStack> payments = shop.getAcceptedPayments();
        if (!payments.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Payment Options:");
            for (int i = 0; i < payments.size(); i++) {
                ItemStack payment = payments.get(i);
                sender.sendMessage(ChatColor.WHITE + "  " + (i + 1) + ". " +
                        payment.getAmount() + "x " + payment.getType().name());
            }
        }

        // Show stock
        if (shop.getItemOffering() != null) {
            int stock = calculateStock(shop, shop.getItemOffering());
            if (shop.isAdmin()) {
                sender.sendMessage(ChatColor.YELLOW + "Stock: " + ChatColor.GREEN + "Unlimited (admin shop)");
            } else if (stock >= 0) {
                sender.sendMessage(ChatColor.YELLOW + "Stock: " + ChatColor.WHITE + stock + " available");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Stock: " + ChatColor.RED + "No container found");
            }
        }

        sender.sendMessage(ChatColor.YELLOW + "Active: " + ChatColor.WHITE + "Yes");
        sender.sendMessage(ChatColor.GOLD + "================================");
    }

    @Override
    public boolean requiresPlayer() {
        return false; // Console-friendly
    }
}
