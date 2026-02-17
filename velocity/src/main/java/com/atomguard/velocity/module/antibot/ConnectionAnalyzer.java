package com.atomguard.velocity.module.antibot;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP başına bağlantı davranışı analizi.
 */
public class ConnectionAnalyzer {

    private final Map<String, Deque<Long>> connectionTimes = new ConcurrentHashMap<>();
    private final int windowSeconds;
    private final int suspiciousThreshold;

    public ConnectionAnalyzer(int windowSeconds, int suspiciousThreshold) {
        this.windowSeconds = windowSeconds;
        this.suspiciousThreshold = suspiciousThreshold;
    }

    public void recordConnection(String ip) {
        long now = System.currentTimeMillis();
        Deque<Long> times = connectionTimes.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (times) {
            pruneOld(times, now);
            times.addLast(now);
        }
    }

    public boolean isSuspicious(String ip) {
        Deque<Long> times = connectionTimes.get(ip);
        if (times == null) return false;
        long now = System.currentTimeMillis();
        synchronized (times) {
            pruneOld(times, now);
            return times.size() >= suspiciousThreshold;
        }
    }

    public double getConnectionRate(String ip) {
        Deque<Long> times = connectionTimes.get(ip);
        if (times == null) return 0.0;
        long now = System.currentTimeMillis();
        synchronized (times) {
            pruneOld(times, now);
            return (double) times.size() / windowSeconds;
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
    }
}
