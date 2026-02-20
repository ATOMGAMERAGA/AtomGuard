package com.atomguard.velocity.module.antiddos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Gerçek zamanlı DDoS metrik toplayıcı.
 * <p>
 * Takip edilen metrikler:
 * <ul>
 *   <li>CPS — anlık, 1dk, 5dk, 15dk ortalamaları</li>
 *   <li>Block rate — anlık, 1dk ortalaması</li>
 *   <li>Pending bağlantı sayısı</li>
 *   <li>Aktif saldırı seviyesi</li>
 *   <li>Top 10 saldırgan IP (sliding window)</li>
 *   <li>Top 5 saldırgan subnet</li>
 *   <li>Tahmini bant genişliği kullanımı</li>
 *   <li>Son {@code historyMinutes} dakikanın CPS geçmişi</li>
 * </ul>
 * <p>
 * JSON formatında dışa aktarılabilir (web panel entegrasyonu için).
 */
public class DDoSMetricsCollector {

    // ────────────────────────────────────────────────────────
    // Konfigürasyon
    // ────────────────────────────────────────────────────────

    private final int historySeconds;              // Kaç saniyelik geçmiş tutulsun
    private static final int HANDSHAKE_BYTES = 256; // Ortalama handshake boyutu (bayt)

    // ────────────────────────────────────────────────────────
    // CPS geçmişi
    // ────────────────────────────────────────────────────────

    private final Deque<Integer> cpsHistory  = new ArrayDeque<>();
    private final Deque<Integer> blockHistory = new ArrayDeque<>();
    private final ReentrantReadWriteLock historyLock = new ReentrantReadWriteLock();

    // ────────────────────────────────────────────────────────
    // Anlık metrikler
    // ────────────────────────────────────────────────────────

    private volatile int  currentCps         = 0;
    private volatile int  currentBlockRate    = 0;
    private volatile int  pendingConnections  = 0;
    private volatile AttackLevelManager.AttackLevel currentLevel = AttackLevelManager.AttackLevel.NONE;

    // ────────────────────────────────────────────────────────
    // Saldırgan IP ve subnet takibi (sliding window)
    // ────────────────────────────────────────────────────────

    private final Map<String, AtomicInteger> ipHitCounts     = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> subnetHitCounts = new ConcurrentHashMap<>();
    private final AtomicLong totalBlocked  = new AtomicLong(0);
    private final AtomicLong totalAllowed  = new AtomicLong(0);

    public DDoSMetricsCollector(int historyMinutes) {
        this.historySeconds = historyMinutes * 60;
    }

    // ────────────────────────────────────────────────────────
    // Güncelleme
    // ────────────────────────────────────────────────────────

    /**
     * Her saniye çağrılır.
     *
     * @param cps        Anlık bağlantı/saniye
     * @param blockRate  Anlık engelleme/saniye
     * @param pending    Bekleyen bağlantı sayısı
     * @param level      Güncel saldırı seviyesi
     */
    public void tick(int cps, int blockRate, int pending, AttackLevelManager.AttackLevel level) {
        this.currentCps        = cps;
        this.currentBlockRate  = blockRate;
        this.pendingConnections = pending;
        this.currentLevel      = level;

        historyLock.writeLock().lock();
        try {
            cpsHistory.addLast(cps);
            blockHistory.addLast(blockRate);
            if (cpsHistory.size() > historySeconds)  cpsHistory.pollFirst();
            if (blockHistory.size() > historySeconds) blockHistory.pollFirst();
        } finally {
            historyLock.writeLock().unlock();
        }
    }

