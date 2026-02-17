package com.atomguard.command;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import java.util.List;

/**
 * Alt komut arayüzü
 */
public interface SubCommand {
    @NotNull String getName();
    @NotNull String getDescriptionKey();
    @NotNull String getPermission();
    @NotNull String getUsageKey();
    
    void execute(@NotNull CommandSender sender, @NotNull String[] args);
    @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args);
}
