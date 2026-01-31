package org.fourz.BarterShops.command.sub;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.data.dto.ShopDataDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * Subcommand for creating a new shop.
 * Usage: /shop create <name>
 */
public class ShopCreateSubCommand implements SubCommand {

    private final BarterShops plugin;

    public ShopCreateSubCommand(BarterShops plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // This command requires a player to get the target block
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command must be used by a player.");
            sender.sendMessage(ChatColor.GRAY + "Console usage coming in future update.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: " + getUsage());
            return true;
        }

        String shopName = String.join(" ", args);

        // Get the block the player is looking at
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || !targetBlock.getType().name().contains("SIGN")) {
            sender.sendMessage(ChatColor.RED + "You must be looking at a sign to create a shop.");
            return true;
        }

        Location signLocation = targetBlock.getLocation();

        // Check if a shop already exists at this location
        if (plugin.getSignManager().getBarterSigns().containsKey(targetBlock)) {
            sender.sendMessage(ChatColor.RED + "A shop already exists at this location.");
            return true;
        }

        // Create shop via service (placeholder - service implementation pending)
        ShopDataDTO newShop = ShopDataDTO.builder()
                .ownerUuid(player.getUniqueId())
                .shopName(shopName)
                .shopType(ShopDataDTO.ShopType.BARTER)
                .signLocation(signLocation)
                .build();

        sender.sendMessage(ChatColor.GREEN + "Shop '" + shopName + "' created!");
        sender.sendMessage(ChatColor.GRAY + "Location: " + formatLocation(signLocation));
        sender.sendMessage(ChatColor.YELLOW + "Right-click the sign with items to configure trades.");

        return true;
    }

    private String formatLocation(Location loc) {
        return String.format("%s: %d, %d, %d",
                loc.getWorld() != null ? loc.getWorld().getName() : "unknown",
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    @Override
    public String getDescription() {
        return "Create a new shop at the sign you're looking at";
    }

    @Override
    public String getUsage() {
        return "/shop create <name>";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.isOp();
    }

    @Override
    public String getPermission() {
        return "bartershops.create";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            // Suggest example shop names
            String partial = args[0].toLowerCase();
            List<String> suggestions = List.of("MyShop", "TradePost", "Market");
            for (String suggestion : suggestions) {
                if (suggestion.toLowerCase().startsWith(partial)) {
                    completions.add(suggestion);
                }
            }
        }
        return completions;
    }

    @Override
    public boolean requiresPlayer() {
        return true;
    }
}
