package org.fourz.BarterShops.command.sub;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;

import java.util.ArrayList;
import java.util.List;

/**
 * Refuses and explains how to create a shop. Shops are created by writing [barter] on a
 * sign next to a chest (SignManager.onSignChange); this command persists nothing (#2462).
 */
public class ShopCreateSubCommand implements SubCommand {

    private final BarterShops plugin;

    public ShopCreateSubCommand(BarterShops plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // A shop needs a bound container and a registered sign. Only the [barter] sign
        // placement (SignManager.onSignChange) sets those up, so this command refuses
        // instead of reporting a shop it did not save (#2462).
        sender.sendMessage(ChatColor.RED + "/shop create does not create shops.");
        sender.sendMessage(ChatColor.YELLOW + "To create a shop: place a chest, put a sign on or above it,");
        sender.sendMessage(ChatColor.YELLOW + "and write [barter] on line 1 of the sign.");
        return true;
    }

    @Override
    public String getDescription() {
        return "Explains how to create a shop (use a [barter] sign)";
    }

    @Override
    public String getUsage() {
        return "/shop create <name>";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.hasPermission("bartershops.admin");
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

    /** Worked examples served by {@code /shop help <verb>} (#1981). */
    @Override
    public java.util.List<String> getExamples() {
        return java.util.List.of(
                "/shop create does not create a shop; it only tells you how.",
                "  place a chest, attach a sign, write [barter] on line 1 of the sign",
                "Wall sign binds the container BEHIND it; a standing sign binds the one BELOW.");
    }
}
