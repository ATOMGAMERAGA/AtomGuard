package com.atomguard.velocity.module.antivpn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VPN tespit sonuçları için TTL önbelleği.
 */
public class VPNResultCache {

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long ttlMs;

    public VPNResultCache(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    public void put(String ip, boolean isVPN, String provider) {
        cache.put(ip, new CacheEntry(isVPN, provider, System.currentTimeMillis() + ttlMs));
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

    private record CacheEntry(boolean isVPN, String provider, long expiry) {}
    public record CacheResult(boolean isVPN, String provider) {}
}
