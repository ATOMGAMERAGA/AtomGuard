package com.atomguard.velocity.command;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.VelocityBuildInfo;
import com.atomguard.velocity.module.VelocityModule;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;
import java.util.Map;

/**
 * Ana /agv komutu.
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
            case "yenile", "reload" -> {
                plugin.getConfigManager().reload();
                plugin.getMessageManager().load();
                source.sendMessage(mm.deserialize("<green>Yapılandırma yeniden yüklendi.</green>"));
                plugin.getLogManager().log("Yapılandırma yeniden yüklendi: " + getSourceName(source));
            }
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
            default -> sendHelp(source);
        }
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
        if (source instanceof com.velocitypowered.api.proxy.Player p) return p.getUsername();
        return "Konsol";
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("atomguard.admin");
    }
}
