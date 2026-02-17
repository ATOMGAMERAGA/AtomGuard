package com.atomguard.velocity.module.antiddos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kayan pencere ile IP başına bağlantı hız sınırlaması.
 */
public class ConnectionThrottler {

    private final int limitPerMinute;
    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    public ConnectionThrottler(int limitPerMinute) {
        this.limitPerMinute = limitPerMinute;
    }

    public boolean tryConnect(String ip) {
        return check(ip, limitPerMinute);
    }

    public boolean tryConnectAttackMode(String ip) {
        return check(ip, limitPerMinute / 2);
    }

    private boolean check(String ip, int limit) {
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L;
        Deque<Long> times = windows.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() < windowStart) times.pollFirst();
            if (times.size() >= limit) return false;
            times.addLast(now);
            return true;
        }
    }

    public void cleanup() {
        long windowStart = System.currentTimeMillis() - 60_000L;
        windows.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                while (!e.getValue().isEmpty() && e.getValue().peekFirst() < windowStart) e.getValue().pollFirst();
                return e.getValue().isEmpty();
            }
        });
    }

    public int getConnectionCount(String ip) {
        Deque<Long> times = windows.get(ip);
        if (times == null) return 0;
        long windowStart = System.currentTimeMillis() - 60_000L;
        synchronized (times) {
            return (int) times.stream().filter(t -> t >= windowStart).count();
        }
    }
}
