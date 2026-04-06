package org.fourz.BarterShops.command.sub;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.dto.ShopGroupDTO;
import org.fourz.BarterShops.service.IShopGroupService;
import org.fourz.BarterShops.util.TableDisplay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Dispatcher subcommand for /shop group <action>.
 * Handles: list, info, rename, add, remove, transfer, create, delete
 * Console-friendly for list, info, add, remove actions.
 */
public class ShopGroupSubCommand implements SubCommand {

    private final BarterShops plugin;
    private final IShopGroupService groupService;

    // Owner omitted — already shown in the group header above the shop list.
    private static final TableDisplay<ShopDataDTO> SHOP_TABLE = TableDisplay.<ShopDataDTO>builder()
            .column("#",        ChatColor.GRAY,      s -> String.valueOf(s.shopId()))
            .column("TYPE",     ChatColor.YELLOW,    s -> s.shopType() != null ? s.shopType().name() : "BARTER")
            .column("Offering", ChatColor.GREEN,     s -> formatOffering(s.metadata()))
            .column("Location", ChatColor.DARK_GRAY, ShopGroupSubCommand::coords)
            .build();

    public ShopGroupSubCommand(BarterShops plugin, IShopGroupService groupService) {
        this.plugin = plugin;
        this.groupService = groupService;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: " + getUsage());
            showSubActions(sender);
            return true;
        }

        String action = args[0].toLowerCase();
        String[] actionArgs = Arrays.copyOfRange(args, 1, args.length);

