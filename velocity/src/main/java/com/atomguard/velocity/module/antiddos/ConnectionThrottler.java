package com.atomguard.velocity.module.antiddos;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IP başına bağlantı hız sınırlayıcı (1 dakikalık pencere).
 * <p>
 * Caffeine cache sayesinde otomatik temizlenir — memory leak yok.
 * Saldırı modunda limit yarıya düşer.
 * AttackLevel tabanlı dinamik limit desteği mevcuttur.
 */
public class ConnectionThrottler {

    private final int limitPerMinute;

    /**
     * IP → dakikadaki bağlantı sayısı.
     * TTL: 70 saniye (1 dakika pencere + tolerans)
     */
    private final Cache<String, AtomicInteger> minuteCounters = Caffeine.newBuilder()
            .expireAfterWrite(70, TimeUnit.SECONDS)
            .maximumSize(500_000)
            .build();

    /**
     * @param limitPerMinute IP başına 1 dakikadaki maksimum bağlantı sayısı
     */
    public ConnectionThrottler(int limitPerMinute) {
        this.limitPerMinute = limitPerMinute;
    }

    /**
     * Normal modda bağlantı denemesi.
     *
     * @param ip Kaynak IP
     * @return true ise izin verildi
     */
    public boolean tryConnect(String ip) {
        return check(ip, limitPerMinute);
    }

    /**
     * Saldırı modunda bağlantı denemesi (limit yarıya düşer).
     *
     * @param ip Kaynak IP
     * @return true ise izin verildi
     */
    public boolean tryConnectAttackMode(String ip) {
        int attackLimit = Math.max(1, limitPerMinute / 2);
        return check(ip, attackLimit);
    }

    /**
     * Belirtilen limitle bağlantı denemesi (AttackLevel'e göre dinamik).
     *
     * @param ip    Kaynak IP
     * @param limit Uygulanan limit
     * @return true ise izin verildi
     */
    public boolean tryConnectWithLimit(String ip, int limit) {
        return check(ip, limit);
    }

    private boolean check(String ip, int limit) {
        AtomicInteger counter = minuteCounters.get(ip, k -> new AtomicInteger(0));
        return counter.incrementAndGet() <= limit;
    }

    /**
     * IP'nin mevcut dakikadaki bağlantı sayısını döndür.
     */
    public int getConnectionCount(String ip) {
        AtomicInteger c = minuteCounters.getIfPresent(ip);
        return c != null ? c.get() : 0;
    }

    /**
     * IP sayacını sıfırla.
     */
    public void reset(String ip) {
        minuteCounters.invalidate(ip);
    }

    /**
     * Manuel temizlik — Caffeine otomatik temizler; compatibility için korunur.
     */
    public void cleanup() {
        minuteCounters.cleanUp();
    }

    /** Takip edilen IP sayısı. */
    public long getTrackedIPCount() {
        return minuteCounters.estimatedSize();
    }
}
