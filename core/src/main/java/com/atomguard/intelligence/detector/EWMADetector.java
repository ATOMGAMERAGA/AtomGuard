package com.atomguard.intelligence.detector;

/**
 * Ustel Agirlikli Hareketli Ortalama (EWMA) tabanli anomali dedektoru.
 * Zaman serisi verilerinde adaptif ortalama ve varyans takibi yapar,
 * Z-Score hesaplayarak anomali tespiti gerceklestirir.
 *
 * @author AtomGuard Team
 * @version 1.3.0
 */
public class EWMADetector {

    private double ewma;
    private double ewmVar;
    private final double alpha;
    private final double sigmaMultiplier;
    private final int minSamples;
    private long sampleCount;

    /**
     * @param alpha           Yumusatma faktoru (0-1 arasi, dusuk = daha yavas adaptasyon)
     * @param sigmaMultiplier Z-Score esik carpani (ornegin 2.5 = 2.5 sigma)
     * @param minSamples      Anomali tespiti icin gereken minimum ornek sayisi
     */
    public EWMADetector(double alpha, double sigmaMultiplier, int minSamples) {
        this.alpha = alpha;
        this.sigmaMultiplier = sigmaMultiplier;
        this.minSamples = minSamples;
    }

    /**
     * Verilen degerin anomali olup olmadigini kontrol eder ve ic durumu gunceller.
     *
     * @param value Kontrol edilecek deger
     * @return Yeterli ornek varsa ve Z-Score esigi asarsa true
     */
    public synchronized boolean isAnomaly(double value) {
        if (sampleCount < minSamples) {
            update(value);
            return false;
        }
        double stddev = Math.sqrt(ewmVar);
        double zScore = Math.abs(value - ewma) / (stddev + 1e-9);
        boolean anomaly = zScore > sigmaMultiplier;
        update(value);
        return anomaly;
    }

    /**
     * Verilen deger icin Z-Score hesaplar (ic durumu guncellemez).
     *
     * @param value Hesaplanacak deger
     * @return Z-Score degeri
     */
    public synchronized double getZScore(double value) {
        double stddev = Math.sqrt(ewmVar);
        return Math.abs(value - ewma) / (stddev + 1e-9);
    }

    private void update(double value) {
        if (sampleCount == 0) {
            ewma = value;
            ewmVar = 0;
        } else {
            double diff = value - ewma;
            ewma += alpha * diff;
            ewmVar = (1 - alpha) * (ewmVar + alpha * diff * diff);
        }
        sampleCount++;
    }

    /**
     * Tum ic durumu sifirlar.
     */
    public synchronized void reset() {
        ewma = 0;
        ewmVar = 0;
        sampleCount = 0;
    }

    // ─── Getters ───

    public synchronized double getEwma() { return ewma; }
    public synchronized double getVariance() { return ewmVar; }
    public long getSampleCount() { return sampleCount; }
}
