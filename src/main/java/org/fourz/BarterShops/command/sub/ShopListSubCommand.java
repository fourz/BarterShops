package org.fourz.BarterShops.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.util.TableDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Subcommand for listing shops.
 * Usage: /shop list [player] [page]
 * Console-friendly: Yes
 */
public class ShopListSubCommand implements SubCommand {

    private final BarterShops plugin;
    private static final int ITEMS_PER_PAGE = 10;

    // Column order: # | TYPE | Offering | Owner | Location
    // Color-coded segments — no fixed-width padding (variable-width font safe).
    private final TableDisplay<ShopDataDTO> table;

    public ShopListSubCommand(BarterShops plugin) {
        this.plugin = plugin;
        this.table = TableDisplay.<ShopDataDTO>builder()
                .column("#",        ChatColor.GRAY,      s -> String.valueOf(s.shopId()))
                .column("TYPE",     ChatColor.YELLOW,    s -> s.shopType() != null ? s.shopType().name() : "BARTER")
                .column("Offering", ChatColor.GREEN,     s -> formatOffering(s.metadata()))
                .column("Owner",    ChatColor.WHITE,     s -> truncate(plugin.getPlayerLookup().getPlayerName(s.ownerUuid()), 16))
                .column("Location", ChatColor.DARK_GRAY, ShopListSubCommand::coords)
                .build();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (plugin.getShopRepository() == null) {
            sender.sendMessage(ChatColor.RED + "Shop database not available.");
            return true;
        }

        UUID filterOwner = null;
        int page = 1;

        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                if (target.hasPlayedBefore() || target.isOnline()) {
                    filterOwner = target.getUniqueId();
                } else {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                    return true;
                }
            }
        }

        if (args.length > 1 && filterOwner != null) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid page number: " + args[1]);
                return true;
            }
        }

        List<ShopDataDTO> allShops;
        try {
            allShops = filterOwner != null
                    ? plugin.getShopRepository().findByOwner(filterOwner).join()
                    : plugin.getShopRepository().findAllActive().join();
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to query shops from database.");
            return true;
        }

        if (allShops.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + (filterOwner != null
                    ? "No shops found for that player."
                    : "No shops found!"));
            return true;
        }

        // Pagination
        int totalPages = (int) Math.ceil(allShops.size() / (double) ITEMS_PER_PAGE);
        page = Math.max(1, Math.min(page, totalPages));
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allShops.size());

        String header = filterOwner != null
                ? "=== Shops by " + plugin.getPlayerLookup().getPlayerName(filterOwner) + " ==="
                : "=== All Barter Shops ===";
        sender.sendMessage(ChatColor.GREEN + header);
        table.renderHeader(sender);
        table.render(sender, allShops.subList(startIndex, endIndex));

        sender.sendMessage(ChatColor.GREEN + "Total: " + ChatColor.WHITE + allShops.size()
                + ChatColor.GRAY + " | Page " + page + "/" + totalPages);

        if (totalPages > 1) {
            String navHint = filterOwner != null
                    ? "/shop list " + plugin.getPlayerLookup().getPlayerName(filterOwner) + " <page>"
                    : "/shop list <page>";
            sender.sendMessage(ChatColor.GRAY + "Use " + navHint + " to navigate pages");
        }

        return true;
    }

    @Override
    public String getDescription() {
        return "List all shops or shops by player";
    }

    @Override
    public String getUsage() {
        return "/shop list [player] [page]";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.hasPermission("bartershops.admin");
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
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
            if ("1".startsWith(partial)) completions.add("1");
        } else if (args.length == 2) {
            completions.add("1");
            completions.add("2");
        }

        return completions;
    }

    @Override
    public boolean requiresPlayer() {
        return false;
    }

    // -------------------------------------------------------------------------
    // Private helpers used by TableDisplay extractors
    // -------------------------------------------------------------------------

    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max - 3) + "..." : value;
    }

    private static String coords(ShopDataDTO s) {
        return s.locationWorld() != null
                ? String.format("%d,%d,%d", (int) s.locationX(), (int) s.locationY(), (int) s.locationZ())
                : "N/A";
    }

    private static String formatOffering(Map<String, String> metadata) {
        if (metadata == null) return "";
        String json = metadata.get("shop_config_offering");
        if (json == null || json.isEmpty()) return "";
        try {
            String type = json.replaceAll(".*\"type\":\\s*\"([^\"]+)\".*", "$1");
            String amount = json.replaceAll(".*\"amount\":\\s*(\\d+).*", "$1");
            if (type.equals(json)) return "";
            String[] words = type.toLowerCase().split("_");
            StringBuilder name = new StringBuilder();
            for (String w : words) {
                if (!w.isEmpty()) {
                    if (!name.isEmpty()) name.append(' ');
                    name.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
                }
            }
            return name + " x" + (amount.equals(json) ? "?" : amount);
        } catch (Exception e) {
            return "";
        }
    }

    /** Worked examples served by {@code /shop help <verb>} (#1981). */
    @Override
    public java.util.List<String> getExamples() {
        return java.util.List.of(
                "/shop list",
                "/shop list 2",
                "  page 2",
                "/shop list Shad0melt",
                "/shop list Shad0melt 2");
    }
}
