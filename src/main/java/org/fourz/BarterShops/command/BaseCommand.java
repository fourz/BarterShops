package org.fourz.BarterShops.command;

import org.bukkit.command.CommandSender;

public interface BaseCommand {
    void execute(CommandSender sender, String[] args);
    String getPermission();
    String getDescription();
    String getUsage();
}
