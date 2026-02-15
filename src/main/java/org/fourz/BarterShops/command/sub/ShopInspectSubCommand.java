package org.fourz.BarterShops.command.sub;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;

import java.util.Collections;
import java.util.List;

/**
 * Admin subcommand for toggling shop inspection tool.
 * When enabled, players can right-click shop signs to view detailed information.
 * Usage: /shop inspect
 * Console-friendly: No (requires player)
 */
public class ShopInspectSubCommand implements SubCommand {
    private final BarterShops plugin;

    public ShopInspectSubCommand(BarterShops plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use inspection mode");
            return true;
        }

        var inspectionManager = plugin.getInspectionManager();
        if (inspectionManager == null) {
            player.sendMessage(ChatColor.RED + "Inspection system not initialized");
            return true;
        }

        // Toggle inspection mode
        boolean wasInspecting = inspectionManager.toggleInspectionMode(player.getUniqueId());

        if (wasInspecting) {
            // Mode turned OFF - remove tool from inventory
            removeInspectionTool(player);
            player.sendMessage(ChatColor.YELLOW + "- Inspection mode disabled");
        } else {
            // Mode turned ON - give inspection tool
            ItemStack tool = inspectionManager.createInspectionTool();
            player.getInventory().addItem(tool);
            player.sendMessage(ChatColor.GREEN + "+ Inspection mode enabled");
            player.sendMessage(ChatColor.GRAY + "Right-click shop signs with the tool to inspect");
        }

        return true;
    }

    /**
     * Removes inspection tools from player inventory.
     *
     * @param player Player to remove tools from
     */
    private void removeInspectionTool(Player player) {
        var inspectionManager = plugin.getInspectionManager();
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (inspectionManager.isInspectionTool(item)) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    @Override
    public String getDescription() {
        return "Toggle shop inspection tool for real-time inspection";
    }

    @Override
    public String getUsage() {
        return "/shop inspect";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.isOp();
    }

    @Override
    public String getPermission() {
        return "bartershops.admin.inspect";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        return Collections.emptyList(); // No arguments needed
    }

    @Override
    public boolean requiresPlayer() {
        return true;
    }
}
