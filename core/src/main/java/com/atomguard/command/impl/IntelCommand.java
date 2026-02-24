package com.atomguard.command.impl;

import com.atomguard.AtomGuard;
import com.atomguard.command.SubCommand;
import com.atomguard.intelligence.IntelligenceAlert;
import com.atomguard.intelligence.ThreatLevel;
import com.atomguard.intelligence.TrafficIntelligenceEngine;
import com.atomguard.intelligence.TrafficProfile;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * /ag intel <status|reset> komutu — Tehdit istihbarat motoru yönetimi.
 *
 * @author AtomGuard Team
 * @version 1.2.0
 */
public class IntelCommand implements SubCommand {

    private final AtomGuard plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public IntelCommand(AtomGuard plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getName() { return "intel"; }
    @Override public @NotNull String getDescriptionKey() { return "istihbarat.komut-aciklama"; }
    @Override public @NotNull String getPermission() { return "atomguard.admin"; }
    @Override public @NotNull String getUsageKey() { return "istihbarat.kullanim"; }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        TrafficIntelligenceEngine engine = plugin.getIntelligenceEngine();
        if (engine == null || !engine.isEnabled()) {
            sender.sendMessage(mm.deserialize("<red>Tehdit istihbarat motoru devre dışı."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<red>Kullanım: /ag intel <status|reset>"));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "status" -> handleStatus(sender, engine);
            case "reset"  -> handleReset(sender, engine);
            default       -> sender.sendMessage(mm.deserialize("<red>Kullanım: /ag intel <status|reset>"));
        }
    }

    private void handleStatus(CommandSender sender, TrafficIntelligenceEngine engine) {
        ThreatLevel level = engine.getCurrentThreatLevel();
        TrafficProfile profile = engine.getCurrentHourProfile();
        IntelligenceAlert lastAlert = engine.getLastAlert();

        sender.sendMessage(mm.deserialize("<aqua>━━━ Tehdit İstihbaratı Durumu ━━━"));
        sender.sendMessage(mm.deserialize(String.format(
                "<gray>Tehdit Seviyesi: %s%s", level.getColor(), level.getDisplayName())));
        sender.sendMessage(mm.deserialize(String.format(
                "<gray>Öğrenme Modu: <white>%s",
                engine.isLearningMode() ? "<yellow>Aktif" : "<green>Pasif")));
        sender.sendMessage(mm.deserialize(String.format(
                "<gray>Toplam Örnek: <white>%d", engine.getTotalSamples())));
        sender.sendMessage(mm.deserialize(String.format(
                "<gray>Ardışık Anomali: <white>%d dk", engine.getConsecutiveAnomalyMinutes())));
        sender.sendMessage(mm.deserialize(String.format(
                "<gray>Hassasiyet Çarpanı: <white>%.1f×", engine.getSensitivityMultiplier())));
        sender.sendMessage(mm.deserialize(""));
        sender.sendMessage(mm.deserialize("<yellow>Anlık Trafik Profili (Bu Saat):"));
        sender.sendMessage(mm.deserialize(String.format(
                "<gray>  Bağlantı: ort=<white>%.1f</white> stddev=<white>%.1f",
                profile.getMeanConnections(), profile.getStddevConnections())));
        sender.sendMessage(mm.deserialize(String.format(
                "<gray>  Tekil IP: ort=<white>%.1f</white> stddev=<white>%.1f",
                profile.getMeanUniqueIps(), profile.getStddevUniqueIps())));
        sender.sendMessage(mm.deserialize(String.format(
                "<gray>  Paket Hızı: ort=<white>%.1f</white> stddev=<white>%.1f",
                profile.getMeanPacketRate(), profile.getStddevPacketRate())));
        sender.sendMessage(mm.deserialize(String.format(
                "<gray>  Bağlantı Güvenilir: <white>%s",
                profile.isReliable(5) ? "<green>Evet" : "<red>Hayır (az örnek)")));

        if (lastAlert != null) {
            sender.sendMessage(mm.deserialize(""));
            sender.sendMessage(mm.deserialize("<red>Son Uyarı:"));
            sender.sendMessage(mm.deserialize(String.format(
                    "<gray>  Metrik: <white>%s", lastAlert.getMetric())));
            sender.sendMessage(mm.deserialize(String.format(
                    "<gray>  Z-Score: <white>%.2f", lastAlert.getZScore())));
            sender.sendMessage(mm.deserialize(String.format(
                    "<gray>  Detay: <white>%s", lastAlert.getDetails())));
        }

        sender.sendMessage(mm.deserialize("<aqua>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    private void handleReset(CommandSender sender, TrafficIntelligenceEngine engine) {
        engine.resetProfiles();
        sender.sendMessage(mm.deserialize("<green>✓ Tüm trafik profilleri sıfırlandı."));
        plugin.getLogManager().info("[İSTİHBARAT] " + sender.getName() + " tüm trafik profillerini sıfırladı.");
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return Arrays.asList("status", "reset");
        return Collections.emptyList();
    }
}
