package org.fourz.BarterShops.command.sub;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.service.IShopGroupService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Removes a co-owner from a shop group.
 * Usage: /shop unshare <groupId> <player>
 * Console-friendly: No (requires player sender)
 */
public class ShopUnshareSubCommand implements SubCommand {

    private final BarterShops plugin;
    private final IShopGroupService groupService;

    public ShopUnshareSubCommand(BarterShops plugin, IShopGroupService groupService) {
        this.plugin = plugin;
        this.groupService = groupService;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: " + getUsage());
            return true;
        }

        UUID requester;
        if (sender instanceof Player player) {
            requester = player.getUniqueId();
        } else {
            sender.sendMessage(ChatColor.RED + "This command requires a player sender.");
            return true;
        }

        int groupId;
        try {
            groupId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid group ID: " + args[0]);
            return true;
        }

        UUID coOwner = resolvePlayerUuid(args[1]);
        if (coOwner == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return true;
        }

        groupService.removeCoOwner(groupId, requester, coOwner).thenAccept(success -> {
            if (success) {
                sender.sendMessage(ChatColor.GREEN + "+ " +
                    plugin.getPlayerLookup().getPlayerName(coOwner) +
                    " removed as co-owner of group #" + groupId);
            } else {
                sender.sendMessage(ChatColor.RED + "x Failed to remove co-owner (not owner or invalid group).");
            }
        });

        return true;
    }

    @Override
    public String getDescription() {
        return "Remove a co-owner from a shop group";
    }

    @Override
    public String getUsage() {
        return "/shop unshare <groupId> <player>";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.hasPermission("bartershops.admin");
    }

    @Override
    public String getPermission() {
        return "bartershops.group.coowner";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("<groupId>");
        } else if (args.length == 2) {
            String partial = args[1].toLowerCase();
            plugin.getServer().getOnlinePlayers().forEach(p -> {
                if (p.getName().toLowerCase().startsWith(partial)) {
                    completions.add(p.getName());
                }
            });
        }
        return completions;
    }

    @SuppressWarnings("deprecation")
    private UUID resolvePlayerUuid(String name) {
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        org.bukkit.OfflinePlayer offline = plugin.getServer().getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline.getUniqueId() : null;
    }

    @Override
    public boolean requiresPlayer() {
        return true;
    }
}
