package com.atomguard.velocity.module.antiddos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * İstatistiksel trafik anomali tespit motoru.
 * <p>
 * Özellikler:
 * <ul>
 *   <li>Son N dakikanın CPS ortalaması ve standart sapması</li>
 *   <li>Z-score tabanlı anlık anomali tespiti</li>
 *   <li>Yavaş rampa saldırısı (gradual ramp-up) tespiti</li>
 *   <li>Nabız/dalga saldırısı (pulsing) tespiti</li>
 * </ul>
 * <p>
 * Bu sınıf thread-safe olarak tasarlanmıştır.
 */
public class TrafficAnomalyDetector {

    // ────────────────────────────────────────────────────────
    // Konfigürasyon
    // ────────────────────────────────────────────────────────

    private final int    historyWindowSeconds;   // Kaç saniyelik geçmiş tutulacak
    private final double zScoreThreshold;        // Anomali için Z-score eşiği
    private final double slowRampThresholdPct;   // Yavaş rampa eşiği (% artış/dakika)
    private final boolean pulseDetectionEnabled;

    // ────────────────────────────────────────────────────────
    // State
    // ────────────────────────────────────────────────────────

    private final Deque<Integer>       cpsHistory  = new ArrayDeque<>();
    private final ReentrantReadWriteLock lock       = new ReentrantReadWriteLock();

    private volatile double meanCps   = 0.0;
    private volatile double stdDevCps = 1.0;
    private volatile int    lastCps   = 0;

    private final AtomicBoolean anomalyActive      = new AtomicBoolean(false);
    private final AtomicBoolean slowRampActive     = new AtomicBoolean(false);
    private final AtomicBoolean pulseActive        = new AtomicBoolean(false);
    private final AtomicInteger anomalyCount       = new AtomicInteger(0);

    // Yavaş rampa izleme
    private volatile double    rampBaselineCps     = -1.0;
    private volatile long      rampBaselineTime    = 0;

    // Pulse izleme: yüksek-düşük-yüksek döngüsü
    private final Deque<Boolean> pulseHistory       = new ArrayDeque<>();
    private static final int    PULSE_WINDOW        = 20; // son 20 saniye

    public TrafficAnomalyDetector(int historyWindowSeconds,
                                   double zScoreThreshold,
                                   double slowRampThresholdPct,
                                   boolean pulseDetectionEnabled) {
        this.historyWindowSeconds  = historyWindowSeconds;
        this.zScoreThreshold       = zScoreThreshold;
        this.slowRampThresholdPct  = slowRampThresholdPct;
        this.pulseDetectionEnabled = pulseDetectionEnabled;
    }

    // ────────────────────────────────────────────────────────
    // Güncelleme
    // ────────────────────────────────────────────────────────

    /**
     * Anlık CPS değerini kaydet ve anomali analizini çalıştır.
     * Her saniye çağrılması beklenir.
     *
     * @param currentCps Anlık bağlantı/saniye
     */
    public void recordCps(int currentCps) {
        lock.writeLock().lock();
        try {
            cpsHistory.addLast(currentCps);
            if (cpsHistory.size() > historyWindowSeconds) {
                cpsHistory.pollFirst();
            }
            recalculateStats();
        } finally {
            lock.writeLock().unlock();
        }

        lastCps = currentCps;

        // Anomali kontrolleri
        analyzeZScore(currentCps);
        if (slowRampThresholdPct > 0) analyzeSlowRamp(currentCps);
        if (pulseDetectionEnabled)    analyzePulse(currentCps);
    }

    // ────────────────────────────────────────────────────────
    // İstatistik hesaplama
    // ────────────────────────────────────────────────────────

    private void recalculateStats() {
        if (cpsHistory.isEmpty()) return;

        // Ortalama
        double sum = 0;
        for (int v : cpsHistory) sum += v;
        double mean = sum / cpsHistory.size();

        // Standart sapma
        double variance = 0;
        for (int v : cpsHistory) {
            double diff = v - mean;
            variance += diff * diff;
        }
        variance /= cpsHistory.size();

        this.meanCps   = mean;
        this.stdDevCps = Math.max(1.0, Math.sqrt(variance)); // min 1.0 — sıfıra bölmeyi önle
    }

