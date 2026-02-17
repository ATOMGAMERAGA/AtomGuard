package com.atomguard.velocity.module.antiddos;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Ping/status flood tespiti ve MOTD önbelleği.
 */
public class PingFloodDetector {

    private final RateLimiter rateLimiter;
    private final ConcurrentHashMap<String, CacheEntry> motdCache = new ConcurrentHashMap<>();

    private record CacheEntry(long expiry) {
        boolean isExpired() { return System.currentTimeMillis() > expiry; }
    }

    public PingFloodDetector(int maxPingsPerSecond) {
        this.rateLimiter = new RateLimiter(maxPingsPerSecond, maxPingsPerSecond);
    }

    public boolean allowPing(String ip) {
        return rateLimiter.tryAcquire(ip);
    }

    public boolean hasCachedMotd(String ip) {
        CacheEntry e = motdCache.get(ip);
        return e != null && !e.isExpired();
    }

    public void cacheMotd(String ip, long ttlMs) {
        motdCache.put(ip, new CacheEntry(System.currentTimeMillis() + ttlMs));
    }

    public void cleanup() {
        motdCache.entrySet().removeIf(e -> e.getValue().isExpired());
        rateLimiter.cleanup();
    }
}
