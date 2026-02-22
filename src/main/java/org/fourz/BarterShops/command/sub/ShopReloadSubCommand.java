package org.fourz.BarterShops.command.sub;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin subcommand for reloading plugin configuration.
 * Usage: /shop reload
 * Console-friendly: Yes
 */
public class ShopReloadSubCommand implements SubCommand {
    private final BarterShops plugin;
    private final LogManager logger;

    public ShopReloadSubCommand(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ShopReload");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.YELLOW + "Reloading BarterShops configuration and caches...");

        try {
            long startTime = System.currentTimeMillis();

            // Reload configuration
            plugin.getConfigManager().reloadConfig();

            // Reload economy manager
            plugin.getEconomyManager().reloadConfiguration();
            logger.debug("Reloaded economy manager");

            // Reload type availability (shop types and sign types)
            if (plugin.getTypeAvailabilityManager() != null) {
                plugin.getTypeAvailabilityManager().loadEnabledTypes();
                logger.debug("Reloaded type availability");
            }

            // FIX 8df42120: Clear in-memory caches after config reload
            // SignManager: reload all barter signs from database
            if (plugin.getSignManager() != null) {
                plugin.getSignManager().clearSigns();
                plugin.getSignManager().loadSignsFromDatabase();
                logger.debug("Cleared and reloaded barter signs from database");
            }

            // TemplateManager: reload templates
            if (plugin.getTemplateManager() != null) {
                plugin.getTemplateManager().reload();
                logger.debug("Reloaded shop templates");
            }

            // Log the action
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Configuration and caches reloaded by " + sender.getName() + " (" + duration + "ms)");

            sender.sendMessage(ChatColor.GREEN + "BarterShops configuration and caches reloaded!");
            sender.sendMessage(ChatColor.GRAY + "Reload completed in " + duration + "ms");

        } catch (Exception e) {
            logger.warning("Failed to reload configuration: " + e.getMessage());
            sender.sendMessage(ChatColor.RED + "Failed to reload configuration!");
            sender.sendMessage(ChatColor.RED + "Error: " + e.getMessage());
            sender.sendMessage(ChatColor.GRAY + "Check console for details.");
        }

        return true;
    }

    @Override
    public String getDescription() {
        return "Reload plugin configuration (admin)";
    }

    @Override
    public String getUsage() {
        return "/shop reload";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.isOp();
    }

    @Override
    public String getPermission() {
        return "bartershops.admin.reload";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }

    @Override
    public boolean requiresPlayer() {
        return false;
    }
}
