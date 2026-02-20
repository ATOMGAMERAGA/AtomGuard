package com.atomguard.velocity.module.antiddos;

import com.atomguard.velocity.data.AttackSnapshot;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Saldırı oturum kaydedici.
 * <p>
 * Her saldırı oturumunun tam kaydını tutar:
 * <ul>
 *   <li>Başlangıç / bitiş zamanı, süre</li>
 *   <li>Tepe hızı ve ortalama CPS</li>
 *   <li>Engellenen toplam bağlantı</li>
 *   <li>En aktif 20 saldırgan IP</li>
 *   <li>En aktif 5 saldırgan subnet</li>
 *   <li>Saldırı tipi tahmini</li>
 *   <li>Periyodik {@link AttackSnapshot}'lar</li>
 * </ul>
 * <p>
 * JSON dosyasına kaydeder ve bellekte son {@code maxHistory} oturumu tutar.
 */
public class AttackSessionRecorder {

    // ────────────────────────────────────────────────────────
    // Veri yapıları
    // ────────────────────────────────────────────────────────

    /** Tek bir saldırı oturumunun kaydı. */
    public static class AttackSession {
        public final long   startTime;
        public       long   endTime;
        public       int    peakCps;
        public       double avgCps;
        public       long   totalBlocked;
        public       String attackType;
        public final List<String>       topAttackerIPs     = new ArrayList<>();
        public final List<String>       topAttackerSubnets = new ArrayList<>();
        public final List<AttackSnapshot> snapshots         = new ArrayList<>();
        public       int    cpsSum;
        public       int    tickCount;

        public AttackSession(long startTime) {
            this.startTime = startTime;
        }

        public long getDurationMs() {
            return (endTime > 0 ? endTime : System.currentTimeMillis()) - startTime;
        }

