package com.atomguard.velocity.module.protocol;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Geçersiz/malformed paket sayacı ve engelleyici.
 */
public class InvalidPacketFilter {

    private final ConcurrentHashMap<String, AtomicInteger> invalidCounts = new ConcurrentHashMap<>();
    private final int maxInvalidPackets;
    private final long windowMs;
    private final ConcurrentHashMap<String, Long> windows = new ConcurrentHashMap<>();

    public InvalidPacketFilter(int maxInvalidPackets, long windowMs) {
        this.maxInvalidPackets = maxInvalidPackets;
        this.windowMs = windowMs;
    }

    public boolean recordAndCheck(String ip) {
        long now = System.currentTimeMillis();
        windows.compute(ip, (k, v) -> {
            if (v == null || now - v > windowMs) {
                invalidCounts.put(ip, new AtomicInteger(0));
                return now;
            }
            return v;
        });

        int count = invalidCounts.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
        return count >= maxInvalidPackets;
    }

    public void reset(String ip) {
        invalidCounts.remove(ip);
        windows.remove(ip);
    }

    public void cleanup() {
        long cutoff = System.currentTimeMillis() - windowMs;
        windows.entrySet().removeIf(e -> {
            if (e.getValue() < cutoff) { invalidCounts.remove(e.getKey()); return true; }
            return false;
        });
    }
}