    // ────────────────────────────────────────────────────────
    // Z-score anomali tespiti
    // ────────────────────────────────────────────────────────

    private void analyzeZScore(int currentCps) {
        if (cpsHistory.size() < 10) return; // Yeterli veri yok
        double z = (currentCps - meanCps) / stdDevCps;
        boolean isAnomaly = z > zScoreThreshold;

        if (isAnomaly && !anomalyActive.get()) {
            anomalyActive.set(true);
            anomalyCount.incrementAndGet();
        } else if (!isAnomaly) {
            anomalyActive.set(false);
        }
    }

    // ────────────────────────────────────────────────────────
    // Yavaş rampa tespiti
    // ────────────────────────────────────────────────────────

    private void analyzeSlowRamp(int currentCps) {
        long now = System.currentTimeMillis();

        if (rampBaselineCps < 0) {
            // Baseline yok — şimdi al
            rampBaselineCps  = currentCps;
            rampBaselineTime = now;
            return;
        }

        long elapsedMinutes = (now - rampBaselineTime) / 60_000L;
        if (elapsedMinutes < 1) return;

        // Dakika başına artış yüzdesi
        double rampRatePct = ((currentCps - rampBaselineCps) / rampBaselineCps) * 100.0 / elapsedMinutes;
        boolean isRamping  = rampRatePct >= slowRampThresholdPct;

        if (isRamping && !slowRampActive.get()) {
            slowRampActive.set(true);
        } else if (!isRamping) {
            slowRampActive.set(false);
            // Baseline'ı yenile
            if (elapsedMinutes >= 5) {
                rampBaselineCps  = currentCps;
                rampBaselineTime = now;
            }
        }
    }

    // ────────────────────────────────────────────────────────
    // Pulse/wave tespiti
    // ────────────────────────────────────────────────────────

    private void analyzePulse(int currentCps) {
        boolean isHigh = currentCps > meanCps * 1.5;

        synchronized (pulseHistory) {
            pulseHistory.addLast(isHigh);
            if (pulseHistory.size() > PULSE_WINDOW) pulseHistory.pollFirst();

            if (pulseHistory.size() < PULSE_WINDOW) {
                pulseActive.set(false);
                return;
            }

            // Alternatif yüksek-düşük desen tespiti
            int transitions = 0;
            Boolean prev = null;
            for (Boolean val : pulseHistory) {
                if (prev != null && !val.equals(prev)) transitions++;
                prev = val;
            }

            // En az PULSE_WINDOW/3 geçiş → pulse saldırısı
            pulseActive.set(transitions >= PULSE_WINDOW / 3);
        }
    }

    // ────────────────────────────────────────────────────────
    // Getters
    // ────────────────────────────────────────────────────────

    /** Herhangi bir anomali aktif mi? */
    public boolean isAnomalyActive() {
        return anomalyActive.get() || slowRampActive.get() || pulseActive.get();
    }

    public boolean isZScoreAnomaly()  { return anomalyActive.get(); }
    public boolean isSlowRampActive() { return slowRampActive.get(); }
    public boolean isPulseActive()    { return pulseActive.get(); }
    public int     getAnomalyCount()  { return anomalyCount.get(); }
    public double  getMeanCps()       { return meanCps; }
    public double  getStdDevCps()     { return stdDevCps; }
    public int     getLastCps()       { return lastCps; }
    public int     getHistorySize()   {
        lock.readLock().lock();
        try { return cpsHistory.size(); }
        finally { lock.readLock().unlock(); }
    }

    /** Anlık CPS için Z-score döndür. */
    public double getZScore(int cps) {
        return (cps - meanCps) / stdDevCps;
    }

    /** CPS varyansını döndür (pulse detection için). */
    public double getVariance() {
        double sd = stdDevCps;
        return sd * sd;
    }
}
