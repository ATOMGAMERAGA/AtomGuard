package com.atomguard.command.impl;

import com.atomguard.AtomGuard;
import com.atomguard.command.SubCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HealthCommand implements SubCommand {
    private final AtomGuard plugin;

    public HealthCommand(AtomGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getName() { return "health"; }

    @Override
    public @NotNull String getDescriptionKey() { return "health.aciklama"; }

    @Override
    public @NotNull String getPermission() { return "atomguard.admin.health"; }

    @Override
    public @NotNull String getUsageKey() { return "health.kullanim"; }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        plugin.getMessageManager().sendMessage(sender, "health.baslik");

        // TPS
        double tps = plugin.getServer().getTPS()[0];
        NamedTextColor tpsColor = tps > 18 ? NamedTextColor.GREEN : (tps > 15 ? NamedTextColor.YELLOW : NamedTextColor.RED);
        sender.sendMessage(Component.text("  TPS: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.2f", tps), tpsColor)));

        // Memory
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;
        
        sender.sendMessage(Component.text("  Bellek: ", NamedTextColor.GRAY)
                .append(Component.text(usedMemory + "MB / " + maxMemory + "MB", NamedTextColor.WHITE)));

        // Modules
        int active = plugin.getModuleManager().getEnabledModuleCount();
        int total = plugin.getModuleManager().getTotalModuleCount();
        sender.sendMessage(Component.text("  Aktif Modüller: ", NamedTextColor.GRAY)
                .append(Component.text(active + "/" + total, NamedTextColor.WHITE)));

        // Attack Mode
        boolean attack = plugin.getAttackModeManager().isAttackMode();
        sender.sendMessage(Component.text("  Saldırı Modu: ", NamedTextColor.GRAY)
                .append(Component.text(attack ? "AKTIF" : "Normal", attack ? NamedTextColor.RED : NamedTextColor.GREEN)));

        // Storage
        sender.sendMessage(Component.text("  Veritabanı: ", NamedTextColor.GRAY)
                .append(Component.text("Bağlı", NamedTextColor.GREEN)));

        plugin.getMessageManager().sendMessage(sender, "health.alt-cizgi");
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
