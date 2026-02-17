package com.atomguard.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe Token Bucket algoritması implementasyonu.
 * Bukkit'ten bağımsız, unit test edilebilir tasarım.
 *
 * Her kova belirli bir kapasite ve dolum oranına sahiptir.
 * Token tüketildiğinde negatife düşebilir — bu flood tespiti için kullanılır.
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class TokenBucket {

    private final long capacity;
    private final long refillPerSecond;
    private final AtomicLong tokens;
    private final AtomicLong lastRefillTimeMs;

    /**
     * Yeni bir Token Bucket oluşturur
     *
     * @param capacity       Maksimum token kapasitesi
     * @param refillPerSecond Saniyede eklenecek token sayısı
     */
    public TokenBucket(long capacity, long refillPerSecond) {
        if (capacity <= 0) throw new IllegalArgumentException("Kapasite 0'dan büyük olmalı");
        if (refillPerSecond <= 0) throw new IllegalArgumentException("Dolum oranı 0'dan büyük olmalı");

        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.tokens = new AtomicLong(capacity);
        this.lastRefillTimeMs = new AtomicLong(System.currentTimeMillis());
    }

    /**
     * Bir token tüketmeye çalışır. Önce otomatik dolum yapar.
     *
     * @return Tüketme sonrası kalan token sayısı (negatif = token yok, flood göstergesi)
     */
    public long tryConsume() {
        refill();
        return tokens.decrementAndGet();
    }

    /**
     * Geçen süreye göre token'ları yeniden doldurur.
     * CAS (Compare-And-Swap) ile thread-safe dolum sağlar.
     */
    private void refill() {
        long now = System.currentTimeMillis();
        long last = lastRefillTimeMs.get();
        long elapsed = now - last;

        // En az 1 saniye geçmişse dolum yap
        if (elapsed >= 1000) {
            // CAS ile dolum hakkını kazanmayı dene
            if (lastRefillTimeMs.compareAndSet(last, now)) {
                long secondsPassed = elapsed / 1000;
                long tokensToAdd = secondsPassed * refillPerSecond;

                // Kapasiteyi aşmadan token ekle (CAS döngüsü)
                long current;
                long newVal;
                do {
                    current = tokens.get();
                    newVal = Math.min(capacity, current + tokensToAdd);
                } while (!tokens.compareAndSet(current, newVal));
            }
        }
    }

    /**
     * Mevcut token sayısını döndürür (dolum sonrası)
     *
     * @return Kalan token sayısı
     */
    public long getTokens() {
        refill();
        return tokens.get();
    }

    /**
     * Kova kapasitesini döndürür
     *
     * @return Maksimum kapasite
     */
    public long getCapacity() {
        return capacity;
    }

    /**
     * Saniyedeki dolum oranını döndürür
     *
     * @return Dolum oranı (token/sn)
     */
    public long getRefillPerSecond() {
        return refillPerSecond;
    }

    /**
     * Kovayı tam kapasiteye sıfırlar
     */
    public void reset() {
        tokens.set(capacity);
        lastRefillTimeMs.set(System.currentTimeMillis());
    }

    @Override
    public String toString() {
        return String.format("TokenBucket{capacity=%d, refill=%d/s, tokens=%d}",
                capacity, refillPerSecond, tokens.get());
    }
}