        public String getFormattedStart() {
            return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(startTime));
        }
    }

    // ────────────────────────────────────────────────────────
    // State
    // ────────────────────────────────────────────────────────

    private final int    maxHistory;
    private final long   snapshotIntervalMs;
    private final boolean jsonEnabled;
    private final Path   dataDirectory;
    private final Logger logger;
    private final Gson   gson = new GsonBuilder().setPrettyPrinting().create();

    /** Son N saldırı oturumu (bellek içi) */
    private final Deque<AttackSession> sessions = new ArrayDeque<>();

    /** Güncel aktif oturum */
    private volatile AttackSession activeSession = null;

    /** Saldırı sırasında IP bazlı bağlantı sayıcısı */
    private final Map<String, AtomicInteger> attackerIPCounts = new ConcurrentHashMap<>();

    /** Saldırı sırasında subnet bazlı bağlantı sayıcısı */
    private final Map<String, AtomicInteger> attackerSubnetCounts = new ConcurrentHashMap<>();

    private final ScheduledExecutorService snapshotScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "atomguard-snapshot");
        t.setDaemon(true);
        return t;
    });

    public AttackSessionRecorder(int maxHistory, long snapshotIntervalMs,
                                  boolean jsonEnabled, Path dataDirectory, Logger logger) {
        this.maxHistory          = maxHistory;
        this.snapshotIntervalMs  = snapshotIntervalMs;
        this.jsonEnabled         = jsonEnabled;
        this.dataDirectory       = dataDirectory;
        this.logger              = logger;

        snapshotScheduler.scheduleAtFixedRate(this::takeSnapshot,
                snapshotIntervalMs, snapshotIntervalMs, TimeUnit.MILLISECONDS);
    }

    // ────────────────────────────────────────────────────────
    // Oturum yönetimi
    // ────────────────────────────────────────────────────────

    /** Yeni saldırı oturumu başlat. */
    public synchronized void startSession(AttackClassifier.AttackType attackType) {
        if (activeSession != null) return; // Zaten aktif

        activeSession = new AttackSession(System.currentTimeMillis());
        activeSession.attackType = attackType.getDisplayName();
        attackerIPCounts.clear();
        attackerSubnetCounts.clear();
        logger.info("Saldırı oturumu başladı: {}", attackType.getDisplayName());
    }

    /** Aktif oturumu sonlandır. */
    public synchronized void endSession(long totalBlocked, AttackClassifier.AttackType finalType) {
        if (activeSession == null) return;

        AttackSession session = activeSession;
        session.endTime      = System.currentTimeMillis();
        session.totalBlocked = totalBlocked;
        session.attackType   = finalType.getDisplayName();
        session.avgCps       = session.tickCount > 0 ? (double) session.cpsSum / session.tickCount : 0;

        // En aktif saldırganlar
        attackerIPCounts.entrySet().stream()
                .sorted(Map.Entry.<String, AtomicInteger>comparingByValue(
                        (a, b) -> b.get() - a.get()))
                .limit(20)
                .forEach(e -> session.topAttackerIPs.add(e.getKey() + "(" + e.getValue().get() + ")"));

        attackerSubnetCounts.entrySet().stream()
                .sorted(Map.Entry.<String, AtomicInteger>comparingByValue(
                        (a, b) -> b.get() - a.get()))
                .limit(5)
                .forEach(e -> session.topAttackerSubnets.add(e.getKey() + "(" + e.getValue().get() + ")"));

        // Belleğe ekle
        synchronized (sessions) {
            sessions.addLast(session);
            while (sessions.size() > maxHistory) sessions.pollFirst();
        }

        if (jsonEnabled) saveSessionToFile(session);
        activeSession = null;
        logger.info("Saldırı oturumu sona erdi. Süre: {}ms, Engellenen: {}",
                session.getDurationMs(), totalBlocked);
    }

    // ────────────────────────────────────────────────────────
    // Metrik güncelleme
    // ────────────────────────────────────────────────────────

    /** Anlık CPS değerini oturuma kaydet. */
    public void recordCps(int cps) {
        AttackSession s = activeSession;
        if (s == null) return;
        s.cpsSum   += cps;
        s.tickCount++;
        if (cps > s.peakCps) s.peakCps = cps;
    }

    /** Engellenen bağlantıyı kaydet. */
    public void recordBlockedConnection(String ip) {
        AttackSession s = activeSession;
        if (s == null) return;
        attackerIPCounts.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
        attackerSubnetCounts.computeIfAbsent(SubnetAnalyzer.getSubnet24(ip),
                k -> new AtomicInteger(0)).incrementAndGet();
    }

    // ────────────────────────────────────────────────────────
    // Snapshot
    // ────────────────────────────────────────────────────────

    private void takeSnapshot() {
        AttackSession s = activeSession;
        if (s == null) return;

        // En aktif IP'leri topla (anlık)
        List<String> topIPs = attackerIPCounts.entrySet().stream()
                .sorted(Map.Entry.<String, AtomicInteger>comparingByValue(
                        (a, b) -> b.get() - a.get()))
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

        String dominantSubnet = attackerSubnetCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue((a, b) -> a.get() - b.get()))
                .map(Map.Entry::getKey)
                .orElse(null);

        long duration = System.currentTimeMillis() - s.startTime;
        AttackSnapshot snapshot = AttackSnapshot.current(
                duration, s.peakCps, (int) s.totalBlocked, dominantSubnet, topIPs, true);
        s.snapshots.add(snapshot);
    }

    // ────────────────────────────────────────────────────────
    // JSON kayıt
    // ────────────────────────────────────────────────────────

    private void saveSessionToFile(AttackSession session) {
        try {
            Path dir = dataDirectory.resolve("attack-sessions");
            Files.createDirectories(dir);
            String filename = "session-" + session.startTime + ".json";
            String json = gson.toJson(session);
            Files.writeString(dir.resolve(filename), json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            logger.warn("Saldırı oturumu kaydedilemedi: {}", e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────
    // Getters
    // ────────────────────────────────────────────────────────

    public boolean             isSessionActive()  { return activeSession != null; }
    public AttackSession       getActiveSession()  { return activeSession; }

    public List<AttackSession> getRecentSessions() {
        synchronized (sessions) {
            return new ArrayList<>(sessions);
        }
    }

    public AttackSession getLastSession() {
        synchronized (sessions) {
            return sessions.isEmpty() ? null : sessions.peekLast();
        }
    }

    public void shutdown() {
        snapshotScheduler.shutdownNow();
    }
}
