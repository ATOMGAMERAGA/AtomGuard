package com.atomguard.intelligence;

import java.util.ArrayList;
import java.util.List;

/**
 * Z-Score tabanlı istatistiksel anomali tespiti.
 * Birden fazla metrik anomali gösterirse tehdit seviyesi yükselir.
 *
 * @author AtomGuard Team
 * @version 1.2.0
 */
public class AnomalyDetector {

    // Varsayılan Z-Score eşikleri
    private static final double DEFAULT_ELEVATED_Z = 2.0;
    private static final double DEFAULT_HIGH_Z     = 3.0;
    private static final double DEFAULT_CRITICAL_Z = 4.0;

    private final double elevatedZ;
    private final double highZ;
    private final double criticalZ;
    private final double sensitivityMultiplier;

    public AnomalyDetector(double sensitivityMultiplier) {
        this.sensitivityMultiplier = Math.max(0.1, Math.min(5.0, sensitivityMultiplier));
        // Sensitivity > 1 → eşikleri düşür (daha hassas), < 1 → eşikleri yükselt
        double inv = 1.0 / this.sensitivityMultiplier;
        this.elevatedZ = DEFAULT_ELEVATED_Z * inv;
        this.highZ     = DEFAULT_HIGH_Z     * inv;
        this.criticalZ = DEFAULT_CRITICAL_Z * inv;
    }

    /**
     * Tek bir metrik için Z-Score hesaplar ve uyarı üretir (varsa).
     *
     * @param metric      Metrik adı (log/uyarı için)
     * @param current     Anlık değer
     * @param mean        Öğrenilmiş ortalama
     * @param stddev      Öğrenilmiş standart sapma (> 0 olmalı)
     * @return            Anomali varsa IntelligenceAlert, yoksa null
     */
    public IntelligenceAlert analyze(String metric, double current, double mean, double stddev) {
        if (stddev <= 0) return null;

        double z = (current - mean) / stddev;

        // Sadece yüksek yönde anomali (saldırılar trafik artışı olarak gösterilir)
        if (z < elevatedZ) return null;

        IntelligenceAlert.Level level;
        String details;

        if (z >= criticalZ) {
            level = IntelligenceAlert.Level.CRITICAL;
            details = String.format("Z=%.2f — %s değeri normalin %.1fx üzerinde (Kritik)", z, metric, current / Math.max(0.01, mean));
        } else if (z >= highZ) {
            level = IntelligenceAlert.Level.HIGH;
            details = String.format("Z=%.2f — %s değeri normalin %.1fx üzerinde (Yüksek)", z, metric, current / Math.max(0.01, mean));
        } else {
            level = IntelligenceAlert.Level.ELEVATED;
            details = String.format("Z=%.2f — %s anormal yükseliş tespit edildi (Yükselen)", z, metric);
        }

        return new IntelligenceAlert(level, metric, z, current, mean, details);
    }

    /**
     * Birden fazla metriği analiz eder. Birden fazla anomali varsa
     * en yüksek seviyeli uyarıyı döndürür ve ayrıntıları birleştirir.
     */
    public IntelligenceAlert analyzeAll(TrafficProfile profile,
                                        double currentConnections,
                                        double currentUniqueIps,
                                        double currentPacketRate,
                                        double currentJoinLeaveRatio) {
        List<IntelligenceAlert> alerts = new ArrayList<>();

        IntelligenceAlert a;
        a = analyze("baglanti-sayisi", currentConnections,
                profile.getMeanConnections(), profile.getStddevConnections());
        if (a != null) alerts.add(a);

        a = analyze("tekil-ip-sayisi", currentUniqueIps,
                profile.getMeanUniqueIps(), profile.getStddevUniqueIps());
        if (a != null) alerts.add(a);

        a = analyze("paket-hizi", currentPacketRate,
                profile.getMeanPacketRate(), profile.getStddevPacketRate());
        if (a != null) alerts.add(a);

        a = analyze("giris-cikis-orani", currentJoinLeaveRatio,
                profile.getMeanJoinLeaveRatio(), profile.getStddevJoinLeaveRatio());
        if (a != null) alerts.add(a);

        if (alerts.isEmpty()) return null;

        // En yüksek seviyeli uyarıyı bul
        IntelligenceAlert worst = alerts.get(0);
        for (IntelligenceAlert alert : alerts) {
            if (alert.getLevel().ordinal() > worst.getLevel().ordinal()) {
                worst = alert;
            }
        }

        // Birden fazla anomali varsa seviyeyi bir kademeli artır (ELEVATED → HIGH, HIGH → CRITICAL)
        IntelligenceAlert.Level finalLevel = worst.getLevel();
        if (alerts.size() >= 2 && finalLevel != IntelligenceAlert.Level.CRITICAL) {
            finalLevel = IntelligenceAlert.Level.values()[finalLevel.ordinal() + 1];
        }

        // Tüm anomali detaylarını birleştir
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%d anomali] ", alerts.size()));
        for (int i = 0; i < alerts.size(); i++) {
            if (i > 0) sb.append(" | ");
            sb.append(alerts.get(i).getDetails());
        }

        return new IntelligenceAlert(
                finalLevel,
                worst.getMetric(),
                worst.getZScore(),
                worst.getCurrentValue(),
                worst.getMeanValue(),
                sb.toString()
        );
    }

    // ─── Getters ───

    public double getElevatedZ() { return elevatedZ; }
    public double getHighZ() { return highZ; }
    public double getCriticalZ() { return criticalZ; }
    public double getSensitivityMultiplier() { return sensitivityMultiplier; }
}
