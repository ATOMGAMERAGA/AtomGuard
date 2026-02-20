package com.atomguard.velocity.module.antiddos;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Subnet bazlı akıllı trafik analizi.
 * <p>
 * /24 ve /16 subnet'leri ayrı izler, koordineli saldırıları (botnet)
 * tespit eder ve subnet düzeyinde itibar skoru tutar.
 */
public class SubnetAnalyzer {

    // ────────────────────────────────────────────────────────
    // Konfigürasyon
    // ────────────────────────────────────────────────────────

    /** Bir subnet'ten saniyede bu kadar bağlantı → throttle */
    private final int subnetThrottleMultiplier;

    /** Tek /16'dan bu kadar farklı /24 → coordinated attack */
    private final int coordinatedAttackSubnet24Threshold;

    /** Subnet ban aktif mi? */
    private final boolean subnetBanEnabled;

    /** Subnet ban eşiği (itibar skoru) */
    private final int subnetBanThreshold;

    // ────────────────────────────────────────────────────────
    // State
    // ────────────────────────────────────────────────────────

    /** /24 subnet → son 60 saniyedeki bağlantı sayısı */
    private final Map<String, AtomicInteger> subnet24Connections = new ConcurrentHashMap<>();

    /** /16 subnet → aktif /24 alt-subnet seti */
    private final Map<String, Set<String>> subnet16ActiveSubnets = new ConcurrentHashMap<>();

    /** /24 subnet → itibar skoru (0-100, başlangıç 50) */
    private final Map<String, AtomicInteger> subnet24Reputation = new ConcurrentHashMap<>();

    /** Throttle edilen subnet'ler (Caffeine — TTL: 5 dakika) */
    private final Cache<String, Boolean> throttledSubnets = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(5_000)
            .build();

    /** Banlanan subnet'ler (Caffeine — TTL: 1 saat) */
    private final Cache<String, String> bannedSubnets = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(1_000)
            .build();

    /** Koordineli saldırı tespiti sayacı */
    private final AtomicLong coordinatedAttacksDetected = new AtomicLong(0);

    public SubnetAnalyzer(int subnetThrottleMultiplier,
                          int coordinatedAttackSubnet24Threshold,
                          boolean subnetBanEnabled,
                          int subnetBanThreshold) {
        this.subnetThrottleMultiplier          = subnetThrottleMultiplier;
        this.coordinatedAttackSubnet24Threshold = coordinatedAttackSubnet24Threshold;
        this.subnetBanEnabled                  = subnetBanEnabled;
        this.subnetBanThreshold                = subnetBanThreshold;
    }

    // ────────────────────────────────────────────────────────
    // Bağlantı kaydı
    // ────────────────────────────────────────────────────────

    /**
     * Yeni bağlantıyı kaydet ve analiz et.
     *
     * @param ip Kaynak IP
     * @return Subnet engeli var mı?
     */
    public boolean recordAndCheck(String ip) {
        String subnet24 = getSubnet24(ip);
        String subnet16 = getSubnet16(ip);

        // /24 bağlantı sayacı
        subnet24Connections.computeIfAbsent(subnet24, k -> new AtomicInteger(0))
                           .incrementAndGet();

        // /16 altında aktif /24 takibi
        subnet16ActiveSubnets
                .computeIfAbsent(subnet16, k -> ConcurrentHashMap.newKeySet())
                .add(subnet24);

        // Koordineli saldırı kontrolü
        checkCoordinatedAttack(subnet16);

        // Ban ve throttle kontrolü
        return isSubnetBanned(subnet24) || isSubnetThrottled(subnet24);
    }

    /**
     * Birden fazla /16 subnet'inde koordineli saldırı tespiti.
     */
    private void checkCoordinatedAttack(String subnet16) {
        Set<String> activeSubnets = subnet16ActiveSubnets.get(subnet16);
        if (activeSubnets == null) return;

        if (activeSubnets.size() >= coordinatedAttackSubnet24Threshold) {
            coordinatedAttacksDetected.incrementAndGet();
            // /16'daki tüm /24'lerin itibarını düşür
            for (String sub24 : activeSubnets) {
                penalizeSubnet(sub24, 20, "koordineli-saldiri");
            }
        }
    }

