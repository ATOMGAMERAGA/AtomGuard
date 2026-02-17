package com.atomguard.velocity.module.antiddos;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Slowloris saldırı tespiti - tamamlanmamış bağlantıları izler.
 */
public class SlowlorisDetector {

    private final ConcurrentHashMap<String, AtomicInteger> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> startTimes = new ConcurrentHashMap<>();
    private final int maxPendingPerIP;
    private final long timeoutMs;

    public SlowlorisDetector(int maxPendingPerIP, long timeoutMs) {
        this.maxPendingPerIP = maxPendingPerIP;
        this.timeoutMs = timeoutMs;
    }

    public void onConnectionStarted(String ip, String connId) {
        pending.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
        startTimes.put(connId, System.currentTimeMillis());
    }

    public void onHandshakeComplete(String ip, String connId) {
        AtomicInteger count = pending.get(ip);
        if (count != null) count.decrementAndGet();
        startTimes.remove(connId);
    }

    public void onConnectionClosed(String ip, String connId) {
        AtomicInteger count = pending.get(ip);
        if (count != null && count.decrementAndGet() <= 0) pending.remove(ip);
        startTimes.remove(connId);
    }

    public boolean isSlowlorisIP(String ip) {
        AtomicInteger count = pending.get(ip);
        return count != null && count.get() >= maxPendingPerIP;
    }

    public void cleanupExpiredConnections() {
        long cutoff = System.currentTimeMillis() - timeoutMs;
        startTimes.entrySet().removeIf(e -> e.getValue() < cutoff);
        pending.entrySet().removeIf(e -> e.getValue().get() <= 0);
    }

    public int getPendingCount(String ip) {
        AtomicInteger c = pending.get(ip);
        return c != null ? c.get() : 0;
    }
}
