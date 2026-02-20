package com.atomguard.velocity.module.antiddos;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ping/status flood tespiti ve MOTD önbelleği.
 * <p>
 * Caffeine cache kullanır — memory-leak yok, otomatik temizlenir.
 */
public class PingFloodDetector {

    private final RateLimiter rateLimiter;

    /** IP → MOTD önbellek bitiş zamanı (Caffeine TTL ile yönetilir) */
    private final Cache<String, Boolean> motdCache;

    /** Anlık ping sayacı (saniyede sıfırlanır) */
    private final AtomicInteger pingsThisSecond = new AtomicInteger(0);

    public PingFloodDetector(int maxPingsPerSecond) {
        this.rateLimiter = new RateLimiter(maxPingsPerSecond, maxPingsPerSecond);
        this.motdCache   = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.SECONDS) // Varsayılan MOTD TTL
                .maximumSize(100_000)
                .build();
    }

    /**
     * Ping isteğine izin ver ya da reddet.
     *
     * @param ip Kaynak IP
     * @return true ise ping'e izin verildi
     */
    public boolean allowPing(String ip) {
        boolean allowed = rateLimiter.tryAcquire(ip);
        if (allowed) pingsThisSecond.incrementAndGet();
        return allowed;
    }

    /**
     * Bu IP için MOTD önbellekte var mı?
     */
    public boolean hasCachedMotd(String ip) {
        return motdCache.getIfPresent(ip) != null;
    }

    /**
     * Bu IP için MOTD'yi önbelleğe al.
     *
     * @param ip    Kaynak IP
     * @param ttlMs Önbellek geçerlilik süresi (ms) — not: Caffeine TTL sabittir,
     *              bu parametre uyumluluk için korunmuştur
     */
    public void cacheMotd(String ip, long ttlMs) {
        motdCache.put(ip, Boolean.TRUE);
    }

    /**
     * Anlık ping sayısını döndür.
     */
    public int getPingsThisSecond() {
        return pingsThisSecond.get();
    }

    /**
     * Ping sayacını sıfırla (her saniye çağrılır).
     */
    public int resetAndGetPings() {
        return pingsThisSecond.getAndSet(0);
    }

    /**
     * Periyodik temizlik — Caffeine otomatik temizler;
     * bu metod compatibility için korunmuştur.
     */
    public void cleanup() {
        motdCache.cleanUp();
        rateLimiter.cleanup();
    }
}