        return switch (action) {
            case "list" -> handleList(sender, actionArgs);
            case "info" -> handleInfo(sender, actionArgs);
            case "rename" -> handleRename(sender, actionArgs);
            case "add" -> handleAdd(sender, actionArgs);
            case "remove" -> handleRemove(sender, actionArgs);
            case "transfer" -> handleTransfer(sender, actionArgs);
            case "create" -> handleCreate(sender, actionArgs);
            case "delete" -> handleDelete(sender, actionArgs);
            case "coowner" -> handleCoOwner(sender, actionArgs);
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown group action: " + action);
                showSubActions(sender);
                yield true;
            }
        };
    }

    private boolean handleList(CommandSender sender, String[] args) {
        UUID targetUuid;
        if (args.length > 0 && sender.hasPermission("bartershops.admin")) {
            // Admin: lookup another player
            targetUuid = resolvePlayer(sender, args[0]);
            if (targetUuid == null) return true;
        } else if (sender instanceof Player player) {
            targetUuid = player.getUniqueId();
        } else {
            sender.sendMessage(ChatColor.RED + "Console must specify a player: /shop group list <player>");
            return true;
        }

        groupService.getPlayerGroups(targetUuid).thenAccept(groups -> {
            if (groups.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "No shop groups found.");
                return;
            }

            String playerName = plugin.getPlayerLookup().getPlayerName(targetUuid);
            sender.sendMessage(ChatColor.GOLD + "===== Shop Groups for " + playerName + " =====");

            for (ShopGroupDTO group : groups) {
                String role = group.ownerUuid().equals(targetUuid) ? "owner" : "co-owner";
                // Count shops in group async
                plugin.getShopGroupRepository().getShopsInGroup(group.groupId()).thenAccept(shops -> {
                    sender.sendMessage(ChatColor.YELLOW + " #" + group.groupId() + " " +
                        ChatColor.WHITE + group.groupName() +
                        ChatColor.GRAY + " (" + shops.size() + " shops, " + role + ", " + group.world() + ")");
                });
            }
        }).exceptionally(ex -> {
            sender.sendMessage(ChatColor.RED + "Failed to load groups.");
            return null;
        });

        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /shop group info <groupId>");
            return true;
        }

        int groupId = parseGroupId(sender, args[0]);
        if (groupId < 0) return true;

        groupService.getGroup(groupId).thenAccept(opt -> {
            if (opt.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "Group not found: " + groupId);
                return;
            }

            ShopGroupDTO group = opt.get();
            sender.sendMessage(ChatColor.GOLD + "===== Group #" + group.groupId() + " =====");
            sender.sendMessage(ChatColor.YELLOW + "Name: " + ChatColor.WHITE + group.groupName());
            sender.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE +
                plugin.getPlayerLookup().getPlayerName(group.ownerUuid()));
            sender.sendMessage(ChatColor.YELLOW + "World: " + ChatColor.WHITE + group.world());

            if (!group.coOwners().isEmpty()) {
                StringBuilder coOwnerStr = new StringBuilder();
                for (UUID co : group.coOwners()) {
                    if (!coOwnerStr.isEmpty()) coOwnerStr.append(", ");
                    coOwnerStr.append(plugin.getPlayerLookup().getPlayerName(co));
                }
                sender.sendMessage(ChatColor.YELLOW + "Co-owners: " + ChatColor.WHITE + coOwnerStr);
            }

            // Show shops in group
            plugin.getShopGroupRepository().getShopsInGroup(groupId).thenAccept(shops -> {
                sender.sendMessage(ChatColor.YELLOW + "Shops (" + shops.size() + "):");
                SHOP_TABLE.renderHeader(sender);
                SHOP_TABLE.render(sender, shops);
            });
        }).exceptionally(ex -> {
            sender.sendMessage(ChatColor.RED + "Failed to load group info.");
            return null;
        });

        return true;
    }

    private boolean handleRename(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /shop group rename <groupId> <name...>");
            return true;
        }

        int groupId = parseGroupId(sender, args[0]);
        if (groupId < 0) return true;

        String newName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (newName.length() > 64) {
            sender.sendMessage(ChatColor.RED + "Group name too long (max 64 characters).");
            return true;
        }

        groupService.renameGroup(groupId, player.getUniqueId(), newName).thenAccept(success -> {
            if (success) {
                sender.sendMessage(ChatColor.GREEN + "+ Group renamed to: " + newName);
            } else {
                sender.sendMessage(ChatColor.RED + "x Failed to rename group (not found or not owner).");
            }
        });

        return true;
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /shop group add <shopId> <groupId>");
            return true;
        }

        UUID requester = getRequesterUuid(sender);
        if (requester == null) return true;

        int shopId;
        int groupId;
        try {
            shopId = Integer.parseInt(args[0]);
            groupId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid ID. Usage: /shop group add <shopId> <groupId>");
            return true;
        }

        groupService.addShopToGroup(shopId, groupId, requester).thenAccept(success -> {
            if (success) {
                sender.sendMessage(ChatColor.GREEN + "+ Shop #" + shopId + " added to group #" + groupId);
            } else {
                sender.sendMessage(ChatColor.RED + "x Failed to add shop to group (not found or not owner).");
            }
        });

        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /shop group remove <shopId>");
            return true;
        }

        UUID requester = getRequesterUuid(sender);
        if (requester == null) return true;

        int shopId;
        try {
            shopId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid shop ID.");
            return true;
        }

        groupService.removeShopFromGroup(shopId, requester).thenAccept(success -> {
            if (success) {
                sender.sendMessage(ChatColor.GREEN + "+ Shop #" + shopId + " removed from its group.");
            } else {
                sender.sendMessage(ChatColor.RED + "x Failed to remove shop from group.");
            }
        });

        return true;
    }

    private boolean handleTransfer(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /shop group transfer <groupId> <player>");
            return true;
        }

        int groupId = parseGroupId(sender, args[0]);
        if (groupId < 0) return true;

        UUID newOwner = resolvePlayer(sender, args[1]);
        if (newOwner == null) return true;

        groupService.transferGroupOwnership(groupId, player.getUniqueId(), newOwner).thenAccept(success -> {
            if (success) {
                sender.sendMessage(ChatColor.GREEN + "+ Group ownership transferred to " +
                    plugin.getPlayerLookup().getPlayerName(newOwner));
            } else {
                sender.sendMessage(ChatColor.RED + "x Failed to transfer group ownership.");
            }
        });

        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /shop group create <name...>");
            return true;
        }

        String name = String.join(" ", args);
        if (name.length() > 64) {
            sender.sendMessage(ChatColor.RED + "Group name too long (max 64 characters).");
            return true;
        }

        String world = player.getWorld().getName();
        groupService.createGroup(player.getUniqueId(), name, world).thenAccept(group -> {
            sender.sendMessage(ChatColor.GREEN + "+ Created group #" + group.groupId() + ": " + group.groupName());
        }).exceptionally(ex -> {
            sender.sendMessage(ChatColor.RED + "x Failed to create group.");
            return null;
        });

        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /shop group delete <groupId>");
            return true;
        }

        int groupId = parseGroupId(sender, args[0]);
        if (groupId < 0) return true;

        groupService.deleteGroup(groupId, player.getUniqueId()).thenAccept(success -> {
            if (success) {
                sender.sendMessage(ChatColor.GREEN + "+ Group #" + groupId + " deleted. Shops ungrouped.");
            } else {
                sender.sendMessage(ChatColor.RED + "x Failed to delete group (not found or not owner).");
            }
        });

        return true;
    }

    // ========================================================
    // Helpers
    // ========================================================

    private boolean handleCoOwner(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /shop group coowner <add|remove> <groupId> <player>");
            return true;
        }

        String subAction = args[0].toLowerCase();
        int groupId = parseGroupId(sender, args[1]);
        if (groupId < 0) return true;

        UUID target = resolvePlayer(sender, args[2]);
        if (target == null) return true;

        UUID requester = getRequesterUuid(sender);
        if (requester == null) return true;

        if (subAction.equals("add")) {
            groupService.addCoOwner(groupId, requester, target).thenAccept(success -> {
                if (success) {
                    sender.sendMessage(ChatColor.GREEN + "+ " + plugin.getPlayerLookup().getPlayerName(target)
                            + " added as co-owner of group #" + groupId);
                } else {
                    sender.sendMessage(ChatColor.RED + "x Failed (not group owner, or group not found).");
                }
            });
        } else if (subAction.equals("remove")) {
            groupService.removeCoOwner(groupId, requester, target).thenAccept(success -> {
                if (success) {
                    sender.sendMessage(ChatColor.GREEN + "+ " + plugin.getPlayerLookup().getPlayerName(target)
                            + " removed as co-owner of group #" + groupId);
                } else {
                    sender.sendMessage(ChatColor.RED + "x Failed (not group owner, or group not found).");
                }
            });
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /shop group coowner <add|remove> <groupId> <player>");
        }

        return true;
    }

    private void showSubActions(CommandSender sender) {
        sender.sendMessage(ChatColor.GRAY + "Actions: list, info, rename, add, remove, transfer, create, delete, coowner");
    }

    private int parseGroupId(CommandSender sender, String arg) {
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid group ID: " + arg);
            return -1;
        }
    }

    private UUID resolvePlayer(CommandSender sender, String name) {
        UUID uuid = resolvePlayerUuid(name);
        if (uuid == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + name);
        }
        return uuid;
    }

    @SuppressWarnings("deprecation")
    private UUID resolvePlayerUuid(String name) {
        // Check online players first
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) return online.getUniqueId();

        // Fall back to offline player lookup
        org.bukkit.OfflinePlayer offline = plugin.getServer().getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline.getUniqueId() : null;
    }

    private UUID getRequesterUuid(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getUniqueId();
        }
        // Console acts with admin authority — use a sentinel UUID
        // But for ownership checks, we need the actual admin override in service
        sender.sendMessage(ChatColor.RED + "This action requires a player sender.");
        return null;
    }

    @Override
    public String getDescription() {
        return "Manage shop groups";
    }

    @Override
    public String getUsage() {
        return "/shop group <list|info|rename|add|remove|transfer|create|delete>";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("bartershops.group.list") ||
               sender.hasPermission("bartershops.group.info") ||
               sender.hasPermission("bartershops.group.manage") ||
               sender.hasPermission("bartershops.admin") ||
               sender.isOp();
    }

    @Override
    public String getPermission() {
        return "bartershops.group.list";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String action : List.of("list", "info", "rename", "add", "remove", "transfer", "create", "delete", "coowner")) {
                if (action.startsWith(partial)) {
                    completions.add(action);
                }
            }
        } else if (args.length == 2) {
            String action = args[0].toLowerCase();
            if (List.of("info", "rename", "delete", "transfer", "coowner").contains(action)) {
                completions.add("<groupId>");
            } else if ("add".equals(action)) {
                completions.add("<shopId>");
            } else if ("remove".equals(action)) {
                completions.add("<shopId>");
            } else if ("list".equals(action) && sender.hasPermission("bartershops.admin")) {
                plugin.getServer().getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            }
        } else if (args.length == 3) {
            String action = args[0].toLowerCase();
            if ("add".equals(action)) {
                completions.add("<groupId>");
            } else if (List.of("transfer", "coowner").contains(action)) {
                plugin.getServer().getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            }
        } else if (args.length == 4 && "coowner".equals(args[0].toLowerCase())) {
            plugin.getServer().getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        }

        return completions;
    }

    @Override
    public boolean requiresPlayer() {
        return false; // Console-friendly for list, info, add, remove
    }

    private static String coords(ShopDataDTO s) {
        return s.locationWorld() != null
                ? String.format("%d,%d,%d", (int) s.locationX(), (int) s.locationY(), (int) s.locationZ())
                : "N/A";
    }

    private static String formatOffering(java.util.Map<String, String> metadata) {
        if (metadata == null) return "";
        String json = metadata.get("shop_config_offering");
        if (json == null || json.isEmpty()) return "";
        try {
            String type = json.replaceAll(".*\"type\":\\s*\"([^\"]+)\".*", "$1");
            String amount = json.replaceAll(".*\"amount\":\\s*(\\d+).*", "$1");
            if (type.equals(json)) return "";
            String[] words = type.toLowerCase().split("_");
            StringBuilder name = new StringBuilder();
            for (String w : words) {
                if (!w.isEmpty()) {
                    if (!name.isEmpty()) name.append(' ');
                    name.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
                }
            }
            return name + " x" + (amount.equals(json) ? "?" : amount);
        } catch (Exception e) {
            return "";
        }
    }
}
