package com.atomguard.command.impl;

import com.atomguard.AtomGuard;
import com.atomguard.command.SubCommand;
import com.atomguard.trust.TrustProfile;
import com.atomguard.trust.TrustScoreManager;
import com.atomguard.trust.TrustTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * /ag trust <oyuncu|set|reset|top> komutu.
 *
 * @author AtomGuard Team
 * @version 1.2.0
 */
public class TrustCommand implements SubCommand {

    private final AtomGuard plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public TrustCommand(AtomGuard plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getName() { return "trust"; }
    @Override public @NotNull String getDescriptionKey() { return "guven.komut-aciklama"; }
    @Override public @NotNull String getPermission() { return "atomguard.admin"; }
    @Override public @NotNull String getUsageKey() { return "guven.kullanim"; }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        TrustScoreManager tm = plugin.getTrustScoreManager();
        if (tm == null || !tm.isEnabled()) {
            sender.sendMessage(Component.text("Güven puanı sistemi devre dışı.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<red>Kullanım: /ag trust <oyuncu|set|reset|top>"));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "top" -> handleTop(sender, tm);
            case "set" -> handleSet(sender, args, tm);
            case "reset" -> handleReset(sender, args, tm);
            default -> handleInfo(sender, args[1], tm);
        }
    }

    private void handleInfo(CommandSender sender, String playerName, TrustScoreManager tm) {
        // Önce online oyuncularda ara
        UUID uuid = null;
        String name = playerName;

        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            uuid = online.getUniqueId();
            name = online.getName();
        } else {
            // Profil listesinde ara
            Optional<TrustProfile> found = tm.findByName(playerName);
            if (found.isPresent()) {
                uuid = found.get().getUuid();
                name = found.get().getLastKnownName();
            }
        }

        if (uuid == null) {
            sender.sendMessage(mm.deserialize("<red>Oyuncu bulunamadı: " + playerName));
            return;
        }

        TrustProfile profile = tm.getOrCreate(uuid);
        TrustTier tier = TrustTier.fromScore(profile.getTrustScore());
        String color = tier.getColor();

        sender.sendMessage(mm.deserialize("<aqua>━━━ Güven Profili: " + name + " ━━━"));
        sender.sendMessage(mm.deserialize("<gray>Puan: " + color + String.format("%.1f", profile.getTrustScore())
            + "/100 <dark_gray>(" + tier.getDisplayName() + ")"));

        if (profile.getFirstJoinTimestamp() > 0) {
            String date = new SimpleDateFormat("dd.MM.yyyy").format(new Date(profile.getFirstJoinTimestamp()));
            sender.sendMessage(mm.deserialize("<gray>İlk Giriş: <white>" + date));
        }

        int hours = profile.getTotalPlaytimeMinutes() / 60;
        int mins = profile.getTotalPlaytimeMinutes() % 60;
        sender.sendMessage(mm.deserialize("<gray>Toplam Süre: <white>" + hours + "s " + mins + "dk"));
        sender.sendMessage(mm.deserialize("<gray>Giriş Günleri: <white>" + profile.getUniqueLoginDays()));
        sender.sendMessage(mm.deserialize("<gray>Temiz Oturumlar: <white>" + profile.getConsecutiveCleanSessions()));
        sender.sendMessage(mm.deserialize("<gray>Toplam İhlal: <white>" + profile.getTotalViolations()
            + " <dark_gray>(son 24s: " + profile.getRecentViolations() + ")"));
        sender.sendMessage(mm.deserialize("<gray>Kick Sayısı: <white>" + profile.getKickCount()));
        sender.sendMessage(mm.deserialize("<aqua>━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    private void handleSet(CommandSender sender, String[] args, TrustScoreManager tm) {
        if (args.length < 4) {
            sender.sendMessage(mm.deserialize("<red>Kullanım: /ag trust set <oyuncu> <puan>"));
            return;
        }

        String playerName = args[2];
        double score;
        try {
            score = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(mm.deserialize("<red>Geçersiz puan: " + args[3]));
            return;
        }

        UUID uuid = resolveUUID(playerName, tm);
        if (uuid == null) {
            sender.sendMessage(mm.deserialize("<red>Oyuncu bulunamadı: " + playerName));
            return;
        }

        tm.setScore(uuid, playerName, score);
        sender.sendMessage(mm.deserialize("<green>✓ " + playerName + " oyuncusunun güven puanı "
            + String.format("%.1f", score) + " olarak ayarlandı."));
    }

    private void handleReset(CommandSender sender, String[] args, TrustScoreManager tm) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<red>Kullanım: /ag trust reset <oyuncu>"));
            return;
        }

        String playerName = args[2];
        UUID uuid = resolveUUID(playerName, tm);
        if (uuid == null) {
            sender.sendMessage(mm.deserialize("<red>Oyuncu bulunamadı: " + playerName));
            return;
        }

        tm.resetProfile(uuid, playerName);
        sender.sendMessage(mm.deserialize("<yellow>↻ " + playerName + " oyuncusunun güven puanı sıfırlandı."));
    }

    private void handleTop(CommandSender sender, TrustScoreManager tm) {
        List<Map.Entry<UUID, TrustProfile>> top = tm.getTopPlayers(10);
        sender.sendMessage(mm.deserialize("<aqua>━━━ En Güvenilir Oyuncular ━━━"));

        if (top.isEmpty()) {
            sender.sendMessage(mm.deserialize("<gray>Henüz kayıtlı oyuncu yok."));
            return;
        }

        for (int i = 0; i < top.size(); i++) {
            TrustProfile p = top.get(i).getValue();
            TrustTier tier = TrustTier.fromScore(p.getTrustScore());
            sender.sendMessage(mm.deserialize(
                "<gray>" + (i + 1) + ". " + tier.getColor() + p.getLastKnownName()
                + " <dark_gray>— <white>" + String.format("%.1f", p.getTrustScore())
                + "/100 <gray>(" + tier.getDisplayName() + ")"
            ));
        }

        sender.sendMessage(mm.deserialize("<aqua>━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    private UUID resolveUUID(String playerName, TrustScoreManager tm) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) return online.getUniqueId();
        return tm.findByName(playerName).map(TrustProfile::getUuid).orElse(null);
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return Arrays.asList("top", "set", "reset");
        if (args.length == 3 && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("reset"))) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
            return names;
        }
        return Collections.emptyList();
    }
}
