package com.atomguard.velocity.module.antiddos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token bucket hız sınırlayıcı - her anahtar için ayrı kova.
 */
public class RateLimiter {

    private final int capacity;
    private final int refillPerSecond;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int capacity, int refillPerSecond) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
    }

    public boolean tryAcquire(String key) {
        return buckets.computeIfAbsent(key, k -> new Bucket(capacity, refillPerSecond)).tryConsume();
    }

    public boolean tryAcquire(String key, int tokens) {
        return buckets.computeIfAbsent(key, k -> new Bucket(capacity, refillPerSecond)).tryConsume(tokens);
    }

    public void cleanup() {
        long cutoff = System.currentTimeMillis() - 300_000L;
        buckets.entrySet().removeIf(e -> e.getValue().lastAccess < cutoff);
    }

    public void reset(String key) { buckets.remove(key); }

    public int getTokens(String key) {
        Bucket b = buckets.get(key);
        return b != null ? (int) b.getAvailable() : capacity;
    }

    private static final class Bucket {
        private double tokens;
        private final int capacity;
        private final double refillPerMs;
        private long lastRefill;
        volatile long lastAccess;

        Bucket(int capacity, int refillPerSecond) {
            this.capacity = capacity;
            this.tokens = capacity;
            this.refillPerMs = refillPerSecond / 1000.0;
            this.lastRefill = System.currentTimeMillis();
            this.lastAccess = lastRefill;
        }

        synchronized boolean tryConsume() { return tryConsume(1); }

        synchronized boolean tryConsume(int amount) {
            refill();
            lastAccess = System.currentTimeMillis();
            if (tokens >= amount) { tokens -= amount; return true; }
            return false;
        }

        synchronized long getAvailable() { refill(); return (long) tokens; }

        private void refill() {
            long now = System.currentTimeMillis();
            double toAdd = (now - lastRefill) * refillPerMs;
            tokens = Math.min(capacity, tokens + toAdd);
            lastRefill = now;
        }
    }
}
