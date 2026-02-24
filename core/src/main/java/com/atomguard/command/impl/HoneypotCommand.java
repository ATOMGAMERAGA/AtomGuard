package com.atomguard.command.impl;

import com.atomguard.AtomGuard;
import com.atomguard.command.SubCommand;
import com.atomguard.module.honeypot.HoneypotConnection;
import com.atomguard.module.honeypot.HoneypotModule;
import com.atomguard.module.honeypot.HoneypotServer;
import com.atomguard.module.honeypot.HoneypotStatistics;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * /ag honeypot &lt;status|stats&gt; komutunu işler.
 *
 * <ul>
 *   <li><b>status</b> — Modül durumu, aktif portlar, kara liste boyutu</li>
 *   <li><b>stats</b>  — Port başına sayaçlar, son 10 bağlantı</li>
 * </ul>
 *
 * Gerekli izin: {@code atomguard.admin.honeypot}
 */
public class HoneypotCommand implements SubCommand {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final AtomGuard plugin;

    public HoneypotCommand(AtomGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getName() { return "honeypot"; }

    @Override
    public @NotNull String getDescriptionKey() { return "honeypot.aciklama"; }

    @Override
    public @NotNull String getPermission() { return "atomguard.admin.honeypot"; }

    @Override
    public @NotNull String getUsageKey() { return "honeypot.kullanim"; }

    // ═══════════════════════════════════════════════════════
    // Komut yürütme
    // ═══════════════════════════════════════════════════════

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        // /ag honeypot [status|stats]
        String sub = args.length >= 2 ? args[1].toLowerCase() : "status";

        switch (sub) {
            case "status" -> handleStatus(sender);
            case "stats"  -> handleStats(sender);
            default       -> sendUsage(sender);
        }
    }

    // ═══════════════════════════════════════════════════════
    // /ag honeypot status
    // ═══════════════════════════════════════════════════════

    private void handleStatus(CommandSender sender) {
        sendHeader(sender, "Honeypot Durumu");

        HoneypotModule module = getHoneypotModule();
        if (module == null) {
            sender.sendMessage(Component.text("  Bal Kupu modülü bulunamadı veya kayıtlı değil.", NamedTextColor.RED));
            sendFooter(sender);
            return;
        }

        // Modül aktif mi?
        NamedTextColor stateColor = module.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED;
        String stateText = module.isEnabled() ? "AKTİF" : "DEVRE DIŞI";
        sender.sendMessage(Component.text("  Durum: ", NamedTextColor.GRAY)
                .append(Component.text(stateText, stateColor)));

        // Aktif portlar
        List<HoneypotServer> servers = module.getServers();
        if (servers.isEmpty()) {
            sender.sendMessage(Component.text("  Aktif Port: ", NamedTextColor.GRAY)
                    .append(Component.text("yok", NamedTextColor.YELLOW)));
        } else {
            StringBuilder portList = new StringBuilder();
            for (HoneypotServer srv : servers) {
                if (portList.length() > 0) portList.append(", ");
                portList.append(srv.getPort());
                portList.append(srv.isRunning() ? " ✓" : " ✗");
            }
            sender.sendMessage(Component.text("  Aktif Portlar: ", NamedTextColor.GRAY)
                    .append(Component.text(portList.toString(), NamedTextColor.WHITE)));
        }

        // Sahte MOTD
        sender.sendMessage(Component.text("  Sahte MOTD: ", NamedTextColor.GRAY)
                .append(Component.text(module.isFakeMotdEnabled() ? "AKTİF" : "DEVRE DIŞI",
                        module.isFakeMotdEnabled() ? NamedTextColor.GREEN : NamedTextColor.YELLOW)));

        // Toplam yakalanan
        HoneypotStatistics stats = module.getStatistics();
        if (stats != null) {
            sender.sendMessage(Component.text("  Toplam Yakalanan IP: ", NamedTextColor.GRAY)
                    .append(Component.text(stats.getTotalTrapped(), NamedTextColor.WHITE)));
        }

        // Kara liste boyutu
        if (module.getBlacklistBridge() != null) {
            sender.sendMessage(Component.text("  Kara Liste Boyutu: ", NamedTextColor.GRAY)
                    .append(Component.text(module.getBlacklistBridge().getBlacklistSize(), NamedTextColor.WHITE)));
        }

        // Toplam engelleme (modül bloklama sayacı)
        sender.sendMessage(Component.text("  Toplam Engelleme: ", NamedTextColor.GRAY)
                .append(Component.text(module.getBlockedCount(), NamedTextColor.WHITE)));

        sendFooter(sender);
    }

