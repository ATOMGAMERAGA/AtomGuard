package com.atomguard.forensics;

import com.atomguard.AtomGuard;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Saldırı adli analiz yöneticisi.
 * Saldırı sırasında metrikleri kaydeder, saldırı bittiğinde snapshot oluşturur.
 *
 * @author AtomGuard Team
 * @version 1.2.0
 */
public class ForensicsManager {

    private final AtomGuard plugin;
    private final Gson gson;
    private final File forensicsDir;
    private final ScheduledExecutorService scheduler;

    // Aktif kayıt durumu
    private volatile AttackSnapshot activeSnapshot;
    private final List<TimelineEvent> activeTimeline = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, AtomicInteger> activeIpCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> activeModuleBlocks = new ConcurrentHashMap<>();
    private final AtomicLong connectionCountThisInterval = new AtomicLong(0);
    private final AtomicLong blockedCountThisInterval = new AtomicLong(0);
    private volatile long tpsSum = 0;
    private volatile int tpsReadings = 0;

    // Geçmiş snapshotlar (bellekte)
    private final ConcurrentLinkedDeque<AttackSnapshot> recentSnapshots = new ConcurrentLinkedDeque<>();
    private final int maxSnapshotsInMemory;
    private final int maxSnapshotsOnDisk;

    // Config
    private final boolean enabled;
    private final int metricIntervalSeconds;
    private final int cleanupIntervalHours;

