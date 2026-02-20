package com.atomguard.velocity.module.antiddos;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Geçersiz/malformed handshake paketlerini tespit eder.
 * <p>
 * Caffeine cache kullanarak memory-leak koruması sağlar.
 * Ek olarak ConnectionFingerprinter ile entegre parmak izi doğrulaması destekler.
 */
public class NullPingDetector {

    private static final int  BLOCK_THRESHOLD  = 5;
    private static final long BLOCK_DURATION_MS = 300_000L; // 5 dakika

    /** IP → geçersiz handshake sayısı (10 dakika TTL) */
    private final Cache<String, AtomicInteger> invalidCounts = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(100_000)
            .build();

    /** Engellenen IP'ler (5 dakika ban) */
    private final Cache<String, Boolean> blockedIPs = Caffeine.newBuilder()
            .expireAfterWrite(BLOCK_DURATION_MS, TimeUnit.MILLISECONDS)
            .maximumSize(10_000)
            .build();

    /**
     * Handshake parametrelerinin geçerliliğini kontrol et.
     *
     * @param hostname       Bağlanılan hostname
     * @param port           Bağlanılan port
     * @param protocolVersion Minecraft protokol versiyonu
     * @return true ise geçerli handshake
     */
    public boolean isValidHandshake(String hostname, int port, int protocolVersion) {
        if (hostname == null || hostname.isBlank()) return false;
        if (hostname.length() > 255) return false;
        if (port < 1 || port > 65535) return false;
        if (protocolVersion < 0) return false;
        return true;
    }

    /**
     * Geçersiz handshake kaydet.
     * Eşik aşılırsa IP engellenir.
     *
     * @param ip Kaynak IP
     */
    public void recordInvalid(String ip) {
        AtomicInteger count = invalidCounts.get(ip, k -> new AtomicInteger(0));
        if (count.incrementAndGet() >= BLOCK_THRESHOLD) {
            blockedIPs.put(ip, Boolean.TRUE);
        }
    }

    /**
     * IP engelli mi?
     */
    public boolean isBlocked(String ip) {
        return blockedIPs.getIfPresent(ip) != null;
    }

    /**
     * IP'nin geçersiz handshake sayısını döndür.
     */
    public int getInvalidCount(String ip) {
        AtomicInteger c = invalidCounts.getIfPresent(ip);
        return c != null ? c.get() : 0;
    }

    /**
     * Periyodik temizlik — Caffeine otomatik temizler;
     * bu metod compatibility için korunmuştur.
     */
    public void cleanup() {
        invalidCounts.cleanUp();
        blockedIPs.cleanUp();
    }
}
