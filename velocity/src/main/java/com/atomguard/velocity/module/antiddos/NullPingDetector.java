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
    private static final int BLOCK_THRESHOLD = 5;

    public boolean isValidHandshake(String hostname, int port, int protocolVersion) {
        if (hostname == null || hostname.isBlank()) return false;
        if (hostname.length() > 255) return false;
        if (port < 1 || port > 65535) return false;
        if (protocolVersion < 0) return false;
        return true;
    }

    public void recordInvalid(String ip) {
        int count = invalidCounts.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
        if (count >= BLOCK_THRESHOLD) blocked.add(ip);
    }

    public boolean isBlocked(String ip) { return blocked.contains(ip); }

    public int getInvalidCount(String ip) {
        AtomicInteger c = invalidCounts.get(ip);
        return c != null ? c.get() : 0;
    }

    public void cleanup() {
        // Periyodik temizlik: her ~100 kontrolde bir
        if (Math.random() < 0.01) {
            blocked.clear();
            invalidCounts.clear();
        }
    }
}