    /**
     * Engellenen bir bağlantıyı kaydet.
     */
    public void recordBlocked(String ip) {
        totalBlocked.incrementAndGet();
        ipHitCounts.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
        subnetHitCounts.computeIfAbsent(SubnetAnalyzer.getSubnet24(ip),
                k -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * İzin verilen bir bağlantıyı kaydet.
     */
    public void recordAllowed(String ip) {
        totalAllowed.incrementAndGet();
    }

    // ────────────────────────────────────────────────────────
    // Hesaplamalar
    // ────────────────────────────────────────────────────────

    /** Anlık CPS */
    public int getCurrentCps() { return currentCps; }

    /** Son 1 dakika ortalama CPS */
    public double getAvgCps1m() { return calcAverage(60); }

    /** Son 5 dakika ortalama CPS */
    public double getAvgCps5m() { return calcAverage(300); }

    /** Son 15 dakika ortalama CPS */
    public double getAvgCps15m() { return calcAverage(900); }

    private double calcAverage(int seconds) {
        historyLock.readLock().lock();
        try {
            if (cpsHistory.isEmpty()) return 0.0;
            int count = Math.min(seconds, cpsHistory.size());
            // Deque'nin son 'count' elemanlarını al
            int[] arr = cpsHistory.stream().mapToInt(Integer::intValue).toArray();
            double sum = 0;
            int start = Math.max(0, arr.length - count);
            for (int i = start; i < arr.length; i++) sum += arr[i];
            return count > 0 ? sum / (arr.length - start) : 0.0;
        } finally {
            historyLock.readLock().unlock();
        }
    }

    /** Anlık block rate */
    public int getCurrentBlockRate() { return currentBlockRate; }

    /** Son 1 dakika ortalama block rate */
    public double getAvgBlockRate1m() {
        historyLock.readLock().lock();
        try {
            if (blockHistory.isEmpty()) return 0.0;
            int count = Math.min(60, blockHistory.size());
            int[] arr = blockHistory.stream().mapToInt(Integer::intValue).toArray();
            double sum = 0;
            int start = Math.max(0, arr.length - count);
            for (int i = start; i < arr.length; i++) sum += arr[i];
            return count > 0 ? sum / (arr.length - start) : 0.0;
        } finally {
            historyLock.readLock().unlock();
        }
    }

    /** Tahmini bant genişliği (KB/s) */
    public double getEstimatedBandwidthKBps() {
        return (currentCps * HANDSHAKE_BYTES) / 1024.0;
    }

    // ────────────────────────────────────────────────────────
    // Top saldırganlar
    // ────────────────────────────────────────────────────────

    /** En fazla engellenen top N IP (sliding window) */
    public List<Map.Entry<String, Integer>> getTopAttackerIPs(int limit) {
        return ipHitCounts.entrySet().stream()
                .sorted(Map.Entry.<String, AtomicInteger>comparingByValue(
                        (a, b) -> b.get() - a.get()))
                .limit(limit)
                .map(e -> Map.entry(e.getKey(), e.getValue().get()))
                .toList();
    }

    /** En fazla engellenen top N subnet */
    public List<Map.Entry<String, Integer>> getTopAttackerSubnets(int limit) {
        return subnetHitCounts.entrySet().stream()
                .sorted(Map.Entry.<String, AtomicInteger>comparingByValue(
                        (a, b) -> b.get() - a.get()))
                .limit(limit)
                .map(e -> Map.entry(e.getKey(), e.getValue().get()))
                .toList();
    }

    // ────────────────────────────────────────────────────────
    // JSON dışa aktarım
    // ────────────────────────────────────────────────────────

    /**
     * Tüm metrikleri JSON formatında bir Map olarak döndür.
     * Web panel veya Prometheus adaptörü tarafından kullanılır.
     */
    public Map<String, Object> toJsonMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("currentCps",           currentCps);
        map.put("avgCps1m",             getAvgCps1m());
        map.put("avgCps5m",             getAvgCps5m());
        map.put("avgCps15m",            getAvgCps15m());
        map.put("currentBlockRate",     currentBlockRate);
        map.put("avgBlockRate1m",       getAvgBlockRate1m());
        map.put("pendingConnections",   pendingConnections);
        map.put("attackLevel",          currentLevel.getDisplayName());
        map.put("totalBlocked",         totalBlocked.get());
        map.put("totalAllowed",         totalAllowed.get());
        map.put("estimatedBandwidthKBps", getEstimatedBandwidthKBps());
        map.put("topAttackerIPs",       getTopAttackerIPs(10));
        map.put("topAttackerSubnets",   getTopAttackerSubnets(5));
        return map;
    }

    // ────────────────────────────────────────────────────────
    // Temizlik
    // ────────────────────────────────────────────────────────

    /**
     * Periyodik sayaç temizliği.
     * Her 5 dakikada bir çağrılır.
     */
    public void cleanup() {
        // Düşük sayımlı kayıtları temizle
        ipHitCounts.entrySet().removeIf(e -> e.getValue().get() <= 0);
        subnetHitCounts.entrySet().removeIf(e -> e.getValue().get() <= 0);
    }

    // ────────────────────────────────────────────────────────
    // Getters
    // ────────────────────────────────────────────────────────

    public AttackLevelManager.AttackLevel getCurrentLevel() { return currentLevel; }
    public int  getPendingConnections() { return pendingConnections; }
    public long getTotalBlocked()       { return totalBlocked.get(); }
    public long getTotalAllowed()       { return totalAllowed.get(); }

    public List<Integer> getCpsHistory() {
        historyLock.readLock().lock();
        try {
            return new ArrayList<>(cpsHistory);
        } finally {
            historyLock.readLock().unlock();
        }
    }
}