    public ForensicsManager(AtomGuard plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.forensicsDir = new File(plugin.getDataFolder(), "forensics");
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "AtomGuard-Forensics");
            t.setDaemon(true);
            return t;
        });

        this.enabled = plugin.getConfig().getBoolean("adli-analiz.aktif", true);
        this.maxSnapshotsInMemory = plugin.getConfig().getInt("adli-analiz.max-bellek", 20);
        this.maxSnapshotsOnDisk = plugin.getConfig().getInt("adli-analiz.max-disk", 100);
        this.metricIntervalSeconds = plugin.getConfig().getInt("adli-analiz.metrik-araligi-sn", 10);
        this.cleanupIntervalHours = plugin.getConfig().getInt("adli-analiz.temizlik-araligi-saat", 24);
    }

    public void start() {
        if (!enabled) return;

        if (!forensicsDir.exists()) forensicsDir.mkdirs();

        // Periyodik metrik kaydı
        scheduler.scheduleAtFixedRate(this::recordMetrics,
            metricIntervalSeconds, metricIntervalSeconds, TimeUnit.SECONDS);

        // Temizlik görevi
        scheduler.scheduleAtFixedRate(this::cleanupOldSnapshots,
            cleanupIntervalHours, cleanupIntervalHours, TimeUnit.HOURS);

        // Mevcut snapshotları yükle
        loadRecentSnapshots();

        plugin.getLogger().info("Adli Analiz sistemi başlatıldı. Forensics dizini: " + forensicsDir.getPath());
    }

    public void stop() {
        scheduler.shutdown();
        try { scheduler.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Saldırı modu aktifleştiğinde çağrılır.
     */
    public void onAttackStart(int triggerRate) {
        if (!enabled) return;

        AttackSnapshot snap = new AttackSnapshot();
        snap.setSnapshotId(UUID.randomUUID().toString());
        snap.setStartTime(System.currentTimeMillis());
        snap.setPeakConnectionRate(triggerRate);
        snap.setOnlinePlayerCount(plugin.getServer().getOnlinePlayers().size());

        try {
            snap.setAvgTps(plugin.getServer().getTPS()[0]);
            Runtime rt = Runtime.getRuntime();
            snap.setAvgMemoryUsageMb((rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
        } catch (Exception ignored) {}

        activeTimeline.clear();
        activeIpCounts.clear();
        activeModuleBlocks.clear();
        connectionCountThisInterval.set(0);
        blockedCountThisInterval.set(0);
        tpsSum = 0;
        tpsReadings = 0;

        addTimelineEvent(new TimelineEvent(System.currentTimeMillis(), "ATTACK_START",
            "Saldırı tespit edildi. Tetikleyici hız: " + triggerRate + "/sn"));

        this.activeSnapshot = snap;
        plugin.getLogger().info("[Forensics] Saldırı kaydı başlatıldı: " + snap.getShortId());
    }

    /**
     * Saldırı modu deaktifleştiğinde çağrılır.
     */
    public void onAttackEnd() {
        if (!enabled || activeSnapshot == null) return;
        AttackSnapshot snapshot = activeSnapshot;
        this.activeSnapshot = null;

        snapshot.setEndTime(System.currentTimeMillis());
        snapshot.setDurationSeconds((snapshot.getEndTime() - snapshot.getStartTime()) / 1000);
        snapshot.setOnlinePlayerCountEnd(plugin.getServer().getOnlinePlayers().size());
        snapshot.setTotalAllowed(snapshot.getTotalConnectionAttempts() - snapshot.getTotalBlocked());

        // Ortalama bağlantı hızı
        if (snapshot.getDurationSeconds() > 0) {
            snapshot.setAvgConnectionRate(
                (double) snapshot.getTotalConnectionAttempts() / snapshot.getDurationSeconds());
        }

        // Top IP hesapla
        Map<String, Integer> topIps = computeTop(activeIpCounts, 20);
        snapshot.setTopIps(topIps);
        snapshot.setUniqueIpCount(activeIpCounts.size());

        // Subnet hesapla
        ConcurrentHashMap<String, AtomicInteger> subnetCounts = new ConcurrentHashMap<>();
        for (Map.Entry<String, AtomicInteger> e : activeIpCounts.entrySet()) {
            String subnet = getSubnet(e.getKey());
            subnetCounts.computeIfAbsent(subnet, k -> new AtomicInteger()).addAndGet(e.getValue().get());
        }
        snapshot.setTopSubnets(computeTop(subnetCounts, 10));
        snapshot.setUniqueSubnetCount(subnetCounts.size());

        // Modül blokları
        Map<String, Long> modBlocks = new LinkedHashMap<>();
        List<String> triggered = new ArrayList<>();
        for (Map.Entry<String, AtomicLong> e : activeModuleBlocks.entrySet()) {
            modBlocks.put(e.getKey(), e.getValue().get());
            triggered.add(e.getKey());
        }
        snapshot.setModuleBlockCounts(modBlocks);
        snapshot.setTriggeredModules(triggered);

        // Timeline
        addTimelineEvent(new TimelineEvent(System.currentTimeMillis(), "ATTACK_END",
            "Saldırı sona erdi. Süre: " + snapshot.getDurationSeconds() + "s | Engellenen: " + snapshot.getTotalBlocked()));
        snapshot.setTimeline(new ArrayList<>(activeTimeline));

        // Ortalama TPS
        if (tpsReadings > 0) snapshot.setAvgTps(tpsSum / 100.0 / tpsReadings);

        // Şiddet & sınıflandırma
        int threshold = plugin.getConfig().getInt("attack-mode.threshold", 10);
        snapshot.setSeverity(snapshot.calculateSeverity(threshold));
        snapshot.setClassification(classifyAttack(snapshot));
        snapshot.setResolution("auto-resolved");

        // Disk'e asenkron kaydet
        final AttackSnapshot finalSnapshot = snapshot;
        scheduler.submit(() -> saveSnapshot(finalSnapshot));

        // Belleğe ekle
        recentSnapshots.addFirst(snapshot);
        while (recentSnapshots.size() > maxSnapshotsInMemory) recentSnapshots.removeLast();

        plugin.getLogger().info("[Forensics] Saldırı kaydı tamamlandı: " + snapshot.getShortId()
            + " | Şiddet: " + snapshot.getSeverity()
            + " | Süre: " + snapshot.getDurationSeconds() + "s"
            + " | Engellenen: " + snapshot.getTotalBlocked());

        // Discord raporu
        if (plugin.getConfig().getBoolean("adli-analiz.discord-rapor", true)) {
            plugin.getDiscordWebhookManager().notifyForensicsReport(snapshot);
        }

        // API event
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getServer().getPluginManager().callEvent(
                new com.atomguard.api.event.AttackSnapshotCompleteEvent(
                    snapshot.getSnapshotId(),
                    snapshot.getSeverity(),
                    snapshot.getClassification().name(),
                    snapshot.getDurationSeconds(),
                    snapshot.getTotalBlocked()
                )
            );
        });
    }

    /**
     * Bağlantı kaydeder.
     */
    public void recordConnection(String ip) {
        if (!enabled || activeSnapshot == null) return;
        activeIpCounts.computeIfAbsent(ip, k -> new AtomicInteger()).incrementAndGet();
        connectionCountThisInterval.incrementAndGet();
        activeSnapshot.setTotalConnectionAttempts(activeSnapshot.getTotalConnectionAttempts() + 1);

        int currentRate = activeIpCounts.size();
        if (activeSnapshot.getPeakConnectionRate() < (int) connectionCountThisInterval.get()) {
            activeSnapshot.setPeakConnectionRate((int) connectionCountThisInterval.get());
        }
    }

    /**
     * Engelleme kaydeder.
     */
    public void recordBlock(String ip) {
        if (!enabled || activeSnapshot == null) return;
        blockedCountThisInterval.incrementAndGet();
        activeSnapshot.setTotalBlocked(activeSnapshot.getTotalBlocked() + 1);
    }

    /**
     * Modül engelleme kaydeder.
     */
    public void recordModuleBlock(String moduleName) {
        if (!enabled || activeSnapshot == null) return;
        activeModuleBlocks.computeIfAbsent(moduleName, k -> new AtomicLong()).incrementAndGet();
    }

    private void recordMetrics() {
        if (activeSnapshot == null) return;
        try {
            double tps = plugin.getServer().getTPS()[0];
            tpsSum += (long) (tps * 100);
            tpsReadings++;

            long conns = connectionCountThisInterval.getAndSet(0);
            long blocked = blockedCountThisInterval.getAndSet(0);

            addTimelineEvent(new TimelineEvent(System.currentTimeMillis(), "METRIC_SNAPSHOT",
                String.format("Bağlantı: %d | Engellenen: %d | TPS: %.1f", conns, blocked, tps)));
        } catch (Exception ignored) {}
    }

    private AttackClassification classifyAttack(AttackSnapshot s) {
        if (s.getUniqueIpCount() == 0) return AttackClassification.UNKNOWN;

        double avgPerIp = s.getUniqueIpCount() > 0
            ? (double) s.getTotalConnectionAttempts() / s.getUniqueIpCount() : 0;

        if (s.getUniqueIpCount() > 100 && avgPerIp < 5) return AttackClassification.DDOS;
        if (s.getUniqueIpCount() < 20 && avgPerIp > 20) return AttackClassification.BOT_ATTACK;
        if (s.getDurationSeconds() > 600 && avgPerIp < 3) return AttackClassification.SLOW_LORIS;
        if (s.getUniqueIpCount() > 20 && avgPerIp > 10) return AttackClassification.MIXED;
        return AttackClassification.UNKNOWN;
    }

    private void addTimelineEvent(TimelineEvent event) {
        activeTimeline.add(event);
    }

    private void saveSnapshot(AttackSnapshot snapshot) {
        try {
            File file = new File(forensicsDir, "attack-" + snapshot.getSnapshotId() + ".json");
            String json = gson.toJson(snapshot);
            try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                w.write(json);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Forensics] Snapshot kaydedilemedi: " + e.getMessage());
        }
    }

    private void loadRecentSnapshots() {
        File[] files = forensicsDir.listFiles(f -> f.getName().startsWith("attack-") && f.getName().endsWith(".json"));
        if (files == null || files.length == 0) return;

        // En yeni dosyaları al
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        int loaded = 0;
        for (File f : files) {
            if (loaded >= maxSnapshotsInMemory) break;
            try {
                String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                AttackSnapshot snap = gson.fromJson(json, AttackSnapshot.class);
                if (snap != null) {
                    recentSnapshots.addLast(snap);
                    loaded++;
                }
            } catch (Exception ignored) {}
        }
        if (loaded > 0) plugin.getLogger().info("[Forensics] " + loaded + " snapshot yüklendi.");
    }

    private void cleanupOldSnapshots() {
        try {
            File[] files = forensicsDir.listFiles(f -> f.getName().startsWith("attack-") && f.getName().endsWith(".json"));
            if (files == null || files.length <= maxSnapshotsOnDisk) return;

            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            int toDelete = files.length - maxSnapshotsOnDisk;
            for (int i = 0; i < toDelete; i++) {
                files[i].delete();
            }
            plugin.getLogger().info("[Forensics] " + toDelete + " eski snapshot silindi.");
        } catch (Exception e) {
            plugin.getLogger().warning("[Forensics] Temizlik hatası: " + e.getMessage());
        }
    }

    /**
     * ID prefix ile snapshot ara.
     */
    public AttackSnapshot getSnapshot(String idPrefix) {
        for (AttackSnapshot s : recentSnapshots) {
            if (s.getSnapshotId() != null && s.getSnapshotId().startsWith(idPrefix)) return s;
        }
        // Disk'te de ara
        File[] files = forensicsDir.listFiles(f -> f.getName().contains(idPrefix));
        if (files != null && files.length > 0) {
            try {
                String json = new String(Files.readAllBytes(files[0].toPath()), StandardCharsets.UTF_8);
                return gson.fromJson(json, AttackSnapshot.class);
            } catch (Exception ignored) {}
        }
        return null;
    }

    public AttackSnapshot getLatestSnapshot() { return recentSnapshots.peekFirst(); }
    public List<AttackSnapshot> getRecentSnapshots() { return new ArrayList<>(recentSnapshots); }
    public String getForensicsDir() { return forensicsDir.getAbsolutePath(); }
    public boolean isEnabled() { return enabled; }
    public boolean isRecording() { return activeSnapshot != null; }

    private <T extends Number> Map<String, Integer> computeTop(Map<String, T> counts, int limit) {
        return counts.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue().intValue(), a.getValue().intValue()))
            .limit(limit)
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().intValue(),
                (e1, e2) -> e1, LinkedHashMap::new));
    }

    private String getSubnet(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length >= 3) return parts[0] + "." + parts[1] + "." + parts[2] + ".0/24";
        } catch (Exception ignored) {}
        return ip;
    }
}
