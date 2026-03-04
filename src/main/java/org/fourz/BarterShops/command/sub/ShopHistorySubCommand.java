package org.fourz.BarterShops.command.sub;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.data.dto.TradeRecordDTO;
import org.fourz.BarterShops.data.repository.ITradeRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Subcommand for viewing paginated trade history for a specific shop.
 * Queries the trade repository asynchronously and formats output in chat.
 *
 * <p>Usage: /shop history &lt;shopId&gt; [page]</p>
 * <p>Console-friendly: Yes</p>
 */
public class ShopHistorySubCommand implements SubCommand {

    private static final int PAGE_SIZE = 5;
    private static final int MAX_FETCH = 50;
    private static final Pattern MATERIAL_PATTERN = Pattern.compile("^([A-Z_]+)");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM d, h:mm a");

    private final BarterShops plugin;

    public ShopHistorySubCommand(BarterShops plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: " + getUsage());
            return true;
        }

        int shopId;
        try {
            shopId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "x Invalid shop ID: " + args[0]);
            return true;
        }

        int page = 1;
        if (args.length >= 2) {
            try {
                page = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "x Invalid page number: " + args[1]);
                return true;
            }
        }

        ITradeRepository tradeRepo = plugin.getTradeRepository();
        if (tradeRepo == null) {
            sender.sendMessage(ChatColor.RED + "x Trade history is unavailable (database not connected).");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "* Loading trade history for shop #" + shopId + "...");

        final int finalPage = page;
        tradeRepo.findByShop(shopId, MAX_FETCH).thenAccept(trades -> {
            if (trades.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "! No trades found for shop #" + shopId + ".");
                return;
            }

            int totalPages = (int) Math.ceil(trades.size() / (double) PAGE_SIZE);
            int displayPage = Math.min(finalPage, totalPages);
            int fromIndex = (displayPage - 1) * PAGE_SIZE;
            int toIndex = Math.min(fromIndex + PAGE_SIZE, trades.size());
            List<TradeRecordDTO> pageTrades = trades.subList(fromIndex, toIndex);

            sender.sendMessage(ChatColor.GOLD + "===== Trade History — Shop #" + shopId
                    + " (Page " + displayPage + "/" + totalPages + ") =====");

            for (int i = 0; i < pageTrades.size(); i++) {
                TradeRecordDTO trade = pageTrades.get(i);
                int rowNum = fromIndex + i + 1;

                String statusColor = trade.status() == TradeRecordDTO.TradeStatus.COMPLETED
                        ? ChatColor.GREEN.toString() : ChatColor.RED.toString();
                String timestamp = trade.completedAt() != null
                        ? DATE_FORMAT.format(trade.completedAt()) : "Unknown";

                String buyer  = plugin.getPlayerLookup().getPlayerName(trade.buyerUuid());
                String seller = plugin.getPlayerLookup().getPlayerName(trade.sellerUuid());
                String offered  = formatItemData(trade.itemStackData(), trade.quantity());
                String currency = trade.pricePaid() + "x " + formatMaterialName(trade.currencyMaterial());

                sender.sendMessage(
                        ChatColor.GRAY + "#" + rowNum + "  "
                        + statusColor + "[" + trade.status().name() + "]  "
                        + ChatColor.GRAY + timestamp);
                sender.sendMessage(
                        ChatColor.GRAY + "   "
                        + ChatColor.AQUA + "Buyer: " + ChatColor.WHITE + buyer
                        + ChatColor.GRAY + "  |  "
                        + ChatColor.YELLOW + "Seller: " + ChatColor.WHITE + seller);
                sender.sendMessage(
                        ChatColor.GRAY + "   "
                        + ChatColor.AQUA + "Gave: " + ChatColor.WHITE + offered
                        + ChatColor.GRAY + "  \u2192  "
                        + ChatColor.YELLOW + "Got: " + ChatColor.WHITE + currency);
            }

            if (displayPage < totalPages) {
                sender.sendMessage(ChatColor.GRAY + "   Next: /shop history " + shopId + " " + (displayPage + 1));
            }
            sender.sendMessage(ChatColor.GOLD + "Total: " + trades.size()
                    + " trade" + (trades.size() == 1 ? "" : "s")
                    + " | Page " + displayPage + "/" + totalPages);

        }).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            sender.sendMessage(ChatColor.RED + "x Failed to load trade history: " + cause.getMessage());
            plugin.getLogger().warning("ShopHistorySubCommand error for shop #" + shopId + ": " + cause.getMessage());
            return null;
        });

        return true;
    }

    /**
     * Extracts a readable item name from serialized itemStackData.
     * Parses the material type from Bukkit YAML serialization (type: MATERIAL_NAME).
     * Falls back to "[item]" if the format is unrecognized.
     */
    private String formatItemData(String itemStackData, int quantity) {
        if (itemStackData == null || itemStackData.isBlank()) {
            return quantity + "x [unknown]";
        }
        Matcher m = MATERIAL_PATTERN.matcher(itemStackData);
        if (m.find()) {
            return quantity + "x " + formatMaterialName(m.group(1));
        }
        return quantity + "x [item]";
    }

    /**
     * Formats a material enum name for display (e.g. DIAMOND_SWORD → Diamond Sword).
     */
    private String formatMaterialName(String material) {
        if (material == null || material.isBlank()) return "[none]";
        String[] parts = material.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    @Override
    public String getDescription() {
        return "View trade history for a shop";
    }

    @Override
    public String getUsage() {
        return "/shop history <shopId> [page]";
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
        return new ArrayList<>();
    }

    @Override
    public boolean requiresPlayer() {
        return false; // Console-friendly
    }
}
