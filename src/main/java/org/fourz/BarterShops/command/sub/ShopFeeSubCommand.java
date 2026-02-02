package org.fourz.BarterShops.command.sub;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.economy.EconomyManager;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Subcommand for viewing shop listing fees.
 * Usage: /shop fee [list]
 * Console-friendly: Yes
 */
public class ShopFeeSubCommand implements SubCommand {
    private final BarterShops plugin;
    private final LogManager logger;
    private final EconomyManager economyManager;

    public ShopFeeSubCommand(BarterShops plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ShopFee");
        this.economyManager = economyManager;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!economyManager.isEconomyEnabled()) {
            sender.sendMessage(ChatColor.YELLOW + "Economy features are disabled (Vault not found)");
            return true;
        }

        if (!economyManager.areFeesEnabled()) {
            sender.sendMessage(ChatColor.YELLOW + "Shop listing fees are currently disabled");
            sender.sendMessage(ChatColor.GRAY + "Check config.yml: economy.fees.enabled");
            return true;
        }

        // Show fee listing
        sender.sendMessage(ChatColor.GOLD + "=== Shop Listing Fees ===");
        sender.sendMessage("");

        double baseFee = economyManager.getBaseFee();
        double currencyFee = economyManager.getCurrencyShopFee();

        sender.sendMessage(ChatColor.AQUA + "Standard Barter Shop:");
        sender.sendMessage(ChatColor.WHITE + "  " + economyManager.format(baseFee));
        sender.sendMessage("");

        sender.sendMessage(ChatColor.AQUA + "Currency-Based Shop:");
        sender.sendMessage(ChatColor.WHITE + "  " + economyManager.format(currencyFee));
        sender.sendMessage("");

        sender.sendMessage(ChatColor.GRAY + "Fees are charged when creating a shop");
        sender.sendMessage(ChatColor.GRAY + "Rare items may have higher fees");

        // Show statistics if available
        economyManager.getTotalFeesCollected().thenAccept(totalFees -> {
            if (totalFees > 0 && sender.hasPermission("bartershops.admin.economy")) {
                sender.sendMessage("");
                sender.sendMessage(ChatColor.DARK_GRAY + "Total fees collected: " + economyManager.format(totalFees));
            }
        });

        return true;
    }

    @Override
    public String getDescription() {
        return "View shop listing fees";
    }

    @Override
    public String getUsage() {
        return "/shop fee [list]";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission());
    }

    @Override
    public String getPermission() {
        return "bartershops.economy.fee";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 2) {
            completions.add("list");
        }
        return completions;
    }

    @Override
    public boolean requiresPlayer() {
        return false;
    }
}
