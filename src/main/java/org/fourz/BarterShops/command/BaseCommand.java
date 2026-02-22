package org.fourz.BarterShops.command;

import org.bukkit.command.CommandSender;

/**
 * @deprecated Use {@link SubCommand} instead. This interface will be removed in a future version.
 */
@Deprecated(forRemoval = true)
public interface BaseCommand {
    void execute(CommandSender sender, String[] args);
    String getPermission();
    String getDescription();
    String getUsage();
}
