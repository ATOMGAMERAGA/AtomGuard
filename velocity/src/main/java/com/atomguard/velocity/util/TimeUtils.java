package com.atomguard.velocity.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Zaman formatlama yardımcıları.
 */
public final class TimeUtils {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private TimeUtils() {}

    /**
     * Milisaniyeyi Türkçe okunabilir süre string'ine çevirir.
     * Örn: 3661000 → "1 saat 1 dakika 1 saniye"
     */
    public static String formatDuration(long ms) {
        if (ms < 0) return "0 saniye";
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(" gün ");
        if (hours % 24 > 0) sb.append(hours % 24).append(" saat ");
        if (minutes % 60 > 0) sb.append(minutes % 60).append(" dakika ");
        if (seconds % 60 > 0 || sb.isEmpty()) sb.append(seconds % 60).append(" saniye");
        return sb.toString().trim();
    }

    /**
     * Kısa format: "1s 1d 1sn"
     */
    public static String formatDurationShort(long ms) {
        if (ms < 0) return "0sn";
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("g ");
        if (hours % 24 > 0) sb.append(hours % 24).append("s ");
        if (minutes % 60 > 0) sb.append(minutes % 60).append("d ");
        if (seconds % 60 > 0 || sb.isEmpty()) sb.append(seconds % 60).append("sn");
        return sb.toString().trim();
    }

    /**
     * Türkçe/İngilizce süre string'ini milisaniyeye çevirir.
     * Desteklenen formatlar: "30s", "30sn", "10d", "10m", "10min", "2s", "2h", "7g", "7d"
     */
    public static long parseDuration(String duration) {
        if (duration == null || duration.isEmpty()) return 0;
        duration = duration.trim().toLowerCase();
        try {
            if (duration.endsWith("gün") || duration.endsWith("g")) {
                long val = Long.parseLong(duration.replaceAll("[^0-9]", ""));
                return val * 86400_000L;
            } else if (duration.endsWith("saat") || duration.endsWith("h")) {
                long val = Long.parseLong(duration.replaceAll("[^0-9]", ""));
                return val * 3600_000L;
            } else if (duration.endsWith("dakika") || duration.endsWith("min") || duration.endsWith("m")) {
                long val = Long.parseLong(duration.replaceAll("[^0-9]", ""));
                return val * 60_000L;
            } else if (duration.endsWith("saniye") || duration.endsWith("sn") || duration.endsWith("s")) {
                long val = Long.parseLong(duration.replaceAll("[^0-9]", ""));
                return val * 1000L;
            } else {
                // Try plain number as seconds
                return Long.parseLong(duration.replaceAll("[^0-9]", "")) * 1000L;
            }
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Unix timestamp'i okunabilir tarih string'ine çevirir.
     */
    public static String formatTimestamp(long timestamp) {
        return FORMATTER.format(Instant.ofEpochMilli(timestamp));
    }

    /**
     * Başlangıç zamanından itibaren geçen süreyi döndürür.
     */
    public static String getUptime(long startTimeMs) {
        return formatDuration(System.currentTimeMillis() - startTimeMs);
    }

    /**
     * Anlık zaman string'i döndürür (HH:mm:ss formatı).
     */
    public static String now() {
        return TIME_FORMATTER.format(Instant.now());
    }
}
