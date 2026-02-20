package com.atomguard.velocity.module.antiddos;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token Bucket tabanlı hız sınırlayıcı.
 * <p>
 * Caffeine cache kullanarak memory-leak koruması sağlar.
 * ConcurrentHashMap yerine Caffeine tercih edilmiştir:
 * <ul>
 *   <li>Otomatik TTL temizliği (manuel cleanup gerekmez)</li>
 *   <li>Maksimum boyut sınırı (unbounded büyüme yok)</li>
 *   <li>Daha yüksek concurrency performansı</li>
 * </ul>
 */
public class RateLimiter {

    private final int capacity;
    private final int refillPerSecond;

    /** Anahtar → Token bucket (5 dakika hareketsizlikte otomatik temizlenir) */
    private final Cache<String, Bucket> buckets;

    /**
     * @param capacity        Maksimum token kapasitesi
     * @param refillPerSecond Saniyede yenilenen token sayısı
     */
    public RateLimiter(int capacity, int refillPerSecond) {
        this.capacity        = capacity;
        this.refillPerSecond = refillPerSecond;
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(5, TimeUnit.MINUTES)
                .maximumSize(500_000)
                .build();
    }

    /**
     * 1 token tüketmeyi dene.
     *
     * @param key Anahtar (genellikle IP adresi)
     * @return true ise token alındı, false ise sınıra ulaşıldı
     */
    public boolean tryAcquire(String key) {
        return buckets.get(key, k -> new Bucket(capacity, refillPerSecond)).tryConsume(1);
    }

    /**
     * N token tüketmeyi dene.
     *
     * @param key    Anahtar
     * @param tokens Tüketilecek token sayısı
     * @return true ise yeterli token mevcut
     */
    public boolean tryAcquire(String key, int tokens) {
        return buckets.get(key, k -> new Bucket(capacity, refillPerSecond)).tryConsume(tokens);
    }

    /**
     * Anahtarın bucket'ını sıfırla.
     */
    public void reset(String key) {
        buckets.invalidate(key);
    }

    /**
     * Belirtilen anahtarın mevcut token sayısını döndür.
     */
    public int getTokens(String key) {
        Bucket b = buckets.getIfPresent(key);
        return b != null ? (int) b.getAvailable() : capacity;
    }

    /**
     * Manuel temizlik — Caffeine otomatik temizler; compatibility için korunur.
     */
    public void cleanup() {
        buckets.cleanUp();
    }

    // ────────────────────────────────────────────────────────
    // Token Bucket implementasyonu
    // ────────────────────────────────────────────────────────

    private static final class Bucket {
        private double tokens;
        private final int capacity;
        private final double refillPerMs;
        private final AtomicLong lastRefill;

        Bucket(int capacity, int refillPerSecond) {
            this.capacity    = capacity;
            this.tokens      = capacity;
            this.refillPerMs = refillPerSecond / 1000.0;
            this.lastRefill  = new AtomicLong(System.currentTimeMillis());
        }

        synchronized boolean tryConsume(int amount) {
            refill();
            if (tokens >= amount) {
                tokens -= amount;
                return true;
            }
            return false;
        }

        synchronized long getAvailable() {
            refill();
            return (long) tokens;
        }

        private void refill() {
            long now  = System.currentTimeMillis();
            long last = lastRefill.getAndSet(now);
            double toAdd = (now - last) * refillPerMs;
            tokens = Math.min(capacity, tokens + toAdd);
        }
    }
}
