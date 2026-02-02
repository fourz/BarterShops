package org.fourz.BarterShops.command.sub;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.economy.EconomyManager;
import org.fourz.BarterShops.economy.ShopFeeCalculator;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Subcommand for viewing trade tax information.
 * Usage: /shop tax [info|calculate <amount>]
 * Console-friendly: Yes
 */
public class ShopTaxSubCommand implements SubCommand {
    private final BarterShops plugin;
    private final LogManager logger;
    private final EconomyManager economyManager;
    private final ShopFeeCalculator calculator;

    public ShopTaxSubCommand(BarterShops plugin, EconomyManager economyManager, ShopFeeCalculator calculator) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ShopTax");
        this.economyManager = economyManager;
        this.calculator = calculator;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!economyManager.isEconomyEnabled()) {
            sender.sendMessage(ChatColor.YELLOW + "Economy features are disabled (Vault not found)");
            return true;
        }

        if (!economyManager.areTaxesEnabled()) {
            sender.sendMessage(ChatColor.YELLOW + "Trade taxes are currently disabled");
            sender.sendMessage(ChatColor.GRAY + "Check config.yml: economy.taxes.enabled");
            return true;
        }

        // Handle subcommands
        if (args.length >= 2) {
            String subAction = args[1].toLowerCase();

            if (subAction.equals("calculate") && args.length >= 3) {
                return handleCalculate(sender, args[2]);
            }
        }

        // Default: show tax info
        return showTaxInfo(sender);
    }

    /**
     * Shows general tax information.
     */
    private boolean showTaxInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Trade Tax Information ===");
        sender.sendMessage("");

        double taxRate = economyManager.getTaxRate();
        String taxPercentage = calculator.getTaxRatePercentage();

        sender.sendMessage(ChatColor.AQUA + "Current Tax Rate:");
        sender.sendMessage(ChatColor.WHITE + "  " + taxPercentage);
        sender.sendMessage("");

        sender.sendMessage(ChatColor.AQUA + "How Taxes Work:");
        sender.sendMessage(ChatColor.GRAY + "  - Applied to currency-based trades");
        sender.sendMessage(ChatColor.GRAY + "  - Deducted from buyer at purchase");
        sender.sendMessage(ChatColor.GRAY + "  - Volume discounts available for large trades");
        sender.sendMessage("");

        sender.sendMessage(ChatColor.AQUA + "Volume Discounts:");
        sender.sendMessage(ChatColor.WHITE + "  $1,000+  " + ChatColor.GRAY + "- 5% discount");
        sender.sendMessage(ChatColor.WHITE + "  $5,000+  " + ChatColor.GRAY + "- 15% discount");
        sender.sendMessage(ChatColor.WHITE + "  $10,000+ " + ChatColor.GRAY + "- 25% discount");
        sender.sendMessage("");

        sender.sendMessage(ChatColor.GRAY + "Use '/shop tax calculate <amount>' to estimate tax");

        // Show statistics if available
        economyManager.getTotalTaxesCollected().thenAccept(totalTaxes -> {
            if (totalTaxes > 0 && sender.hasPermission("bartershops.admin.economy")) {
                sender.sendMessage("");
                sender.sendMessage(ChatColor.DARK_GRAY + "Total taxes collected: " + economyManager.format(totalTaxes));
            }
        });

        return true;
    }

    /**
     * Calculates tax for a specific amount.
     */
    private boolean handleCalculate(CommandSender sender, String amountStr) {
        try {
            double amount = Double.parseDouble(amountStr);

            if (amount <= 0) {
                sender.sendMessage(ChatColor.RED + "Amount must be positive");
                return true;
            }

            sender.sendMessage(ChatColor.GOLD + "=== Tax Calculation for " + economyManager.format(amount) + " ===");
            sender.sendMessage("");

            // Standard tax
            double standardTax = calculator.calculateTradeTax(amount);
            sender.sendMessage(ChatColor.AQUA + "Standard Tax (" + calculator.getTaxRatePercentage() + "):");
            sender.sendMessage(ChatColor.WHITE + "  " + economyManager.format(standardTax));
            sender.sendMessage("");

            // Tax with volume discount
            double discountedTax = calculator.calculateTradeTaxWithDiscount(amount);
            if (discountedTax < standardTax) {
                double savings = standardTax - discountedTax;
                sender.sendMessage(ChatColor.AQUA + "With Volume Discount:");
                sender.sendMessage(ChatColor.WHITE + "  " + economyManager.format(discountedTax));
                sender.sendMessage(ChatColor.GREEN + "  You save: " + economyManager.format(savings));
                sender.sendMessage("");
            }

            // Net amounts
            double netStandard = calculator.estimateNetProfit(amount, false);
            sender.sendMessage(ChatColor.AQUA + "Seller Receives:");
            sender.sendMessage(ChatColor.WHITE + "  " + economyManager.format(netStandard));

        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid amount: " + amountStr);
            sender.sendMessage(ChatColor.GRAY + "Usage: /shop tax calculate <amount>");
        }

        return true;
    }

    @Override
    public String getDescription() {
        return "View trade tax information";
    }

    @Override
    public String getUsage() {
        return "/shop tax [info|calculate <amount>]";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission());
    }

    @Override
    public String getPermission() {
        return "bartershops.economy.tax";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 2) {
            completions.add("info");
            completions.add("calculate");
        } else if (args.length == 3 && args[1].equalsIgnoreCase("calculate")) {
            completions.add("100");
            completions.add("500");
            completions.add("1000");
            completions.add("5000");
        }

        return completions;
    }

    @Override
    public boolean requiresPlayer() {
        return false;
    }
}
