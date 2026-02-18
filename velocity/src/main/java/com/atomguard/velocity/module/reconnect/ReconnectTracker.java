package com.atomguard.velocity.module.reconnect;

import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ReconnectTracker {

    private final Map<String, Long> lastConnect = new ConcurrentHashMap<>();
    private final Map<String, Long> lastDisconnect = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<Long>> crashHistory = new ConcurrentHashMap<>();
    private final Map<String, Integer> shortSessionCount = new ConcurrentHashMap<>();

    public void recordConnect(String ip) {
        lastConnect.put(ip, System.currentTimeMillis());
    }

    public void recordDisconnect(String ip) {
        long now = System.currentTimeMillis();
        lastDisconnect.put(ip, now);
        
        // Record for crash loop analysis
        crashHistory.computeIfAbsent(ip, k -> new LinkedList<>()).add(now);
    }

    public void recordShortSession(String ip) {
        shortSessionCount.merge(ip, 1, Integer::sum);
    }

    public long getCooldownRemaining(String ip, long cooldownMs) {
        Long lastDisc = lastDisconnect.get(ip);
        if (lastDisc == null) return 0;
        
        long passed = System.currentTimeMillis() - lastDisc;
        return passed < cooldownMs ? (cooldownMs - passed) : 0;
    }

    public boolean isCrashLoop(String ip, int threshold, long windowMs) {
        LinkedList<Long> history = crashHistory.get(ip);
        if (history == null) return false;

        long now = System.currentTimeMillis();
        synchronized (history) {
            // Remove old entries
            history.removeIf(time -> (now - time) > windowMs);
            return history.size() >= threshold;
        }
    }

    public long getLastConnectTime(String ip) {
        return lastConnect.getOrDefault(ip, 0L);
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        lastConnect.entrySet().removeIf(e -> (now - e.getValue()) > TimeUnit.MINUTES.toMillis(10));
        lastDisconnect.entrySet().removeIf(e -> (now - e.getValue()) > TimeUnit.MINUTES.toMillis(10));
        crashHistory.entrySet().removeIf(e -> (now - e.getValue().getLast()) > TimeUnit.MINUTES.toMillis(5));
        shortSessionCount.clear();
    }
    
    public void clear() {
        lastConnect.clear();
        lastDisconnect.clear();
        crashHistory.clear();
        shortSessionCount.clear();
    }
}