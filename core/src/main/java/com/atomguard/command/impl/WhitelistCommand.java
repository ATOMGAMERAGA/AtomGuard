package com.atomguard.command.impl;

import com.atomguard.AtomGuard;
import com.atomguard.command.SubCommand;
import com.atomguard.manager.WhitelistManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /atomguard whitelist <add|remove|list> [oyuncu]
 *
 * <p>AtomGuard'ın tüm modüllerinden kalıcı muafiyet sağlayan whitelist.
 * Bukkit'in {@code atomguard.bypass} permission'ından farkı:
 * <ul>
 *   <li>Kalıcı (sunucu restart'larında korunur, JSON'da saklanır)</li>
 *   <li>İsim-bazlı (permission yönetimi gerekmez)</li>
 *   <li>Runtime'da admin tarafından yönetilir</li>
 *   <li>{@code AbstractModule.isExempt()} ile her modülde otomatik geçerli</li>
 * </ul>
 *
 * @author AtomGuard Team
 * @version 2.3.0
 */
public class WhitelistCommand implements SubCommand {

    private final AtomGuard plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public WhitelistCommand(AtomGuard plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getName() { return "whitelist"; }
    @Override public @NotNull String getDescriptionKey() { return "whitelist.komut-aciklama"; }
    @Override public @NotNull String getPermission() { return "atomguard.admin"; }
    @Override public @NotNull String getUsageKey() { return "whitelist.kullanim"; }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        WhitelistManager manager = plugin.getWhitelistManager();
        if (manager == null) {
            sender.sendMessage(mm.deserialize("<red>Whitelist manager mevcut değil."));
            return;
        }

        if (args.length < 2) {
            usage(sender);
            return;
        }

        switch (args[1].toLowerCase()) {
            case "add"    -> handleAdd(sender, args, manager);
            case "remove", "rm", "del" -> handleRemove(sender, args, manager);
            case "list", "ls" -> handleList(sender, manager);
            default -> usage(sender);
        }
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(mm.deserialize("<aqua>━━━ AtomGuard Whitelist ━━━"));
        sender.sendMessage(mm.deserialize("<gray>/atomguard whitelist add <oyuncu>"));
        sender.sendMessage(mm.deserialize("<gray>/atomguard whitelist remove <oyuncu>"));
        sender.sendMessage(mm.deserialize("<gray>/atomguard whitelist list"));
        sender.sendMessage(mm.deserialize("<aqua>━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    private void handleAdd(CommandSender sender, String[] args, WhitelistManager manager) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<red>Kullanım: /atomguard whitelist add <oyuncu>"));
            return;
        }
        String name = args[2];
        OfflinePlayer player = resolvePlayer(name);
        if (player == null || player.getUniqueId() == null) {
            sender.sendMessage(mm.deserialize("<red>Oyuncu bulunamadı: " + name));
            return;
        }
        UUID uuid = player.getUniqueId();
        String resolvedName = player.getName() != null ? player.getName() : name;
        boolean added = manager.add(uuid, resolvedName);
        if (added) {
            sender.sendMessage(mm.deserialize("<green>✓ " + resolvedName
                    + " AtomGuard whitelist'ine eklendi (UUID: " + uuid + ")"));
            plugin.getLogManager().info("[Whitelist] " + sender.getName() + " "
                    + resolvedName + " (" + uuid + ") oyuncusunu whitelist'e ekledi.");
        } else {
            sender.sendMessage(mm.deserialize("<yellow>! " + resolvedName + " zaten whitelist'te."));
        }
    }

    private void handleRemove(CommandSender sender, String[] args, WhitelistManager manager) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<red>Kullanım: /atomguard whitelist remove <oyuncu>"));
            return;
        }
        String name = args[2];
        // Önce UUID ile dene (online/offline)
        OfflinePlayer player = resolvePlayer(name);
        boolean removed = false;
        if (player != null && player.getUniqueId() != null) {
            removed = manager.remove(player.getUniqueId());
        }
        if (!removed) {
            // Sonra isim ile dene (legacy / display-name match)
            removed = manager.removeByName(name);
        }
        if (removed) {
            sender.sendMessage(mm.deserialize("<green>✓ " + name + " whitelist'ten çıkarıldı."));
            plugin.getLogManager().info("[Whitelist] " + sender.getName() + " "
                    + name + " oyuncusunu whitelist'ten çıkardı.");
        } else {
            sender.sendMessage(mm.deserialize("<red>" + name + " whitelist'te değil."));
        }
    }

    private void handleList(CommandSender sender, WhitelistManager manager) {
        Map<UUID, String> all = manager.getAll();
        sender.sendMessage(mm.deserialize("<aqua>━━━ AtomGuard Whitelist (" + all.size() + ") ━━━"));
        if (all.isEmpty()) {
            sender.sendMessage(mm.deserialize("<gray>Henüz oyuncu eklenmemiş."));
        } else {
            int i = 1;
            for (Map.Entry<UUID, String> e : all.entrySet()) {
                sender.sendMessage(mm.deserialize("<gray>" + i + ". <white>" + e.getValue()
                        + " <dark_gray>(" + e.getKey() + ")"));
                i++;
            }
        }
        sender.sendMessage(mm.deserialize("<aqua>━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) return offline;
        // Hiç görmediyse de UUID üretmek için döndür (UUID yine de geçerli olur)
        return offline;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) {
            return Arrays.asList("add", "remove", "list");
        }
        if (args.length == 3) {
            String sub = args[1].toLowerCase();
            if (sub.equals("add")) {
                List<String> names = new ArrayList<>();
                Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                return names;
            }
            if (sub.equals("remove") || sub.equals("rm") || sub.equals("del")) {
                WhitelistManager m = plugin.getWhitelistManager();
                if (m == null) return Collections.emptyList();
                return new ArrayList<>(m.getAll().values());
            }
        }
        return Collections.emptyList();
    }
}
