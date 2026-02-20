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

    public RateLimitResult check(String ip) {
        long now = System.currentTimeMillis();
        
        // Reset if window passed
        Long lastReset = ipLastReset.get(ip);
        if (lastReset == null || (now - lastReset > windowMs)) {
            ipCounts.put(ip, new AtomicInteger(0));
            ipLastReset.put(ip, now);
            lastReset = now;
        }

        AtomicInteger count = ipCounts.computeIfAbsent(ip, k -> new AtomicInteger(0));
        if (count.incrementAndGet() <= limit) {
            return RateLimitResult.ALLOWED;
        } else {
            long retryAfter = windowMs - (now - lastReset);
            return new RateLimitResult(false, Math.max(0, retryAfter));
        }
    }

    public static record RateLimitResult(boolean allowed, long retryAfterMs) {
        public static final RateLimitResult ALLOWED = new RateLimitResult(true, 0);
    }

    public boolean allowConnection(String ip) {
        return check(ip).allowed();
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        ipLastReset.entrySet().removeIf(entry -> (now - entry.getValue()) > windowMs * 2); // Remove old entries
        ipCounts.keySet().retainAll(ipLastReset.keySet());
    }
}