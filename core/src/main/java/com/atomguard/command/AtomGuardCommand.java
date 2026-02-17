package com.atomguard.command;

import com.atomguard.AtomGuard;
import com.atomguard.command.impl.HealthCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Modern SubCommand architecture for AtomGuard v4.0.0
 */
public class AtomGuardCommand implements CommandExecutor, TabCompleter {

    private final AtomGuard plugin;
    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public AtomGuardCommand(@NotNull AtomGuard plugin) {
        this.plugin = plugin;
        registerSubCommands();
    }

    private void registerSubCommands() {
        register(new HealthCommand(plugin));
        // Other subcommands would be registered here in a full migration
        // For this v4.0.0 release, we keep the main logic here but allow SubCommands to hook in
    }

    private void register(SubCommand cmd) {
        subCommands.put(cmd.getName().toLowerCase(), cmd);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("atomguard.admin")) {
            plugin.getMessageManager().sendMessage(sender, "genel.izin-yok");
            return true;
        }

        if (args.length == 0) {
            showInfo(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        SubCommand cmd = subCommands.get(sub);

        if (cmd != null) {
            if (!sender.hasPermission(cmd.getPermission())) {
                plugin.getMessageManager().sendMessage(sender, "genel.izin-yok");
                return true;
            }
            cmd.execute(sender, args);
            return true;
        }

        // Legacy fallbacks for main logic (will be moved to classes in v4.1)
        switch (sub) {
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            case "toggle" -> handleToggle(sender, args);
            case "stats" -> handleStats(sender);
            case "info" -> showInfo(sender);
            default -> plugin.getMessageManager().sendMessage(sender, "genel.bilinmeyen-komut");
        }

        return true;
    }

    // --- Original methods preserved but using improved i18n ---

    private void handleReload(CommandSender sender) {
        long start = System.currentTimeMillis();
        plugin.getConfigManager().reload();
        plugin.getMessageManager().clearCache();
        plugin.getModuleManager().reloadModules();
        long duration = System.currentTimeMillis() - start;

        plugin.getMessageManager().sendPrefixedMessage(sender, "genel.yeniden-yuklendi");
        plugin.getMessageManager().sendMessage(sender, "genel.yukleme-suresi", Map.of("sure", String.valueOf(duration)));
    }

    private void handleStatus(CommandSender sender) {
        plugin.getMessageManager().sendMessage(sender, "durum.baslik");
        plugin.getModuleManager().getAllModules().forEach(m -> {
            String statusKey = m.isEnabled() ? "durum.acik" : "durum.kapali";
            var component = plugin.getMessageManager().parse("  <gray>• <white>" + m.getName() + ": ")
                    .append(plugin.getMessageManager().getMessage(statusKey));
            
            if (m.getBlockedCount() > 0) {
                component = component.append(plugin.getMessageManager().getMessage("durum.engelleme-sayisi", Map.of("sayi", String.valueOf(m.getBlockedCount()))));
            }
            sender.sendMessage(component);
        });
        plugin.getMessageManager().sendMessage(sender, "durum.istatistik", Map.of("sayi", String.valueOf(plugin.getModuleManager().getTotalBlockedCount())));
        plugin.getMessageManager().sendMessage(sender, "durum.alt-bilgi");
    }

    private void handleToggle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessageManager().sendMessage(sender, "toggle.kullanim");
            return;
        }
        String mod = args[1];
        if (!plugin.getModuleManager().hasModule(mod)) {
            plugin.getMessageManager().sendMessage(sender, "genel.modul-bulunamadi", Map.of("modul", mod));
            return;
        }
        boolean state = plugin.getModuleManager().toggleModule(mod);
        plugin.getMessageManager().sendPrefixedMessage(sender, state ? "genel.modul-acildi" : "genel.modul-kapandi", Map.of("modul", mod));
    }

    private void handleStats(CommandSender sender) {
        var stats = plugin.getStatisticsManager();
        if (stats == null || !stats.isEnabled()) {
            plugin.getMessageManager().sendMessage(sender, "istatistik.devre-disi");
            return;
        }
        plugin.getMessageManager().sendMessage(sender, "istatistik.baslik");
        plugin.getMessageManager().sendMessage(sender, "istatistik.toplam-tumu", Map.of("sayi", String.valueOf(stats.getTotalBlockedAllTime())));
        plugin.getMessageManager().sendMessage(sender, "istatistik.alt-cizgi");
    }

    private void showInfo(CommandSender sender) {
        plugin.getMessageManager().sendMessage(sender, "info.baslik");
        plugin.getMessageManager().sendMessage(sender, "info.versiyon", Map.of("versiyon", plugin.getDescription().getVersion()));
        plugin.getMessageManager().sendMessage(sender, "info.aktif-modul", Map.of(
            "aktif", String.valueOf(plugin.getModuleManager().getEnabledModuleCount()),
            "toplam", String.valueOf(plugin.getModuleManager().getTotalModuleCount())
        ));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(List.of("reload", "status", "toggle", "stats", "info", "health"));
            return completions.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
            return plugin.getModuleManager().getModuleNames().stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
