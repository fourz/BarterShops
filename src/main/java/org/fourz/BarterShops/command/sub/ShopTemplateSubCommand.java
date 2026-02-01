package org.fourz.BarterShops.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.BarterShops.template.ShopTemplate;
import org.fourz.BarterShops.template.TemplateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Subcommand for template management.
 * Usage: /shop template <save|load|list|delete|info> [args...]
 * Console-friendly for most operations.
 */
public class ShopTemplateSubCommand implements SubCommand {

    private final BarterShops plugin;
    private final TemplateManager templateManager;

    public ShopTemplateSubCommand(BarterShops plugin) {
        this.plugin = plugin;
        this.templateManager = plugin.getTemplateManager();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "save":
                return handleSave(sender, args);
            case "load":
                return handleLoad(sender, args);
            case "list":
                return handleList(sender, args);
            case "delete":
            case "remove":
                return handleDelete(sender, args);
            case "info":
                return handleInfo(sender, args);
            default:
                sender.sendMessage(ChatColor.RED + "Unknown template action: " + action);
                showUsage(sender);
                return true;
        }
    }

    /**
     * Handles template save command.
     * Usage: /shop template save <name> [description] [category] [tags]
     */
    private boolean handleSave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can save templates from shops.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /shop template save <name> [description]");
            return true;
        }

        Player player = (Player) sender;
        String templateName = args[1];

        // Check if template name already exists for this player
        if (templateManager.getTemplateByName(templateName) != null) {
            sender.sendMessage(ChatColor.RED + "✖ A template with that name already exists!");
            return true;
        }

        // Find the shop the player is looking at (simplified - in real implementation
        // would check player target block or require shop ID)
        // For now, we'll just create a basic template

        sender.sendMessage(ChatColor.YELLOW + "⚙ Creating template: " + templateName);

        String description = args.length > 2 ? args[2] : "";
        String category = args.length > 3 ? args[3] : "general";
        String tags = args.length > 4 ? args[4] : "";

        // Create a basic template (would normally copy from actual shop)
        ShopTemplate template = ShopTemplate.builder()
            .id(UUID.randomUUID().toString())
            .name(templateName)
            .owner(player.getUniqueId())
            .signType(org.fourz.BarterShops.sign.SignType.BARTER)
            .description(description)
            .category(category)
            .tags(tags)
            .createdAt(java.time.LocalDateTime.now())
            .isServerPreset(false)
            .build();

        if (templateManager.saveTemplate(template)) {
            sender.sendMessage(ChatColor.GREEN + "✓ Template saved: " + templateName);
            sender.sendMessage(ChatColor.GRAY + "   Use '/shop template load " + templateName + "' to apply it");
        } else {
            sender.sendMessage(ChatColor.RED + "✖ Failed to save template!");
        }

        return true;
    }

    /**
     * Handles template load command.
     * Usage: /shop template load <name>
     */
    private boolean handleLoad(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can load templates to create shops.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /shop template load <name>");
            return true;
        }

        String templateName = args[1];
        ShopTemplate template = templateManager.getTemplateByName(templateName);

        if (template == null) {
            sender.sendMessage(ChatColor.RED + "✖ Template not found: " + templateName);
            return true;
        }

        Player player = (Player) sender;

        // Check permissions - player can load own templates or server presets
        if (!template.isServerPreset() && !template.isOwnedBy(player.getUniqueId())) {
            if (!sender.hasPermission("bartershops.template.load.other")) {
                sender.sendMessage(ChatColor.RED + "✖ You don't have permission to load other players' templates!");
                return true;
            }
        }

        sender.sendMessage(ChatColor.GREEN + "✓ Loaded template: " + template.name());
        sender.sendMessage(ChatColor.GRAY + "   Type: " + template.signType());
        sender.sendMessage(ChatColor.GRAY + "   Category: " + template.category());
        if (!template.description().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "   " + template.description());
        }
        sender.sendMessage(ChatColor.YELLOW + "⚠ Place a sign to create a shop with this template");

        return true;
    }

    /**
     * Handles template list command.
     * Usage: /shop template list [player|category:<name>|tags:<tags>]
     */
    private boolean handleList(CommandSender sender, String[] args) {
        List<ShopTemplate> templates;
        String filterDesc = "all templates";

        if (args.length > 1) {
            String filter = args[1];

            if (filter.startsWith("category:")) {
                String category = filter.substring(9);
                templates = templateManager.getTemplatesByCategory(category);
                filterDesc = "templates in category '" + category + "'";
            } else if (filter.startsWith("tags:")) {
                String tags = filter.substring(5);
                templates = templateManager.getTemplatesByTags(tags);
                filterDesc = "templates with tags '" + tags + "'";
            } else {
                // Try to find player
                OfflinePlayer target = Bukkit.getOfflinePlayer(filter);
                if (target.hasPlayedBefore() || target.isOnline()) {
                    templates = templateManager.getTemplatesByOwner(target.getUniqueId());
                    filterDesc = "templates by " + target.getName();
                } else {
                    sender.sendMessage(ChatColor.RED + "✖ Player not found: " + filter);
                    return true;
                }
            }
        } else if (sender instanceof Player) {
            // Show player's own templates by default
            templates = templateManager.getTemplatesByOwner(((Player) sender).getUniqueId());
            filterDesc = "your templates";
        } else {
            // Console shows server presets by default
            templates = templateManager.getServerPresets();
            filterDesc = "server preset templates";
        }

        if (templates.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No " + filterDesc + " found.");
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + "=== Shop Templates (" + filterDesc + ") ===");
        sender.sendMessage(ChatColor.GRAY + String.format("%-20s %-12s %-15s %-10s",
                "Name", "Type", "Category", "Owner"));
        sender.sendMessage(ChatColor.GRAY + "-----------------------------------------------------------");

        for (ShopTemplate template : templates) {
            String ownerName;
            if (template.isServerPreset()) {
                ownerName = "SERVER";
            } else if (template.owner() != null) {
                OfflinePlayer owner = Bukkit.getOfflinePlayer(template.owner());
                ownerName = owner.getName() != null ? owner.getName() : "Unknown";
            } else {
                ownerName = "Unknown";
            }

            String row = String.format("%-20s %-12s %-15s %-10s",
                    truncate(template.name(), 20),
                    template.signType(),
                    truncate(template.category(), 15),
                    truncate(ownerName, 10));

            sender.sendMessage(ChatColor.WHITE + row);
        }

        sender.sendMessage(ChatColor.GRAY + "-----------------------------------------------------------");
        sender.sendMessage(ChatColor.GREEN + "Total: " + ChatColor.WHITE + templates.size());
        sender.sendMessage(ChatColor.GRAY + "Use '/shop template info <name>' for details");

        return true;
    }

    /**
     * Handles template delete command.
     * Usage: /shop template delete <name>
     */
    private boolean handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /shop template delete <name>");
            return true;
        }

        String templateName = args[1];
        ShopTemplate template = templateManager.getTemplateByName(templateName);

        if (template == null) {
            sender.sendMessage(ChatColor.RED + "✖ Template not found: " + templateName);
            return true;
        }

        // Permission check
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!template.isOwnedBy(player.getUniqueId()) && !sender.hasPermission("bartershops.template.delete.other")) {
                sender.sendMessage(ChatColor.RED + "✖ You don't have permission to delete other players' templates!");
                return true;
            }
        }

        if (templateManager.deleteTemplate(template.id())) {
            sender.sendMessage(ChatColor.GREEN + "✓ Template deleted: " + templateName);
        } else {
            sender.sendMessage(ChatColor.RED + "✖ Failed to delete template!");
        }

        return true;
    }

    /**
     * Handles template info command.
     * Usage: /shop template info <name>
     */
    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /shop template info <name>");
            return true;
        }

        String templateName = args[1];
        ShopTemplate template = templateManager.getTemplateByName(templateName);

        if (template == null) {
            sender.sendMessage(ChatColor.RED + "✖ Template not found: " + templateName);
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + "=== Template: " + template.name() + " ===");
        sender.sendMessage(ChatColor.GRAY + "ID: " + ChatColor.WHITE + template.id());
        sender.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.WHITE + template.signType());
        sender.sendMessage(ChatColor.GRAY + "Category: " + ChatColor.WHITE + template.category());

        if (template.isServerPreset()) {
            sender.sendMessage(ChatColor.GRAY + "Owner: " + ChatColor.GOLD + "SERVER PRESET");
        } else if (template.owner() != null) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(template.owner());
            String ownerName = owner.getName() != null ? owner.getName() : "Unknown";
            sender.sendMessage(ChatColor.GRAY + "Owner: " + ChatColor.WHITE + ownerName);
        }

        if (!template.description().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Description: " + ChatColor.WHITE + template.description());
        }

        if (!template.tags().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Tags: " + ChatColor.WHITE + template.tags());
        }

        sender.sendMessage(ChatColor.GRAY + "Created: " + ChatColor.WHITE +
                template.createdAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        return true;
    }

    /**
     * Shows usage information.
     */
    private void showUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Shop Template Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/shop template save <name> [description]" +
                ChatColor.WHITE + " - Save shop as template");
        sender.sendMessage(ChatColor.YELLOW + "/shop template load <name>" +
                ChatColor.WHITE + " - Load template for new shop");
        sender.sendMessage(ChatColor.YELLOW + "/shop template list [filter]" +
                ChatColor.WHITE + " - List templates");
        sender.sendMessage(ChatColor.YELLOW + "/shop template info <name>" +
                ChatColor.WHITE + " - View template details");
        sender.sendMessage(ChatColor.YELLOW + "/shop template delete <name>" +
                ChatColor.WHITE + " - Delete template");
        sender.sendMessage(ChatColor.GRAY + "Filters: player name, category:<name>, tags:<tags>");
    }

    /**
     * Truncates a string to max length.
     */
    private String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    @Override
    public String getDescription() {
        return "Manage shop templates";
    }

    @Override
    public String getUsage() {
        return "/shop template <save|load|list|delete|info> [args...]";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.isOp();
    }

    @Override
    public String getPermission() {
        return "bartershops.template";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Action completions
            String partial = args[0].toLowerCase();
            List<String> actions = List.of("save", "load", "list", "delete", "info");
            completions.addAll(actions.stream()
                    .filter(a -> a.startsWith(partial))
                    .collect(Collectors.toList()));
        } else if (args.length == 2) {
            String action = args[0].toLowerCase();
            String partial = args[1].toLowerCase();

            if (action.equals("load") || action.equals("delete") || action.equals("info")) {
                // Template name completions
                List<ShopTemplate> templates;
                if (sender instanceof Player) {
                    templates = new ArrayList<>(templateManager.getTemplatesByOwner(((Player) sender).getUniqueId()));
                    templates.addAll(templateManager.getServerPresets());
                } else {
                    templates = new ArrayList<>(templateManager.getAllTemplates());
                }

                completions.addAll(templates.stream()
                        .map(ShopTemplate::name)
                        .filter(name -> name.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList()));
            } else if (action.equals("list")) {
                // Filter completions
                if ("category:".startsWith(partial)) {
                    completions.add("category:");
                }
                if ("tags:".startsWith(partial)) {
                    completions.add("tags:");
                }
                // Add online players
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(partial))
                        .forEach(completions::add);
            }
        }

        return completions;
    }

    @Override
    public boolean requiresPlayer() {
        return false; // Console can list/view/delete templates
    }
}
