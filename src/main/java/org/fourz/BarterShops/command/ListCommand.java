package org.fourz.BarterShops.command;

import org.bukkit.command.CommandSender;
import org.fourz.BarterShops.Main;

public class ListCommand implements BaseCommand {
    private final Main plugin;

    public ListCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        sender.sendMessage("Listing all shops..."); // Implement actual shop listing logic
    }

    @Override
    public String getPermission() {
        return "bartershops.command.list";
    }

    @Override
    public String getDescription() {
        return "Lists all shops";
    }

    @Override
    public String getUsage() {
        return "/shop list";
    }
}
