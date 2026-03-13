package com.atomguard.metrics;

import com.atomguard.AtomGuard;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sunucu ve plugin metriklerini toplayan ve raporlayan sinif.
 * TPS, bellek, modul engelleme sayilari, aktif oyuncu ve islem sureleri takip eder.
 *
 * @author AtomGuard Team
 * @version 2.0.0
 */
public class CoreMetrics {

    private final AtomGuard plugin;
    private final ScheduledExecutorService scheduler;

    // TPS & Memory (updated every second)
    private volatile double currentTps = 20.0;
    private volatile long usedMemoryMB = 0;
    private volatile long maxMemoryMB = 0;
    private volatile int activePlayers = 0;

    // Per-module block counts per minute
    private final ConcurrentHashMap<String, AtomicLong> moduleBlockCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> moduleBlocksPerMinute = new ConcurrentHashMap<>();

    // Heuristic processing time tracking (nanoseconds)
    private final AtomicLong heuristicTotalNanos = new AtomicLong(0);
    private final AtomicLong heuristicCallCount = new AtomicLong(0);
    private volatile double heuristicAvgMs = 0.0;

    // Storage query latency tracking (nanoseconds)
    private final AtomicLong storageTotalNanos = new AtomicLong(0);
    private final AtomicLong storageCallCount = new AtomicLong(0);
    private volatile double storageAvgMs = 0.0;

    // Total blocks across all modules in current minute window
    private final AtomicLong totalBlocksCurrentMinute = new AtomicLong(0);
    private volatile long totalBlocksPerMinute = 0;

    private final boolean enabled;
    private final int updateIntervalSeconds;

    public CoreMetrics(AtomGuard plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("metrics.enabled", true);
        this.updateIntervalSeconds = Math.max(1, plugin.getConfig().getInt("metrics.update-interval-seconds", 1));
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AtomGuard-Metrics");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (!enabled) return;

        // Fast update: TPS, memory, players
        scheduler.scheduleAtFixedRate(this::updateFastMetrics,
            updateIntervalSeconds, updateIntervalSeconds, TimeUnit.SECONDS);

        // Slow update: per-minute rate snapshots
        scheduler.scheduleAtFixedRate(this::updateMinuteRates, 60, 60, TimeUnit.SECONDS);

        plugin.getLogger().info("[Metrikler] Metrik toplama sistemi baslatildi.");
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void updateFastMetrics() {
        try {
            // TPS
            try {
                currentTps = Bukkit.getTPS()[0];
            } catch (Exception e) {
                currentTps = -1.0;
            }

            // Memory
            Runtime runtime = Runtime.getRuntime();
            maxMemoryMB = runtime.maxMemory() / (1024 * 1024);
            usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);

            // Active players
            try {
                activePlayers = Bukkit.getOnlinePlayers().size();
            } catch (Exception e) {
                activePlayers = 0;
            }

            // Heuristic avg
            long hCalls = heuristicCallCount.get();
            if (hCalls > 0) {
                heuristicAvgMs = (heuristicTotalNanos.get() / (double) hCalls) / 1_000_000.0;
            }

            // Storage avg
            long sCalls = storageCallCount.get();
            if (sCalls > 0) {
                storageAvgMs = (storageTotalNanos.get() / (double) sCalls) / 1_000_000.0;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Metrikler] Hizli metrik guncelleme hatasi: " + e.getMessage());
        }
    }

    private void updateMinuteRates() {
        try {
            // Snapshot per-module counts and reset
            for (Map.Entry<String, AtomicLong> entry : moduleBlockCounts.entrySet()) {
                long count = entry.getValue().getAndSet(0);
                moduleBlocksPerMinute.put(entry.getKey(), count);
            }

            // Total blocks per minute
            totalBlocksPerMinute = totalBlocksCurrentMinute.getAndSet(0);

            // Reset heuristic/storage averages for next window
            heuristicTotalNanos.set(0);
            heuristicCallCount.set(0);
            storageTotalNanos.set(0);
            storageCallCount.set(0);
        } catch (Exception e) {
            plugin.getLogger().warning("[Metrikler] Dakikalik metrik guncelleme hatasi: " + e.getMessage());
        }
    }

    // --- Recording Methods ---

    /**
     * Record a module block event.
     */
    public void recordModuleBlock(String moduleName) {
        moduleBlockCounts.computeIfAbsent(moduleName, k -> new AtomicLong(0)).incrementAndGet();
        totalBlocksCurrentMinute.incrementAndGet();
    }

    /**
     * Record heuristic processing time.
     */
    public void recordHeuristicTime(long nanos) {
        heuristicTotalNanos.addAndGet(nanos);
        heuristicCallCount.incrementAndGet();
    }

    /**
     * Record storage query latency.
     */
    public void recordStorageLatency(long nanos) {
        storageTotalNanos.addAndGet(nanos);
        storageCallCount.incrementAndGet();
    }

    // --- JSON Output ---

    /**
     * Generate JSON representation of all metrics.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"tps\":").append(String.format("%.2f", currentTps)).append(",");
        sb.append("\"memory\":{\"used_mb\":").append(usedMemoryMB)
          .append(",\"max_mb\":").append(maxMemoryMB).append("},");
        sb.append("\"active_players\":").append(activePlayers).append(",");
        sb.append("\"blocks_per_minute\":").append(totalBlocksPerMinute).append(",");
        sb.append("\"heuristic_avg_ms\":").append(String.format("%.3f", heuristicAvgMs)).append(",");
        sb.append("\"storage_avg_ms\":").append(String.format("%.3f", storageAvgMs)).append(",");

        // Module blocks per minute
        sb.append("\"module_blocks\":{");
        boolean first = true;
        for (Map.Entry<String, Long> entry : moduleBlocksPerMinute.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":").append(entry.getValue());
        }
        sb.append("}");
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // --- Getters ---

    public boolean isEnabled() { return enabled; }
    public double getCurrentTps() { return currentTps; }
    public long getUsedMemoryMB() { return usedMemoryMB; }
    public long getMaxMemoryMB() { return maxMemoryMB; }
    public int getActivePlayers() { return activePlayers; }
    public long getTotalBlocksPerMinute() { return totalBlocksPerMinute; }
    public double getHeuristicAvgMs() { return heuristicAvgMs; }
    public double getStorageAvgMs() { return storageAvgMs; }
    public Map<String, Long> getModuleBlocksPerMinute() { return Map.copyOf(moduleBlocksPerMinute); }
}
