package org.fourz.BarterShops.command.sub;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.gui.admin.AdminShopGUI;
import org.fourz.BarterShops.gui.admin.AdminStatsGUI;
import org.fourz.BarterShops.gui.admin.AdminTradeHistoryGUI;

import java.util.ArrayList;
import java.util.List;

/**
 * Subcommand to open admin GUI interfaces.
 * Usage: /shop admingui [shops|trades|stats]
 *
 * This command provides GUI-based admin tools for managing shops,
 * viewing trade history, and monitoring server statistics.
 *
 * Console-friendly: No (requires player for GUI interaction)
 * Permission: bartershops.admin.gui
 */
public class ShopAdminGUISubCommand implements SubCommand {

    private final BarterShops plugin;
    private final AdminShopGUI shopGUI;
    private final AdminTradeHistoryGUI tradeHistoryGUI;
    private final AdminStatsGUI statsGUI;

    public ShopAdminGUISubCommand(BarterShops plugin) {
        this.plugin = plugin;
        this.shopGUI = new AdminShopGUI(plugin);
        this.tradeHistoryGUI = new AdminTradeHistoryGUI(plugin);
        this.statsGUI = new AdminStatsGUI(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // Must be a player
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            sender.sendMessage(ChatColor.GRAY + "Admin GUI requires interactive inventory access.");
            return true;
        }

        // Default to shop browser if no args
        if (args.length == 0) {
            shopGUI.openShopBrowser(player);
            return true;
        }

        // Parse GUI type
        String guiType = args[0].toLowerCase();

        switch (guiType) {
            case "shops", "shop", "browser" -> {
                shopGUI.openShopBrowser(player);
                player.sendMessage(ChatColor.GREEN + "Opening shop browser...");
            }
            case "trades", "trade", "history" -> {
                tradeHistoryGUI.openTradeHistory(player);
                player.sendMessage(ChatColor.GREEN + "Opening trade history...");
            }
            case "stats", "statistics", "analytics" -> {
                statsGUI.openStatsView(player);
                player.sendMessage(ChatColor.GREEN + "Opening analytics dashboard...");
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown GUI type: " + guiType);
                sender.sendMessage(ChatColor.YELLOW + "Available GUIs:");
                sender.sendMessage(ChatColor.GRAY + "  shops - Shop browser and management");
                sender.sendMessage(ChatColor.GRAY + "  trades - Trade history viewer");
                sender.sendMessage(ChatColor.GRAY + "  stats - Analytics dashboard");
                return true;
            }
        }

        return true;
    }

    @Override
    public String getDescription() {
        return "Open admin GUI interfaces";
    }

    @Override
    public String getUsage() {
        return "/shop admingui [shops|trades|stats]";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.isOp();
    }

    @Override
    public String getPermission() {
        return "bartershops.admin";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String type : List.of("shops", "trades", "stats")) {
                if (type.startsWith(partial)) {
                    completions.add(type);
                }
            }
        }

        return completions;
    }

    @Override
    public boolean requiresPlayer() {
        return true; // GUI requires player interaction
    }

    /**
     * Gets the shop GUI manager.
     *
     * @return The AdminShopGUI instance
     */
    public AdminShopGUI getShopGUI() {
        return shopGUI;
    }

    /**
     * Gets the trade history GUI manager.
     *
     * @return The AdminTradeHistoryGUI instance
     */
    public AdminTradeHistoryGUI getTradeHistoryGUI() {
        return tradeHistoryGUI;
    }

    /**
     * Gets the stats GUI manager.
     *
     * @return The AdminStatsGUI instance
     */
    public AdminStatsGUI getStatsGUI() {
        return statsGUI;
    }

    /**
     * Shuts down all GUI managers.
     */
    public void shutdown() {
        shopGUI.shutdown();
        tradeHistoryGUI.shutdown();
        statsGUI.shutdown();
    }
}
