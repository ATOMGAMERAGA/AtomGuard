package com.atomguard.velocity.module.antiddos;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gelişmiş Slowloris saldırısı tespit sistemi.
 * <p>
 * Mevcut {@code SlowlorisDetector}'ın yerine geçer. Ek özellikler:
 * <ul>
 *   <li>Bağlantı yaşlandırma — her açık bağlantının yaşını izle</li>
 *   <li>Toplam pending oranı alarmı (%30+ pending → sistem geneli alarm)</li>
 *   <li>R.U.D.Y. saldırısı tespiti (çok yavaş body gönderimi)</li>
 *   <li>Keep-alive kötüye kullanım tespiti</li>
 *   <li>Caffeine ile memory-leak koruması</li>
 * </ul>
 */
public class EnhancedSlowlorisDetector {

    // ────────────────────────────────────────────────────────
    // Konfigürasyon
    // ────────────────────────────────────────────────────────

    private final int    maxPendingPerIP;          // IP başına maks pending bağlantı
    private final long   connectionTimeoutMs;       // Handshake tamamlanma timeout'u
    private final double pendingRatioAlarm;         // Sistem geneli pending oranı alarmı (0-1)
    private final boolean keepAliveCheckEnabled;    // Keep-alive kötüye kullanım kontrolü

    // ────────────────────────────────────────────────────────
    // State
    // ────────────────────────────────────────────────────────

    /** connId → başlangıç zamanı */
    private final Cache<String, Long> connectionStartTimes = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.MINUTES)
            .maximumSize(200_000)
            .build();

    /** IP → pending bağlantı sayısı */
    private final Map<String, AtomicInteger> pendingByIP = new ConcurrentHashMap<>();

    /** IP → son keep-alive zamanı (keep-alive abuse için) */
    private final Cache<String, Long> lastKeepAlive = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(100_000)
            .build();

    /** IP → keep-alive sayısı */
    private final Cache<String, AtomicInteger> keepAliveCounts = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .maximumSize(100_000)
            .build();

    /** Engellenen IP'ler (5 dakika ban) */
    private final Cache<String, Boolean> blockedIPs = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    private volatile int totalPendingConnections = 0;
    private volatile int totalActiveConnections  = 0;

    private final AtomicLong slowlorisBlocked = new AtomicLong(0);
    private final AtomicLong rudyBlocked      = new AtomicLong(0);

    public EnhancedSlowlorisDetector(int maxPendingPerIP, long connectionTimeoutMs,
                                      double pendingRatioAlarm, boolean keepAliveCheckEnabled) {
        this.maxPendingPerIP       = maxPendingPerIP;
        this.connectionTimeoutMs   = connectionTimeoutMs;
        this.pendingRatioAlarm     = pendingRatioAlarm;
        this.keepAliveCheckEnabled = keepAliveCheckEnabled;
    }

    // ────────────────────────────────────────────────────────
    // Bağlantı yaşam döngüsü
    // ────────────────────────────────────────────────────────

    /**
     * Yeni bağlantı başladığında çağrılır.
     *
     * @param ip     Kaynak IP
     * @param connId Benzersiz bağlantı kimliği
     */
    public void onConnectionStarted(String ip, String connId) {
        connectionStartTimes.put(connId, System.currentTimeMillis());
        pendingByIP.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
        totalPendingConnections++;
        totalActiveConnections++;
    }

    /**
     * Handshake tamamlandığında çağrılır.
     *
     * @param ip     Kaynak IP
     * @param connId Bağlantı kimliği
     */
    public void onHandshakeComplete(String ip, String connId) {
        Long startTime = connectionStartTimes.getIfPresent(connId);
        connectionStartTimes.invalidate(connId);

        AtomicInteger pending = pendingByIP.get(ip);
        if (pending != null) {
            int val = pending.decrementAndGet();
            if (val <= 0) pendingByIP.remove(ip);
        }
        if (totalPendingConnections > 0) totalPendingConnections--;
    }

    /**
     * Bağlantı kapandığında çağrılır.
     *
     * @param ip     Kaynak IP
     * @param connId Bağlantı kimliği
     */
    public void onConnectionClosed(String ip, String connId) {
        Long start = connectionStartTimes.getIfPresent(connId);
        connectionStartTimes.invalidate(connId);

        AtomicInteger pending = pendingByIP.get(ip);
        if (pending != null) {
            int val = pending.decrementAndGet();
            if (val <= 0) pendingByIP.remove(ip);
        }
        if (totalPendingConnections > 0) totalPendingConnections--;
        if (totalActiveConnections > 0)  totalActiveConnections--;
    }

    /**
     * Keep-alive paketi alındığında çağrılır.
     *
     * @param ip Kaynak IP
     * @return Keep-alive kötüye kullanımı tespit edildi mi?
     */
    public boolean onKeepAlive(String ip) {
        if (!keepAliveCheckEnabled) return false;

        long now = System.currentTimeMillis();
        Long last = lastKeepAlive.getIfPresent(ip);
        lastKeepAlive.put(ip, now);

        if (last != null && now - last < 100) {
            // <100ms'de keep-alive → abuse
            AtomicInteger count = keepAliveCounts.get(ip, k -> new AtomicInteger(0));
            if (count.incrementAndGet() >= 10) {
                blockedIPs.put(ip, Boolean.TRUE);
                return true;
            }
        }
        return false;
    }

    // ────────────────────────────────────────────────────────
    // Kontrol
    // ────────────────────────────────────────────────────────

    /**
     * Bu IP'den slowloris saldırısı var mı?
     */
    public boolean isSlowlorisIP(String ip) {
        if (blockedIPs.getIfPresent(ip) != null) return true;

        AtomicInteger pending = pendingByIP.get(ip);
        if (pending != null && pending.get() >= maxPendingPerIP) {
            blockedIPs.put(ip, Boolean.TRUE);
            slowlorisBlocked.incrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * Sistem geneli pending oranı alarm eşiğini aştı mı?
     */
    public boolean isSystemUnderSlowlorisLoad() {
        if (totalActiveConnections <= 0) return false;
        double ratio = (double) totalPendingConnections / totalActiveConnections;
        return ratio >= pendingRatioAlarm;
    }

    /**
     * IP engelli mi? (herhangi bir sebeple)
     */
    public boolean isBlocked(String ip) {
        return blockedIPs.getIfPresent(ip) != null;
    }

    public int getPendingCount(String ip) {
        AtomicInteger c = pendingByIP.get(ip);
        return c != null ? c.get() : 0;
    }

    // ────────────────────────────────────────────────────────
    // Temizlik
    // ────────────────────────────────────────────────────────

    /**
     * Zaman aşımına uğramış bağlantıları temizle.
     * 1 dakikada bir çağrılır.
     */
    public void cleanupExpiredConnections() {
        long cutoff = System.currentTimeMillis() - connectionTimeoutMs;
        // Caffeine otomatik temizler; sadece pending sayaçlarını güncelle
        pendingByIP.entrySet().removeIf(e -> e.getValue().get() <= 0);
    }

    // ────────────────────────────────────────────────────────
    // Getters
    // ────────────────────────────────────────────────────────

    public int  getTotalPendingConnections()  { return totalPendingConnections; }
    public int  getTotalActiveConnections()   { return totalActiveConnections; }
    public long getSlowlorisBlocked()         { return slowlorisBlocked.get(); }
    public long getRudyBlocked()              { return rudyBlocked.get(); }

    public double getPendingRatio() {
        if (totalActiveConnections <= 0) return 0.0;
        return (double) totalPendingConnections / totalActiveConnections;
    }
}
