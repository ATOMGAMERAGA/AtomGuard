package com.atomguard.velocity.module.antiddos;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Geçersiz/malformed handshake paketlerini tespit eder.
 */
public class NullPingDetector {

    private final Set<String> blocked = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ConcurrentHashMap<String, AtomicInteger> invalidCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> blockTimes = new ConcurrentHashMap<>();
    private static final int BLOCK_THRESHOLD = 5;
    private static final long BLOCK_DURATION_MS = 300_000L;

    public boolean isValidHandshake(String hostname, int port, int protocolVersion) {
        if (hostname == null || hostname.isBlank()) return false;
        if (hostname.length() > 255) return false;
        if (port < 1 || port > 65535) return false;
        if (protocolVersion < 0) return false;
        return true;
    }

    public void recordInvalid(String ip) {
        int count = invalidCounts.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
        if (count >= BLOCK_THRESHOLD) {
            blocked.add(ip);
            blockTimes.put(ip, System.currentTimeMillis());
        }
    }

    public boolean isBlocked(String ip) { return blocked.contains(ip); }

    public int getInvalidCount(String ip) {
        AtomicInteger c = invalidCounts.get(ip);
        return c != null ? c.get() : 0;
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        blockTimes.entrySet().removeIf(e -> {
            if (now - e.getValue() > BLOCK_DURATION_MS) {
                blocked.remove(e.getKey());
                invalidCounts.remove(e.getKey());
                return true;
            }
            return false;
        });
    }
}
