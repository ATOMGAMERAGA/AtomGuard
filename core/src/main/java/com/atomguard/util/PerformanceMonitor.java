package com.atomguard.util;

import org.bukkit.Bukkit;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Plugin performans izleme sistemi
 * Bellek kullanımı, TPS, işlem süreleri vb. metrikleri takip eder
 */
public class PerformanceMonitor {

    private final MemoryMXBean memoryBean;
    private final AtomicLong totalProcessedPackets;
    private final AtomicLong totalBlockedExploits;
    private final long startTime;

    public PerformanceMonitor() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.totalProcessedPackets = new AtomicLong(0);
        this.totalBlockedExploits = new AtomicLong(0);
        this.startTime = System.currentTimeMillis();
    }

    /**
     * İşlenen paket sayısını artırır
     */
    public void incrementProcessedPackets() {
        totalProcessedPackets.incrementAndGet();
    }

    /**
     * Engellenen exploit sayısını artırır
     */
    public void incrementBlockedExploits() {
        totalBlockedExploits.incrementAndGet();
    }

    /**
     * Toplam işlenen paket sayısını döndürür
     *
     * @return İşlenen paket sayısı
     */
    public long getTotalProcessedPackets() {
        return totalProcessedPackets.get();
    }

    /**
     * Toplam engellenen exploit sayısını döndürür
     *
     * @return Engellenen exploit sayısı
     */
    public long getTotalBlockedExploits() {
        return totalBlockedExploits.get();
    }

    /**
     * Plugin'in kullandığı heap belleği döndürür (MB)
     *
     * @return Bellek kullanımı (MB)
     */
    public double getUsedMemoryMB() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        return heapUsage.getUsed() / (1024.0 * 1024.0);
    }

    /**
     * JVM'in toplam heap belleği döndürür (MB)
     *
     * @return Toplam bellek (MB)
     */
    public double getTotalMemoryMB() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        return heapUsage.getCommitted() / (1024.0 * 1024.0);
    }

    /**
     * Maksimum heap belleği döndürür (MB)
     *
     * @return Maksimum bellek (MB)
     */
    public double getMaxMemoryMB() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long max = heapUsage.getMax();
        return max == -1 ? -1 : max / (1024.0 * 1024.0);
    }

    /**
     * Bellek kullanım yüzdesini döndürür
     *
     * @return Kullanım yüzdesi (0-100)
     */
    public double getMemoryUsagePercent() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long used = heapUsage.getUsed();
        long max = heapUsage.getMax();

        if (max == -1) {
            return -1;
        }

        return (used * 100.0) / max;
    }

    /**
     * Sunucu TPS değerini döndürür
     * Paper API kullanır
     *
     * @return TPS değeri
     */
    public double getServerTPS() {
        try {
            // Paper API - getTPS() metodu 1m, 5m, 15m TPS döndürür
            double[] tps = Bukkit.getTPS();
            return Math.min(tps[0], 20.0); // 1 dakikalık TPS, max 20.0
        } catch (Exception e) {
            return -1.0; // TPS ölçümü desteklenmiyor
        }
    }

    /**
     * Plugin uptime süresini döndürür (milisaniye)
     *
     * @return Uptime (ms)
     */
    public long getUptimeMillis() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Plugin uptime süresini formatlanmış string olarak döndürür
     * Örnek: "2s 15dk 30sn"
     *
     * @return Formatlanmış uptime
     */
    public String getFormattedUptime() {
        long uptimeMs = getUptimeMillis();

        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        seconds %= 60;
        minutes %= 60;
        hours %= 24;

        StringBuilder sb = new StringBuilder();

        if (days > 0) {
            sb.append(days).append("g ");
        }
        if (hours > 0) {
            sb.append(hours).append("s ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("dk ");
        }
        sb.append(seconds).append("sn");

        return sb.toString();
    }

    /**
     * Performans istatistiklerini string olarak döndürür
     *
     * @return İstatistik metni
     */
    public String getStatistics() {
        return String.format(
                "İşlenen Paket: %,d | Engellenen Exploit: %,d | TPS: %.2f | Bellek: %.2f MB | Uptime: %s",
                getTotalProcessedPackets(),
                getTotalBlockedExploits(),
                getServerTPS(),
                getUsedMemoryMB(),
                getFormattedUptime()
        );
    }

    /**
     * Sayaçları sıfırlar
     */
    public void resetCounters() {
        totalProcessedPackets.set(0);
        totalBlockedExploits.set(0);
    }
}
