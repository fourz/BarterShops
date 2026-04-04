package org.fourz.BarterShops.command.sub;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.BarterShops.sign.renderer.SignRenderUtil;
import org.fourz.BarterShops.util.TableDisplay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Subcommand for finding nearby shops.
 * Usage: /shop nearby [radius]
 * Player-only: Yes (needs location)
 */
public class ShopNearbySubCommand implements SubCommand {

    private final BarterShops plugin;
    private static final int DEFAULT_RADIUS = 50;
    private static final int MAX_RADIUS = 200;

    // Bundles a sign with its pre-computed distance so lambdas stay pure.
    private record NearbyEntry(BarterSign sign, int distanceMeters) {}

    private final TableDisplay<NearbyEntry> table;

    public ShopNearbySubCommand(BarterShops plugin) {
        this.plugin = plugin;
        this.table = TableDisplay.<NearbyEntry>builder()
                .column("TYPE",     ChatColor.YELLOW,    e -> String.valueOf(e.sign().getType()))
                .column("Dist",     ChatColor.GRAY,      e -> e.distanceMeters() + "m")
                .column("Offering", ChatColor.GREEN,     e -> offeringStr(e.sign()))
                .column("Owner",    ChatColor.WHITE,     e -> truncate(plugin.getPlayerLookup().getPlayerName(e.sign().getOwner()), 16))
                .build();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        int radius = DEFAULT_RADIUS;
        if (args.length > 0) {
            try {
                radius = Integer.parseInt(args[0]);
                radius = Math.max(1, Math.min(radius, MAX_RADIUS));
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid radius: " + args[0]);
                return true;
            }
        }

        Location playerLoc = player.getLocation();
        Map<Location, BarterSign> allShops = plugin.getSignManager().getBarterSigns();

        final int searchRadius = radius;
        List<NearbyEntry> entries = allShops.entrySet().stream()
                .filter(e -> {
                    Location loc = e.getKey();
                    return loc.getWorld().equals(playerLoc.getWorld())
                            && loc.distance(playerLoc) <= searchRadius;
                })
                .sorted(Comparator.comparingDouble(e -> e.getKey().distance(playerLoc)))
                .map(e -> new NearbyEntry(e.getValue(), (int) e.getKey().distance(playerLoc)))
                .toList();

        if (entries.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No shops found within " + radius + " blocks.");
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + "=== Shops within " + radius + " blocks ===");
        table.renderHeader(sender);
        table.render(sender, entries);
        sender.sendMessage(ChatColor.GREEN + "Found " + entries.size() + " shop(s)");

        return true;
    }

    @Override
    public String getDescription() {
        return "Find shops near your location";
    }

    @Override
    public String getUsage() {
        return "/shop nearby [radius]";
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
            String partial = args[0];
            for (String r : List.of("25", "50", "100", "200")) {
                if (r.startsWith(partial)) completions.add(r);
            }
        }
        return completions;
    }

    @Override
    public boolean requiresPlayer() {
        return true;
    }

    // -------------------------------------------------------------------------

    private static String offeringStr(BarterSign sign) {
        ItemStack item = sign.getItemOffering();
        if (item == null) return "";
        return SignRenderUtil.formatItemName(item) + " x" + item.getAmount();
    }

    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max - 3) + "..." : value;
    }
}
