package com.atomguard.velocity.module.fastchat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ChatRateLimiter {
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final double refillRate; // tokens per second
    private final double capacity;

    public ChatRateLimiter(double refillRate, double capacity) {
        this.refillRate = refillRate;
        this.capacity = capacity;
    }

    public boolean tryConsume(String ip) {
        return buckets.computeIfAbsent(ip, k -> new TokenBucket(capacity, refillRate)).tryConsume();
    }
    
    public void cleanup() {
        long now = System.nanoTime();
        buckets.entrySet().removeIf(entry -> entry.getValue().canBeRemoved(now));
    }

    private static class TokenBucket {
        private final double capacity;
        private final double refillRate;
        private double tokens;
        private long lastRefillTimestamp;
        private long lastAccessTimestamp;

        public TokenBucket(double capacity, double refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.tokens = capacity;
            this.lastRefillTimestamp = System.nanoTime();
            this.lastAccessTimestamp = System.nanoTime();
        }

        public synchronized boolean tryConsume() {
            refill();
            lastAccessTimestamp = System.nanoTime();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            double duration = (now - lastRefillTimestamp) / 1_000_000_000.0;
            if (duration <= 0) return;

            double tokensToAdd = duration * refillRate;
            tokens = Math.min(capacity, tokens + tokensToAdd);
            lastRefillTimestamp = now;
        }

        public synchronized boolean canBeRemoved(long now) {
            // Remove if unused for 5 minutes
            return (now - lastAccessTimestamp) > TimeUnit.MINUTES.toNanos(5);
        }
    }
}