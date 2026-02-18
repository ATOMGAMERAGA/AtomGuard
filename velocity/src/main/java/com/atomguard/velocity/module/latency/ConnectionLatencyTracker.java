package com.atomguard.velocity.module.latency;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionLatencyTracker {

    private final Map<String, Long> handshakeTimes = new ConcurrentHashMap<>();
    private final Map<String, LatencyProfile> profiles = new ConcurrentHashMap<>();

    public void recordHandshake(String ip) {
        handshakeTimes.put(ip, System.nanoTime());
    }

    public long recordLogin(String ip) {
        Long start = handshakeTimes.remove(ip);
        if (start == null) return -1;
        long durationNs = System.nanoTime() - start;
        long durationMs = durationNs / 1_000_000;
        
        profiles.computeIfAbsent(ip, LatencyProfile::new).addMeasurement(durationMs);
        return durationMs;
    }

    public LatencyProfile getProfile(String ip) {
        return profiles.get(ip);
    }
}