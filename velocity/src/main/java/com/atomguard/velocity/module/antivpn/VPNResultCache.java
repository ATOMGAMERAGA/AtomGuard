package com.atomguard.velocity.module.antivpn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VPN tespit sonuçları için TTL önbelleği.
 */
public class VPNResultCache {

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long ttlMs;
    private final int maxSize;

    public VPNResultCache(long ttlMs) {
        this(ttlMs, 10000); // Varsayılan 10k kayıt
    }

    public VPNResultCache(long ttlMs, int maxSize) {
        this.ttlMs = ttlMs;
        this.maxSize = maxSize;
    }

    public void put(String ip, boolean isVPN, String provider) {
        if (cache.size() >= maxSize && !cache.containsKey(ip)) {
            evictOldest();
        }
        cache.put(ip, new CacheEntry(isVPN, provider, System.currentTimeMillis() + ttlMs, System.currentTimeMillis()));
    }

    private void evictOldest() {
        cache.entrySet().stream()
            .min(java.util.Comparator.comparingLong(e -> e.getValue().timestamp()))
            .ifPresent(e -> cache.remove(e.getKey()));
    }

    public CacheResult get(String ip) {
        CacheEntry entry = cache.get(ip);
        if (entry == null) return null;
        if (System.currentTimeMillis() > entry.expiry()) {
            cache.remove(ip);
            return null;
        }
        return new CacheResult(entry.isVPN(), entry.provider());
    }

    public boolean contains(String ip) {
        CacheEntry entry = cache.get(ip);
        if (entry == null) return false;
        if (System.currentTimeMillis() > entry.expiry()) { cache.remove(ip); return false; }
        return true;
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> e.getValue().expiry() < now);
    }

    public int size() { return cache.size(); }

    private record CacheEntry(boolean isVPN, String provider, long expiry, long timestamp) {}
    public record CacheResult(boolean isVPN, String provider) {}
}
