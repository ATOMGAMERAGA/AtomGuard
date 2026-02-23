package com.atomguard.manager;

import com.atomguard.AtomGuard;
import com.atomguard.api.stats.IStatisticsProvider;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Kalici istatistik yonetimi.
 * JSON dosyasina kaydeder/yukler. Modul bazli ve zaman bazli istatistikler tutar.
 */
public class StatisticsManager implements IStatisticsProvider {

    private final AtomGuard plugin;
    private final Gson gson;
    private final File statsFile;
    private final ScheduledExecutorService scheduler;

    // Config
    private final boolean enabled;
    private final int autoSaveMinutes;
    private final int maxAttackHistory;

    // Data
    private final ConcurrentHashMap<String, ModuleStats> moduleStats = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<AttackRecord> attackHistory = new ConcurrentLinkedDeque<>();
    private final java.util.concurrent.atomic.AtomicLong totalBlockedAllTime = new java.util.concurrent.atomic.AtomicLong(0);
    private volatile long serverStartTime = System.currentTimeMillis();

    private ScheduledFuture<?> saveTask;

    public StatisticsManager(AtomGuard plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.statsFile = new File(plugin.getDataFolder(), "statistics.json");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AtomGuard-Stats");
            t.setDaemon(true);
            return t;
        });

        this.enabled = plugin.getConfig().getBoolean("istatistik.aktif", true);
        this.autoSaveMinutes = plugin.getConfig().getInt("istatistik.otomatik-kaydetme-dakika", 5);
        this.maxAttackHistory = plugin.getConfig().getInt("istatistik.max-saldiri-gecmisi", 100);
    }

    public void start() {
        if (!enabled) return;

        load();

        // Auto-save task
        saveTask = scheduler.scheduleAtFixedRate(this::save,
                autoSaveMinutes, autoSaveMinutes, TimeUnit.MINUTES);

        plugin.getLogger().info("StatisticsManager baslatildi. Otomatik kaydetme: " + autoSaveMinutes + "dk");
    }

    public void stop() {
        if (saveTask != null) saveTask.cancel(false);
        save();
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // ═══════════════════════════════════════
    // Recording
    // ═══════════════════════════════════════

    /**
     * Records a module block event.
     */
    public void recordBlock(String moduleName) {
        if (!enabled) return;
        String today = todayKey();
        moduleStats.computeIfAbsent(moduleName, k -> new ModuleStats())
                   .increment(today);
        totalBlockedAllTime.incrementAndGet();
    }

    /**
     * Records an attack event (when attack mode ends).
     */
    public void recordAttack(long startTime, long endTime, int peakRate, long blockedDuring) {
        if (!enabled) return;
        AttackRecord record = new AttackRecord();
        record.startTime = startTime;
        record.endTime = endTime;
        record.peakConnectionRate = peakRate;
        record.blockedCount = blockedDuring;
        record.date = todayKey();
        attackHistory.addFirst(record);

        // Trim history — pollLast() kullan (boş deque'de NPE güvenli)
        while (attackHistory.size() > maxAttackHistory) {
            attackHistory.pollLast();
        }
    }

    // ═══════════════════════════════════════
    // Querying
    // ═══════════════════════════════════════

    public long getTotalBlockedAllTime() {
        return totalBlockedAllTime.get();
    }

    @Override
    public long getTotalBlocked() {
        return totalBlockedAllTime.get();
    }

    public long getModuleBlockedToday(String moduleName) {
        ModuleStats stats = moduleStats.get(moduleName);
        return stats == null ? 0 : stats.getCount(todayKey());
    }

    public long getModuleBlockedTotal(String moduleName) {
        ModuleStats stats = moduleStats.get(moduleName);
        return stats == null ? 0 : stats.getTotal();
    }

    public Map<String, Long> getAllModuleTotals() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, ModuleStats> entry : moduleStats.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getTotal());
        }
        return result;
    }

    public List<AttackRecord> getAttackHistory() {
        return new ArrayList<>(attackHistory);
    }

    public int getAttackCount() {
        return attackHistory.size();
    }

    // ═══════════════════════════════════════
    // Persistence
    // ═══════════════════════════════════════

    @SuppressWarnings("unchecked")
    public void load() {
        if (!statsFile.exists()) return;

        try (Reader reader = new InputStreamReader(new FileInputStream(statsFile), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<StatsData>(){}.getType();
            StatsData data = gson.fromJson(reader, type);
            if (data == null) return;

            this.totalBlockedAllTime.set(data.totalBlockedAllTime);
            if (data.moduleStats != null) {
                this.moduleStats.clear();
                this.moduleStats.putAll(data.moduleStats);
            }
            if (data.attackHistory != null) {
                this.attackHistory.clear();
                this.attackHistory.addAll(data.attackHistory);
            }

            plugin.getLogger().info("Istatistikler yuklendi. Toplam engelleme: " + totalBlockedAllTime);
        } catch (Exception e) {
            plugin.getLogger().warning("Istatistik dosyasi okunamadi: " + e.getMessage());
        }
    }

    public synchronized void save() {
        if (!enabled) return;

        try {
            if (!statsFile.getParentFile().exists()) {
                statsFile.getParentFile().mkdirs();
            }

            StatsData data = new StatsData();
            data.totalBlockedAllTime = this.totalBlockedAllTime.get();
            data.moduleStats = new HashMap<>(this.moduleStats);
            data.attackHistory = new ArrayList<>(this.attackHistory);
            data.lastSave = System.currentTimeMillis();

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(statsFile), StandardCharsets.UTF_8)) {
                gson.toJson(data, writer);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Istatistikler kaydedilemedi: " + e.getMessage());
        }
    }

    private String todayKey() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ═══════════════════════════════════════
    // Data classes
    // ═══════════════════════════════════════

    public static class StatsData {
        public long totalBlockedAllTime;
        public Map<String, ModuleStats> moduleStats;
        public List<AttackRecord> attackHistory;
        public long lastSave;
    }

    public static class ModuleStats {
        private final ConcurrentHashMap<String, Long> dailyCounts = new ConcurrentHashMap<>();
        private final java.util.concurrent.atomic.AtomicLong total = new java.util.concurrent.atomic.AtomicLong(0);

        public void increment(String dayKey) {
            dailyCounts.merge(dayKey, 1L, Long::sum);
            total.incrementAndGet();
        }

        public long getCount(String dayKey) {
            return dailyCounts.getOrDefault(dayKey, 0L);
        }

        public long getTotal() {
            return total.get();
        }

        public Map<String, Long> getDailyCounts() {
            return Collections.unmodifiableMap(dailyCounts);
        }
    }

    public static class AttackRecord {
        public long startTime;
        public long endTime;
        public int peakConnectionRate;
        public long blockedCount;
        public String date;

        public long getDurationSeconds() {
            return (endTime - startTime) / 1000;
        }
    }
}
