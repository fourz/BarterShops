package org.fourz.BarterShops.command.sub;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.service.IShopGroupService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lists co-owners of a shop group.
 * Usage: /shop shared <groupId>
 * Console-friendly: Yes
 */
public class ShopSharedSubCommand implements SubCommand {

    private final BarterShops plugin;
    private final IShopGroupService groupService;

    public ShopSharedSubCommand(BarterShops plugin, IShopGroupService groupService) {
        this.plugin = plugin;
        this.groupService = groupService;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: " + getUsage());
            return true;
        }

        int groupId;
        try {
            groupId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid group ID: " + args[0]);
            return true;
        }

        groupService.getGroup(groupId).thenAccept(opt -> {
            if (opt.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "Group not found: " + groupId);
                return;
            }

            var group = opt.get();
            sender.sendMessage(ChatColor.GOLD + "===== Co-owners of \"" + group.groupName() + "\" =====");
            sender.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE +
                plugin.getPlayerLookup().getPlayerName(group.ownerUuid()));

            if (group.coOwners().isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "No co-owners. Use /shop share <groupId> <player> to add.");
            } else {
                for (int i = 0; i < group.coOwners().size(); i++) {
                    UUID co = group.coOwners().get(i);
                    sender.sendMessage(ChatColor.WHITE + "  " + (i + 1) + ". " +
                        plugin.getPlayerLookup().getPlayerName(co));
                }
            }
            sender.sendMessage(ChatColor.GOLD + "================================");
        }).exceptionally(ex -> {
            sender.sendMessage(ChatColor.RED + "Failed to load group co-owners.");
            return null;
        });

        return true;
    }

    @Override
    public String getDescription() {
        return "List co-owners of a shop group";
    }

    @Override
    public String getUsage() {
        return "/shop shared <groupId>";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.hasPermission("bartershops.admin");
    }

    @Override
    public String getPermission() {
        return "bartershops.group.info";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("<groupId>");
        }
        return completions;
    }

    @Override
    public boolean requiresPlayer() {
        return false;
    }

    /** Worked examples served by {@code /shop help <verb>} (#1981). */
    @Override
    public java.util.List<String> getExamples() {
        return java.util.List.of(
                "/shop shared 4",
                "  who group 4 is shared with");
    }
}
