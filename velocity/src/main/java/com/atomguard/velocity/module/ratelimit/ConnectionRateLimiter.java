package com.atomguard.velocity.module.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionRateLimiter {

    private final Map<String, AtomicInteger> ipCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> ipLastReset = new ConcurrentHashMap<>();
    private final int limit;
    private final long windowMs;

    public ConnectionRateLimiter(int limit, long windowSeconds) {
        this.limit = limit;
        this.windowMs = windowSeconds * 1000;
    }

    public boolean allowConnection(String ip) {
        long now = System.currentTimeMillis();
        
        // Reset if window passed
        ipLastReset.compute(ip, (k, last) -> {
            if (last == null || (now - last > windowMs)) {
                ipCounts.put(ip, new AtomicInteger(0));
                return now;
            }
            return last;
        });

        AtomicInteger count = ipCounts.computeIfAbsent(ip, k -> new AtomicInteger(0));
        return count.incrementAndGet() <= limit;
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        ipLastReset.entrySet().removeIf(entry -> (now - entry.getValue()) > windowMs * 2); // Remove old entries
        ipCounts.keySet().retainAll(ipLastReset.keySet());
    }
}