    // ────────────────────────────────────────────────────────
    // Throttle ve ban
    // ────────────────────────────────────────────────────────

    /**
     * Bir subnet'in normalin {@code subnetThrottleMultiplier} katını aşıp
     * aşmadığını kontrol eder. Aşıyorsa throttle et.
     *
     * @param subnet24   /24 subnet string'i ("192.168.1")
     * @param normalBase Normal trafik baz CPS değeri
     */
    public void evaluateThrottle(String subnet24, int normalBase) {
        AtomicInteger counter = subnet24Connections.get(subnet24);
        if (counter == null) return;

        int count = counter.get();
        if (count > normalBase * subnetThrottleMultiplier) {
            throttledSubnets.put(subnet24, Boolean.TRUE);
            penalizeSubnet(subnet24, 10, "hiz-asimi");
        }
    }

    public boolean isSubnetThrottled(String ip) {
        return throttledSubnets.getIfPresent(getSubnet24(ip)) != null;
    }

    public boolean isSubnetBanned(String ip) {
        return bannedSubnets.getIfPresent(getSubnet24(ip)) != null;
    }

    /**
     * Subnet'i yasakla.
     *
     * @param ip     Bu IP'nin /24 subnet'i yasaklanır
     * @param reason Sebep
     */
    public void banSubnet(String ip, String reason) {
        if (!subnetBanEnabled) return;
        String subnet24 = getSubnet24(ip);
        bannedSubnets.put(subnet24, reason);
    }

    // ────────────────────────────────────────────────────────
    // İtibar sistemi
    // ────────────────────────────────────────────────────────

    /**
     * Subnet itibarını düşür.
     *
     * @param subnet24 /24 subnet string'i
     * @param penalty  Düşülecek puan
     * @param reason   Sebep (log)
     */
    public void penalizeSubnet(String subnet24, int penalty, String reason) {
        AtomicInteger rep = subnet24Reputation.computeIfAbsent(subnet24, k -> new AtomicInteger(50));
        int newRep = Math.max(0, rep.addAndGet(-penalty));

        if (subnetBanEnabled && newRep <= subnetBanThreshold) {
            bannedSubnets.put(subnet24, reason);
        }
    }

    /**
     * Subnet itibarını artır (başarılı bağlantılar için).
     */
    public void rewardSubnet(String ip, int bonus) {
        String subnet24 = getSubnet24(ip);
        AtomicInteger rep = subnet24Reputation.computeIfAbsent(subnet24, k -> new AtomicInteger(50));
        rep.updateAndGet(v -> Math.min(100, v + bonus));
    }

    public int getSubnetReputation(String ip) {
        AtomicInteger rep = subnet24Reputation.get(getSubnet24(ip));
        return rep != null ? rep.get() : 50;
    }

    // ────────────────────────────────────────────────────────
    // Temizlik
    // ────────────────────────────────────────────────────────

    /**
     * Periyodik temizlik — 1 dakikada bir çağrılır.
     */
    public void cleanup() {
        // Bağlantı sayaçlarını sıfırla (1 dakikalık pencere)
        subnet24Connections.clear();
        // Aktif subnet setlerini küçült
        subnet16ActiveSubnets.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    // ────────────────────────────────────────────────────────
    // Yardımcı
    // ────────────────────────────────────────────────────────

    /** "192.168.1.100" → "192.168.1" */
    public static String getSubnet24(String ip) {
        int lastDot = ip.lastIndexOf('.');
        return lastDot > 0 ? ip.substring(0, lastDot) : ip;
    }

    /** "192.168.1.100" → "192.168" */
    public static String getSubnet16(String ip) {
        int firstDot  = ip.indexOf('.');
        int secondDot = ip.indexOf('.', firstDot + 1);
        return (firstDot > 0 && secondDot > 0) ? ip.substring(0, secondDot) : ip;
    }

    // ────────────────────────────────────────────────────────
    // Getters
    // ────────────────────────────────────────────────────────

    public long getCoordinatedAttacksDetected() { return coordinatedAttacksDetected.get(); }
    public int  getActiveSubnet24Count()         { return subnet24Connections.size(); }
    public int  getBannedSubnetCount()           { return (int) bannedSubnets.estimatedSize(); }
    public int  getThrottledSubnetCount()        { return (int) throttledSubnets.estimatedSize(); }
}
