package com.atomguard.velocity.module.antiddos;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Saldırı tipi sınıflandırıcı.
 * <p>
 * Gelen trafiği analiz ederek saldırı türünü belirler ve
 * her tür için önerilen karşı strateji döndürür.
 * <p>
 * Sınıflandırma, periyodik olarak sağlanan metrik anlık görüntülerine dayalıdır
 * ve istatistiksel kararlar için pencere bazlı sayaçlar kullanır.
 */
public class AttackClassifier {

    // ────────────────────────────────────────────────────────
    // Saldırı türleri
    // ────────────────────────────────────────────────────────

    public enum AttackType {
        NONE("Saldırı Yok", "Standart koruma"),
        VOLUMETRIC("Hacimsel", "Bağlantı limitlerini düşür, subnet ban uygula"),
        SLOWLORIS("Slowloris", "Pending bağlantı limiti düşür, timeout azalt"),
        APPLICATION_LAYER("Uygulama Katmanı", "Login oranını sınırla, CAPTCHA aktifleştir"),
        PING_FLOOD("Ping Flood", "Ping yanıtını durdur, ICMP throttle uygula"),
        DISTRIBUTED("Dağıtık (DDoS)", "Subnet ban, geo-blok değerlendirmesi"),
        PULSING("Nabız/Dalga", "Seviye yavaş düşür, hysteresis artır"),
        AMPLIFICATION("Amplifikasyon", "Ping yanıtı küçült, motd önbelleğe al");

        private final String displayName;
        private final String counterStrategy;

        AttackType(String displayName, String counterStrategy) {
            this.displayName     = displayName;
            this.counterStrategy = counterStrategy;
        }

        public String getDisplayName()     { return displayName; }
        public String getCounterStrategy() { return counterStrategy; }
    }

    // ────────────────────────────────────────────────────────
    // Anlık görüntü veri yapısı
    // ────────────────────────────────────────────────────────

    /**
     * Sınıflandırma için gerekli trafik metrikleri.
     *
     * @param cps                      Anlık bağlantı/saniye
     * @param pendingConnections       Yarı açık bağlantı sayısı
     * @param totalConnections         Aktif toplam bağlantı
     * @param handshakeCompletionRate  Tamamlanan handshake oranı (0.0 – 1.0)
     * @param loginRate                Giriş oranı (cps'ye göre normalize)
     * @param pingRate                 Anlık ping/saniye
     * @param uniqueIPsInWindow        Penceredeki benzersiz IP sayısı
     * @param avgResponseSizeBytes     Ortalama yanıt boyutu (bayt)
     * @param pulseVariance            CPS varyansı (son 60s)
     */
    public record TrafficSnapshot(
            int    cps,
            int    pendingConnections,
            int    totalConnections,
            double handshakeCompletionRate,
            double loginRate,
            int    pingRate,
            int    uniqueIPsInWindow,
            long   avgResponseSizeBytes,
            double pulseVariance
    ) {}

    // ────────────────────────────────────────────────────────
    // State
    // ────────────────────────────────────────────────────────

    private volatile AttackType lastClassification = AttackType.NONE;
    private final AtomicLong    classifiedAt        = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger classificationCount = new AtomicInteger(0);

    // ────────────────────────────────────────────────────────
    // Sınıflandırma
    // ────────────────────────────────────────────────────────

    /**
     * Verilen trafik anlık görüntüsünü analiz ederek saldırı tipini belirle.
     *
     * @param snapshot Güncel trafik metrikleri
     * @param baseCps  Normal trafik CPS eşiği (config'den)
     * @return Tespit edilen saldırı tipi
     */
    public AttackType classify(TrafficSnapshot snapshot, int baseCps) {
        if (snapshot.cps < baseCps) {
            lastClassification = AttackType.NONE;
            return AttackType.NONE;
        }

        AttackType result = doClassify(snapshot, baseCps);
        lastClassification = result;
        classifiedAt.set(System.currentTimeMillis());
        classificationCount.incrementAndGet();
        return result;
    }

    private AttackType doClassify(TrafficSnapshot s, int baseCps) {
        int    cps         = s.cps();
        int    pending     = s.pendingConnections();
        int    total       = s.totalConnections();
        double hsr         = s.handshakeCompletionRate(); // handshake completion ratio
        double loginRate   = s.loginRate();
        int    pingRate    = s.pingRate();
        int    uniqueIPs   = s.uniqueIPsInWindow();
        long   avgSize     = s.avgResponseSizeBytes();
        double variance    = s.pulseVariance();

        // Pulsing saldırısı: yüksek CPS varyansı
        if (variance > baseCps * 0.5 && cps >= baseCps) {
            return AttackType.PULSING;
        }

        // Amplifikasyon: büyük yanıt boyutu, düşük CPS
        if (avgSize > 512 && pingRate > baseCps * 2 && cps < baseCps * 1.5) {
            return AttackType.AMPLIFICATION;
        }

        // Ping flood: yüksek ping, düşük login
        if (pingRate > baseCps * 3 && loginRate < 0.1) {
            return AttackType.PING_FLOOD;
        }

        // Slowloris: düşük CPS ama yüksek pending oran
        double pendingRatio = total > 0 ? (double) pending / total : 0.0;
        if (pendingRatio > 0.3 && cps < baseCps * 2) {
            return AttackType.SLOWLORIS;
        }

        // Dağıtık saldırı: yüksek benzersiz IP sayısı, orta CPS
        if (uniqueIPs > 50 && cps >= baseCps && cps < baseCps * 3) {
            return AttackType.DISTRIBUTED;
        }

        // Uygulama katmanı: normal CPS ama anormal handshake/login
        if (cps >= baseCps && hsr > 0.7 && loginRate < 0.2) {
            return AttackType.APPLICATION_LAYER;
        }

        // Hacimsel: yüksek CPS, düşük handshake tamamlama
        if (cps >= baseCps * 2 && hsr < 0.4) {
            return AttackType.VOLUMETRIC;
        }

        // Genel yüksek trafik
        if (cps >= baseCps * 1.5) {
            return AttackType.VOLUMETRIC;
        }

        return AttackType.NONE;
    }

    // ────────────────────────────────────────────────────────
    // Getters
    // ────────────────────────────────────────────────────────

    public AttackType getLastClassification()   { return lastClassification; }
    public long       getClassifiedAt()          { return classifiedAt.get(); }
    public int        getClassificationCount()   { return classificationCount.get(); }

    public String getSummary() {
        AttackType t = lastClassification;
        return "Saldırı Tipi: " + t.getDisplayName()
             + " | Strateji: " + t.getCounterStrategy();
    }
}
