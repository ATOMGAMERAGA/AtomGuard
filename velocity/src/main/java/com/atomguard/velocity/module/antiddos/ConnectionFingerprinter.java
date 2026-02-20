package com.atomguard.velocity.module.antiddos;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bağlantı parmak izi sistemi.
 * <p>
 * Her bağlantının parmak izini çıkarır ve kitlesel eşleşmeleri,
 * bot army'lerini tespit eder.
 * <p>
 * Parmak izi bileşenleri:
 * <ul>
 *   <li>Protokol versiyonu</li>
 *   <li>Hostname pattern (normalize edilmiş)</li>
 *   <li>Handshake timing sınıfı (hızlı/normal/yavaş)</li>
 * </ul>
 */
public class ConnectionFingerprinter {

    // ────────────────────────────────────────────────────────
    // Konfigürasyon
    // ────────────────────────────────────────────────────────

    private final int massConnThreshold;           // Aynı parmak izinden bu kadar → işaretle
    private final List<String> knownBotPatterns;  // Bilinen botnet parmak izi kalıpları

    // ────────────────────────────────────────────────────────
    // State
    // ────────────────────────────────────────────────────────

    /** Parmak izi → bağlantı sayısı (10 dakika TTL) */
    private final Cache<String, AtomicInteger> fingerprintCounts = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(50_000)
            .build();

    /** IP → son parmak izi */
    private final Cache<String, String> ipFingerprints = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(100_000)
            .build();

    /** İşaretlenen parmak izleri */
    private final Set<String> flaggedFingerprints = ConcurrentHashMap.newKeySet();

    /** Toplam bot army tespiti sayısı */
    private final AtomicLong botArmyDetections = new AtomicLong(0);

    public ConnectionFingerprinter(int massConnThreshold, List<String> knownBotPatterns) {
        this.massConnThreshold = massConnThreshold;
        this.knownBotPatterns  = knownBotPatterns != null ? List.copyOf(knownBotPatterns) : List.of();
    }

    // ────────────────────────────────────────────────────────
    // Parmak izi oluşturma
    // ────────────────────────────────────────────────────────

    /**
     * Bağlantı bilgilerinden parmak izi oluştur ve kaydet.
     *
     * @param ip              Kaynak IP
     * @param hostname        Handshake hostname'i
     * @param protocolVersion Minecraft protokol versiyonu
     * @param handshakeTimeMs Handshake tamamlanma süresi (ms), -1 ise ölçülemedi
     * @return Oluşturulan parmak izi string'i
     */
    public String recordAndGetFingerprint(String ip, String hostname,
                                          int protocolVersion, long handshakeTimeMs) {
        String fp = buildFingerprint(hostname, protocolVersion, handshakeTimeMs);
        ipFingerprints.put(ip, fp);

        // Sayaç güncelle
        AtomicInteger counter = fingerprintCounts.get(fp, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();

        // Bot army kontrolü
        if (count >= massConnThreshold && !flaggedFingerprints.contains(fp)) {
            flaggedFingerprints.add(fp);
            botArmyDetections.incrementAndGet();
        }

        return fp;
    }

    /**
     * Parmak izi string'ini oluştur.
     * Format: "protocol|hostname_pattern|timing_class"
     */
    private String buildFingerprint(String hostname, int protocolVersion, long handshakeTimeMs) {
        String hostnamePattern = normalizeHostname(hostname);
        String timingClass     = classifyTiming(handshakeTimeMs);
        return protocolVersion + "|" + hostnamePattern + "|" + timingClass;
    }

    /**
     * Hostname'i normalleştir (botnet tespiti için sabit pattern'lar).
     */
    private String normalizeHostname(String hostname) {
        if (hostname == null || hostname.isBlank()) return "null";
        // IP gibi görünen hostname → "ip-like"
        if (hostname.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) return "ip-like";
        // Çok kısa → "short"
        if (hostname.length() < 4) return "short";
        // Port içeren → kıs
        int colonIdx = hostname.indexOf(':');
        if (colonIdx > 0) hostname = hostname.substring(0, colonIdx);
        // Tümü küçük harf, max 32 karakter
        return hostname.toLowerCase().substring(0, Math.min(32, hostname.length()));
    }

    /**
     * Handshake süresini sınıflandır.
     * Botlar genellikle <5ms'de handshake tamamlar.
     */
    private String classifyTiming(long handshakeTimeMs) {
        if (handshakeTimeMs < 0)  return "unknown";
        if (handshakeTimeMs < 5)  return "bot-fast";   // <5ms — şüpheli
        if (handshakeTimeMs < 50) return "fast";
        if (handshakeTimeMs < 300) return "normal";
        return "slow";
    }

    // ────────────────────────────────────────────────────────
    // Kontrol
    // ────────────────────────────────────────────────────────

    /**
     * Bu parmak izinden çok sayıda bağlantı geliyor mu?
     */
    public boolean isMassFingerprint(String fingerprint) {
        return flaggedFingerprints.contains(fingerprint);
    }

    /**
     * Bu IP'nin parmak izi kitlesel bağlantı listesinde mi?
     */
    public boolean isIPFlaggedByFingerprint(String ip) {
        String fp = ipFingerprints.getIfPresent(ip);
        return fp != null && flaggedFingerprints.contains(fp);
    }

    /**
     * Bu parmak izi bilinen botnet kalıplarından birine uyuyor mu?
     */
    public boolean matchesKnownBotPattern(String fingerprint) {
        if (fingerprint == null) return false;
        for (String pattern : knownBotPatterns) {
            if (fingerprint.contains(pattern)) return true;
        }
        return false;
    }

    /** IP'nin mevcut parmak izini döndür. */
    public String getFingerprint(String ip) {
        return ipFingerprints.getIfPresent(ip);
    }

    /** Belirtilen parmak izinin bağlantı sayısını döndür. */
    public int getCount(String fingerprint) {
        AtomicInteger c = fingerprintCounts.getIfPresent(fingerprint);
        return c != null ? c.get() : 0;
    }

    // ────────────────────────────────────────────────────────
    // Temizlik
    // ────────────────────────────────────────────────────────

    /**
     * Periyodik temizlik. Caffeine cache'ler otomatik temizlenir;
     * bu metod flaggedFingerprints'i günceller.
     */
    public void cleanup() {
        // Artık Caffeine'den düşmüş (count=0) fingerprint'leri işaretle kümesinden çıkar
        flaggedFingerprints.removeIf(fp -> {
            AtomicInteger c = fingerprintCounts.getIfPresent(fp);
            return c == null || c.get() < massConnThreshold;
        });
    }

    // ────────────────────────────────────────────────────────
    // Getters
    // ────────────────────────────────────────────────────────

    public long getBotArmyDetections()   { return botArmyDetections.get(); }
    public int  getFlaggedFingerprintCount() { return flaggedFingerprints.size(); }
}
