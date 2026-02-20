package com.atomguard.velocity.command;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.VelocityBuildInfo;
import com.atomguard.velocity.data.ThreatScore;
import com.atomguard.velocity.manager.AttackAnalyticsManager;
import com.atomguard.velocity.module.VelocityModule;
import com.atomguard.velocity.module.antibot.VelocityAntiBotModule;
import com.atomguard.velocity.module.antivpn.VPNDetectionModule;
import com.atomguard.velocity.module.firewall.FirewallModule;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Gelişmiş Ana /agv komutu.
 */
public class AtomGuardVelocityCommand implements SimpleCommand {

    private final AtomGuardVelocity plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public AtomGuardVelocityCommand(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!source.hasPermission("atomguard.admin")) {
            source.sendMessage(mm.deserialize("<red>Bu komutu kullanma izniniz yok.</red>"));
            return;
        }

        if (args.length == 0) {
            sendHelp(source);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "durum", "status" -> sendStatus(source);
            case "yenile", "reload" -> handleReload(source);
            case "modul", "module" -> {
                if (args.length < 2) { source.sendMessage(mm.deserialize("<red>Kullanım: /agv modul <isim></red>")); return; }
                handleModuleCommand(source, args);
            }
            case "yasak", "ban" -> {
                if (args.length < 2) { source.sendMessage(mm.deserialize("<red>Kullanım: /agv yasak <ip> [sure] [sebep]</red>")); return; }
                handleBan(source, args);
            }
            case "af", "unban" -> {
                if (args.length < 2) { source.sendMessage(mm.deserialize("<red>Kullanım: /agv af <ip></red>")); return; }
                plugin.getFirewallModule().unbanIP(args[1]);
                source.sendMessage(mm.deserialize("<green>" + args[1] + " yasağı kaldırıldı.</green>"));
            }
            case "istatistik", "stats" -> sendStats(source);
            case "saldiri", "attack" -> {
                boolean state = plugin.isAttackMode();
                plugin.setAttackMode(!state);
                source.sendMessage(mm.deserialize("<yellow>Saldırı modu: " + (!state ? "<red>AKTİF</red>" : "<green>KAPALI</green>") + "</yellow>"));
            }
            case "inceleme", "inspect" -> {
                if (args.length < 2) { source.sendMessage(mm.deserialize("<red>Kullanım: /agv inceleme <ip/oyuncu></red>")); return; }
                handleInspect(source, args[1]);
            }
            case "rapor", "report" -> sendAttackReport(source);
            case "saglik", "health" -> sendHealthCheck(source);
            default -> sendHelp(source);
        }
    }

    private void handleReload(CommandSource source) {
        plugin.getConfigManager().reload();
        plugin.getMessageManager().load(plugin.getConfigManager().getString("dil", "tr"));
        
        // 1. Backend İletişimi (Redis) Yenile
        if (plugin.getBackendCommunicator() != null) {
            plugin.getBackendCommunicator().reload();
        }

        // 2. Audit Log
        if (plugin.getAuditLogger() != null) {
            plugin.getAuditLogger().log(
                com.atomguard.velocity.audit.AuditLogger.EventType.CONFIG_RELOADED,
                null, getSourceName(source), "system", "Source: " + getSourceName(source),
                com.atomguard.velocity.audit.AuditLogger.Severity.INFO
            );
        }

        plugin.getModuleManager().getAll().forEach(m -> {
            try {
                m.onConfigReload();
            } catch (Exception e) {
                plugin.getSlf4jLogger().error("Modül config reload hatası {}: {}", m.getName(), e.getMessage());
            }
        });

        source.sendMessage(mm.deserialize("<green>Yapılandırma ve tüm modüller yeniden yüklendi.</green>"));
        plugin.getLogManager().log("Yapılandırma yeniden yüklendi: " + getSourceName(source));
    }

    private void sendHelp(CommandSource source) {
        source.sendMessage(mm.deserialize(
            "<gray>─────────────────────────────\n" +
            "<gradient:#FF6B6B:#FF8E53>AtomGuard Velocity</gradient> <gray>v" + VelocityBuildInfo.VERSION + "\n" +
            "<gray>─────────────────────────────\n" +
            "<yellow>/agv durum</yellow> <gray>- Sistem durumu\n" +
            "<yellow>/agv yenile</yellow> <gray>- Yapılandırmayı yenile\n" +
            "<yellow>/agv modul <isim></yellow> <gray>- Modül aç/kapat\n" +
            "<yellow>/agv yasak <ip> [sure]</yellow> <gray>- IP yasağı\n" +
            "<yellow>/agv af <ip></yellow> <gray>- Yasak kaldır\n" +
            "<yellow>/agv istatistik</yellow> <gray>- İstatistikler\n" +
            "<yellow>/agv saldiri</yellow> <gray>- Saldırı modunu aç/kapat\n" +
            "<yellow>/agv inceleme <ip></yellow> <gray>- Detaylı oyuncu/IP profili\n" +
            "<yellow>/agv rapor</yellow> <gray>- Son 24 saat saldırı analizi\n" +
            "<yellow>/agv saglik</yellow> <gray>- Sunucu kaynak ve yük durumu\n" +
            "<gray>─────────────────────────────"
        ));
    }

    private void sendStatus(CommandSource source) {
        int enabled = plugin.getModuleManager().getEnabledCount();
        int total = plugin.getModuleManager().getAll().size();
        boolean attackMode = plugin.isAttackMode();
        long online = plugin.getProxyServer().getPlayerCount();

        source.sendMessage(mm.deserialize(
            "<gray>─────────────────────────────\n" +
            "<gradient:#FF6B6B:#FF8E53>AtomGuard Velocity</gradient> <gray>v" + VelocityBuildInfo.VERSION + "\n" +
            "<white>Modüller: <green>" + enabled + "/" + total + " aktif</green>\n" +
            "<white>Saldırı Modu: " + (attackMode ? "<red>AKTİF</red>" : "<green>KAPALI</green>") + "\n" +
            "<white>Çevrimiçi: <yellow>" + online + " oyuncu</yellow>\n" +
            "<gray>─────────────────────────────"
        ));
    }

    private void sendStats(CommandSource source) {
        Map<String, Long> stats = plugin.getStatisticsManager().getAll();
        StringBuilder sb = new StringBuilder("<gray>─────── İstatistikler ───────\n");
        stats.forEach((k, v) -> sb.append("<white>").append(k).append(": <yellow>").append(v).append("\n"));
        sb.append("<gray>─────────────────────────────");
        source.sendMessage(mm.deserialize(sb.toString()));
    }

    private void handleInspect(CommandSource source, String target) {
        // Resolve IP if target is a username
        String ip = target;
        Player player = plugin.getProxyServer().getPlayer(target).orElse(null);
        if (player != null) {
            ip = player.getRemoteAddress().getAddress().getHostAddress();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<gray>─── IP İnceleme: <white>").append(ip).append("</white> ───\n");

        FirewallModule fw = plugin.getFirewallModule();
        if (fw != null) {
            boolean banned = fw.getTempBanManager().isBanned(ip) || fw.getBlacklistManager().isBlacklisted(ip);
            int reputation = fw.getReputationEngine().getScore(ip);
            sb.append("<white>Güvenlik Duvarı: ").append(banned ? "<red>YASAKLI</red>" : "<green>TEMİZ</green>").append("\n");
            sb.append("<white>İtibar Skoru: <yellow>").append(reputation).append("/100</yellow>\n");
        }

        VelocityAntiBotModule ab = plugin.getAntiBotModule();
        if (ab != null) {
            ThreatScore ts = ab.getScore(ip);
            boolean verified = ab.isVerified(ip);
            sb.append("<white>Bot Tehdit Skoru: <yellow>").append(ts != null ? ts.getTotalScore() : 0).append("</yellow>");
            sb.append(" | Doğrulanmış: ").append(verified ? "<green>EVET</green>" : "<red>HAYIR</red>").append("\n");
        }

        VPNDetectionModule vpn = plugin.getVpnModule();
        if (vpn != null) {
            boolean verifiedClean = vpn.isVerifiedClean(ip);
            sb.append("<white>VPN Temiz Cache: ").append(verifiedClean ? "<green>EVET</green>" : "<gray>YOK</gray>").append("\n");
        }

        // Connection History
        if (player != null && plugin.getConnectionHistory() != null) {
            List<com.atomguard.velocity.data.ConnectionHistory.SessionRecord> history = plugin.getConnectionHistory().getHistory(player.getUniqueId());
            if (!history.isEmpty()) {
                sb.append("<white>Son Sunucular: <yellow>").append(String.join(", ", history.get(0).getServerHistory())).append("</yellow>\n");
            }
        }

        sb.append("<gray>───────────────────────────");
        source.sendMessage(mm.deserialize(sb.toString()));
    }

    private void sendHealthCheck(CommandSource source) {
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMB = rt.maxMemory() / (1024 * 1024);
        int playerCount = plugin.getProxyServer().getPlayerCount();
        int serverCount = plugin.getProxyServer().getAllServers().size();

        String redisStatus = plugin.getBackendCommunicator().isRedisEnabled() ? "<green>BAĞLI</green>" : "<gray>KAPALI</gray>";

        Map<String, Long> moduleBlocks = plugin.getModuleManager().getStatistics();
        String topModule = moduleBlocks.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(e -> e.getKey() + " (" + e.getValue() + ")")
            .orElse("Yok");

        source.sendMessage(mm.deserialize(String.format("""
            <gray>─── Sistem Sağlığı ───
            <white>Bellek: <yellow>%d/%d MB</yellow> (%%%d)
            <white>Oyuncular: <yellow>%d</yellow> | Sunucular: <yellow>%d</yellow>
            <white>Redis: %s
            <white>Saldırı Modu: %s
            <white>En Aktif Modül: <yellow>%s</yellow>
            <gray>───────────────────────────""",
            usedMB, maxMB, (maxMB > 0 ? (usedMB * 100 / maxMB) : 0),
            playerCount, serverCount,
            redisStatus,
            plugin.isAttackMode() ? "<red>AKTİF</red>" : "<green>KAPALI</green>",
            topModule)));
    }

    private void sendAttackReport(CommandSource source) {
        AttackAnalyticsManager analytics = plugin.getAttackAnalyticsManager();
        if (analytics == null) {
            source.sendMessage(mm.deserialize("<red>Analitik motoru aktif değil.</red>"));
            return;
        }

        AttackAnalyticsManager.AttackSummary summary = analytics.getLast24hSummary();
        source.sendMessage(mm.deserialize(String.format("""
            <gray>─── Son 24 Saat Saldırı Raporu ───
            <white>Saldırı Sayısı: <yellow>%d</yellow>
            <white>Toplam Engellenen: <yellow>%d</yellow>
            <white>En Yüksek Hız: <yellow>%d</yellow>/sn
            <gray>───────────────────────────""",
            summary.attackCount(), summary.totalBlocked(), summary.peakRate())));
    }

    private void handleModuleCommand(CommandSource source, String[] args) {
        String moduleName = args[1];
        boolean toggled = plugin.getModuleManager().toggle(moduleName);
        source.sendMessage(mm.deserialize(
            "<yellow>Modül '" + moduleName + "': " + (toggled ? "<green>AKTİF</green>" : "<red>KAPALI</red>") + "</yellow>"
        ));
    }

    private void handleBan(CommandSource source, String[] args) {
        String ip = args[1];
        long durationMs = args.length >= 3 ? parseDuration(args[2]) : 3_600_000L;
        String reason = args.length >= 4 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "Kural ihlali";
        plugin.getFirewallModule().banIP(ip, durationMs, reason);
        source.sendMessage(mm.deserialize("<red>" + ip + " yasaklandı. Sebep: " + reason + "</red>"));
    }

    private long parseDuration(String s) {
        try {
            if (s.endsWith("d")) return Long.parseLong(s.replace("d", "")) * 86_400_000L;
            if (s.endsWith("h")) return Long.parseLong(s.replace("h", "")) * 3_600_000L;
            if (s.endsWith("m")) return Long.parseLong(s.replace("m", "")) * 60_000L;
            return Long.parseLong(s) * 1000L;
        } catch (NumberFormatException e) { return 3_600_000L; }
    }

    private String getSourceName(CommandSource source) {
        if (source instanceof Player p) return p.getUsername();
        return "Konsol";
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.supplyAsync(() -> {
            String[] args = invocation.arguments();
            if (args.length <= 1) {
                String prefix = args.length == 1 ? args[0].toLowerCase() : "";
                return List.of("durum", "yenile", "modul", "yasak", "af",
                               "istatistik", "saldiri", "inceleme", "rapor", "saglik")
                    .stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
            }
            if (args.length == 2 && ("modul".equalsIgnoreCase(args[0]) || "module".equalsIgnoreCase(args[0]))) {
                return plugin.getModuleManager().getAll().stream()
                    .map(VelocityModule::getName)
                    .filter(n -> n.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            if (args.length == 2 && ("inceleme".equalsIgnoreCase(args[0]) || "inspect".equalsIgnoreCase(args[0]))) {
                // Çevrimiçi oyuncuları öner
                return plugin.getProxyServer().getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            return List.of();
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("atomguard.admin");
    }
}
