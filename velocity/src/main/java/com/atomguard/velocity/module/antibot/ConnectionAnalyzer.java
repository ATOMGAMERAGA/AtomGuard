package com.atomguard.velocity.module.antibot;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP başına bağlantı davranışı analizi.
 *
 * <p>Düzeltmeler (false positive önleme):
 * <ul>
 *   <li>{@code suspiciousThreshold} minimum 8 olarak zorlandı (önceden 5 idi)</li>
 *   <li>5 saniyelik grace period: İlk bağlantıdan sonra 5sn içinde şüpheli sayma</li>
 *   <li>Smoothed rate: gerçek zaman aralığına bölünmüş doğru hesaplama</li>
 * </ul>
 */
public class ConnectionAnalyzer {

    private final Map<String, Deque<Long>> connectionTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> firstConnectionTime = new ConcurrentHashMap<>();
    private final int windowSeconds;
    private final int suspiciousThreshold;

    /** Grace period: ilk bağlantıdan bu kadar ms sonrasına kadar şüpheli sayma */
    private static final long GRACE_PERIOD_MS = 5_000L;

    public ConnectionAnalyzer(int windowSeconds, int suspiciousThreshold) {
        this.windowSeconds = windowSeconds;
        // Minimum 8 — çok düşük eşik false positive oluşturur
        this.suspiciousThreshold = Math.max(8, suspiciousThreshold);
    }

    public void recordConnection(String ip) {
        long now = System.currentTimeMillis();
        firstConnectionTime.putIfAbsent(ip, now);
        Deque<Long> times = connectionTimes.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (times) {
            pruneOld(times, now);
            times.addLast(now);
        }
    }

    public boolean isSuspicious(String ip) {
        // Grace period içindeyse şüpheli sayma
        Long firstConn = firstConnectionTime.get(ip);
        if (firstConn != null && System.currentTimeMillis() - firstConn < GRACE_PERIOD_MS) {
            return false;
        }

        Deque<Long> times = connectionTimes.get(ip);
        if (times == null) return false;
        long now = System.currentTimeMillis();
        synchronized (times) {
            pruneOld(times, now);
            return times.size() >= suspiciousThreshold;
        }
    }

    /**
     * Smoothed connection rate: gerçek zaman aralığına bölünmüş hesaplama.
     */
    public double getConnectionRate(String ip) {
        Deque<Long> times = connectionTimes.get(ip);
        if (times == null) return 0.0;
        long now = System.currentTimeMillis();
        synchronized (times) {
            pruneOld(times, now);
            if (times.size() < 2) return (double) times.size() / windowSeconds;
            // Gerçek zaman aralığını kullan
            long oldest = times.peekFirst();
            long elapsed = Math.max(1, now - oldest) / 1000L;
            return (double) times.size() / elapsed;
        }
    }

    private void pruneOld(Deque<Long> times, long now) {
        long cutoff = now - (windowSeconds * 1000L);
        while (!times.isEmpty() && times.peekFirst() < cutoff) times.pollFirst();
    }

    public void cleanup() {
        long cutoff = System.currentTimeMillis() - (windowSeconds * 1000L * 2);
        connectionTimes.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || e.getValue().peekFirst() < cutoff;
            }
        });
        firstConnectionTime.entrySet().removeIf(e -> e.getValue() < cutoff);
    }
}
