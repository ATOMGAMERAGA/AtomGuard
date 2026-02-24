package com.atomguard.forensics;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Saldırı snapshot'ından MiniMessage formatında rapor üretir.
 *
 * @author AtomGuard Team
 * @version 1.2.0
 */
public class ForensicsReport {

    private ForensicsReport() {}

    /**
     * Snapshot'tan MiniMessage formatında rapor satırları döner.
     */
    public static List<String> format(AttackSnapshot s) {
        List<String> lines = new ArrayList<>();
        String idShort = s.getShortId() != null ? s.getShortId() : "?";

        lines.add("<aqua>━━━ Saldırı Raporu: " + idShort + " ━━━");
        lines.add("<gray>Tarih: <white>" + formatDate(s.getStartTime()));
        lines.add("<gray>Süre: <white>" + formatDuration(s.getDurationSeconds()));
        lines.add("<gray>Tür: <white>" + (s.getClassification() != null ? s.getClassification().getDisplayName() : "Bilinmeyen"));
        lines.add("<gray>Şiddet: " + severityColor(s.getSeverity()) + s.getSeverity());
        lines.add("");

        // Trafik
        lines.add("<aqua>📊 Trafik:");
        lines.add("<gray>Peak: <white>" + s.getPeakConnectionRate()
            + " bağlantı/sn <gray>| Toplam: <white>" + s.getTotalConnectionAttempts() + " deneme");

        long total = s.getTotalConnectionAttempts();
        String pct = total > 0 ? String.format("%.1f", (double) s.getTotalBlocked() / total * 100) : "0";
        lines.add("<gray>Engellenen: <white>" + s.getTotalBlocked()
            + " <dark_gray>(" + pct + "%) <gray>| İzin: <white>" + s.getTotalAllowed());
        lines.add("<gray>Benzersiz IP: <white>" + s.getUniqueIpCount()
            + " <gray>| Benzersiz Subnet: <white>" + s.getUniqueSubnetCount());
        lines.add("");

        // Modüller
        if (s.getModuleBlockCounts() != null && !s.getModuleBlockCounts().isEmpty()) {
            lines.add("<aqua>🛡️ Modüller:");
            int count = 0;
            for (Map.Entry<String, Long> e : s.getModuleBlockCounts().entrySet()) {
                if (count++ >= 5) break;
                lines.add("<gray>  " + e.getKey() + ": <white>" + e.getValue() + " engelleme");
            }
            lines.add("");
        }

        // Ülke dağılımı (varsa)
        if (s.getCountryDistribution() != null && !s.getCountryDistribution().isEmpty()) {
            lines.add("<aqua>🌍 Ülke Dağılımı:");
            int i = 1;
            for (Map.Entry<String, Integer> e : s.getCountryDistribution().entrySet()) {
                if (i > 5) break;
                lines.add("<gray>  " + i++ + ". " + e.getKey() + " <white>" + e.getValue() + " bağlantı");
            }
            lines.add("");
        }

        // Sunucu etkisi
        lines.add("<aqua>📈 Sunucu Etkisi:");
        lines.add("<gray>TPS: <white>" + String.format("%.1f", s.getAvgTps())
            + " <gray>| RAM: <white>" + s.getAvgMemoryUsageMb() + "MB");
        lines.add("<gray>Oyuncular: <white>" + s.getOnlinePlayerCount()
            + " <gray>→ <white>" + s.getOnlinePlayerCountEnd());
        lines.add("<gray>Çözüm: <white>" + s.getResolution());

        lines.add("<aqua>━━━━━━━━━━━━━━━━━━━━━━━━━");
        return lines;
    }

    /**
     * Kısa liste satırı formatı.
     */
    public static String formatListLine(int index, AttackSnapshot s) {
        return String.format("<gray>%d. <white>%s <dark_gray>| <yellow>%s <dark_gray>| <white>%s <dark_gray>| %s%s",
            index,
            formatDate(s.getStartTime()),
            s.getClassification() != null ? s.getClassification().getDisplayName() : "?",
            formatDuration(s.getDurationSeconds()),
            severityColor(s.getSeverity()),
            s.getSeverity()
        );
    }

    private static String severityColor(String severity) {
        if (severity == null) return "<white>";
        return switch (severity) {
            case "CRITICAL" -> "<red>";
            case "HIGH" -> "<gold>";
            case "MEDIUM" -> "<yellow>";
            default -> "<green>";
        };
    }

    private static String formatDuration(long seconds) {
        if (seconds < 60) return seconds + " saniye";
        long min = seconds / 60;
        long sec = seconds % 60;
        if (min < 60) return min + " dakika " + sec + " saniye";
        long hours = min / 60;
        min = min % 60;
        return hours + " saat " + min + " dakika";
    }

    private static String formatDate(long timestamp) {
        if (timestamp == 0) return "Bilinmiyor";
        return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(timestamp));
    }
}