    // ═══════════════════════════════════════════════════════
    // /ag honeypot stats
    // ═══════════════════════════════════════════════════════

    private void handleStats(CommandSender sender) {
        sendHeader(sender, "Honeypot İstatistikleri");

        HoneypotModule module = getHoneypotModule();
        if (module == null || module.getStatistics() == null) {
            sender.sendMessage(Component.text("  İstatistik verisi yok.", NamedTextColor.YELLOW));
            sendFooter(sender);
            return;
        }

        HoneypotStatistics stats = module.getStatistics();

        // Port başına bağlantı sayıları
        Map<Integer, AtomicLong> portCounts = stats.getPortCounts();
        if (portCounts.isEmpty()) {
            sender.sendMessage(Component.text("  Henüz port bağlantısı kaydedilmedi.", NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("  Port Bağlantı Sayıları:", NamedTextColor.GRAY));
            portCounts.entrySet().stream()
                    .sorted(Map.Entry.<Integer, AtomicLong>comparingByValue(
                            (a, b) -> Long.compare(b.get(), a.get()))) // azalan sıra
                    .forEach(entry ->
                            sender.sendMessage(Component.text("    :" + entry.getKey() + " → ", NamedTextColor.DARK_GRAY)
                                    .append(Component.text(entry.getValue().get() + " bağlantı", NamedTextColor.WHITE))));
        }

        // Son 10 bağlantı
        List<HoneypotConnection> recent = stats.getRecentConnections();
        int limit = Math.min(10, recent.size());
        if (limit == 0) {
            sender.sendMessage(Component.text("  Son bağlantı yok.", NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("  Son " + limit + " Bağlantı:", NamedTextColor.GRAY));
            for (int i = 0; i < limit; i++) {
                HoneypotConnection conn = recent.get(i);
                String time = TIME_FMT.format(Instant.ofEpochMilli(conn.getTimestamp()));
                NamedTextColor rowColor = conn.isBlacklisted() ? NamedTextColor.RED : NamedTextColor.YELLOW;

                sender.sendMessage(Component.text("    [" + time + "] ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(conn.getIp(), rowColor))
                        .append(Component.text(" :" + conn.getPort(), NamedTextColor.GRAY))
                        .append(Component.text(" [" + conn.getProtocol() + "]", NamedTextColor.DARK_AQUA))
                        .append(conn.isBlacklisted()
                                ? Component.text(" ✗ engellendi", NamedTextColor.RED)
                                : Component.text(" ✓ izlendi", NamedTextColor.GREEN)));
            }
        }

        sendFooter(sender);
    }

    // ═══════════════════════════════════════════════════════
    // Yardımcılar
    // ═══════════════════════════════════════════════════════

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text(
                "Kullanım: /ag honeypot <status|stats>", NamedTextColor.YELLOW));
    }

    private void sendHeader(CommandSender sender, String title) {
        sender.sendMessage(Component.text(
                "━━━━━━━━━━━ " + title + " ━━━━━━━━━━━", NamedTextColor.GOLD));
    }

    private void sendFooter(CommandSender sender) {
        sender.sendMessage(Component.text(
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
    }

    /**
     * ModuleManager'dan HoneypotModule'ü bulur; yoksa null döner.
     */
    private HoneypotModule getHoneypotModule() {
        if (plugin.getModuleManager() == null) return null;
        var raw = plugin.getModuleManager().getModule("bal-kupu");
        if (raw instanceof HoneypotModule honeypot) return honeypot;
        return null;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) {
            String partial = args[1].toLowerCase();
            return List.of("status", "stats").stream()
                    .filter(s -> s.startsWith(partial))
                    .toList();
        }
        return List.of();
    }
}
