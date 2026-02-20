package com.atomguard.velocity.module.antiddos;

import com.atomguard.velocity.AtomGuardVelocity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global bağlantı hızı izleme ve SYN flood tespiti.
 * <p>
 * AttackLevelManager ve TrafficAnomalyDetector ile entegre çalışır.
 * Her saniyede bir tick atar; CPS değerini ölçer ve ilgili bileşenlere iletir.
 * <p>
 * Düzeltilen bug: de-escalation artık anlık değil; hysteresis
 * AttackLevelManager tarafından yönetilir.
 */
public class SynFloodDetector {

    private final AtomGuardVelocity plugin;
    private final int               threshold;

    private final AtomicInteger connectionsThisSecond = new AtomicInteger(0);
    private final AtomicInteger peakRate              = new AtomicInteger(0);

    /** Son 60 saniyelik CPS geçmişi */
    private final Deque<Integer> rateHistory = new ArrayDeque<>();

    private final ScheduledExecutorService scheduler;

    /** AttackLevelManager bağlantısı (opsiyonel) */
    private AttackLevelManager levelManager;

    /** TrafficAnomalyDetector bağlantısı (opsiyonel) */
    private TrafficAnomalyDetector anomalyDetector;

    /** DDoSMetricsCollector bağlantısı (opsiyonel) */
    private DDoSMetricsCollector metricsCollector;

    /** AttackSessionRecorder bağlantısı (opsiyonel) */
    private AttackSessionRecorder sessionRecorder;

    /** AttackClassifier bağlantısı (opsiyonel) */
    private AttackClassifier classifier;

    public SynFloodDetector(AtomGuardVelocity plugin, int threshold) {
        this.plugin    = plugin;
        this.threshold = threshold;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "atomguard-syn-detector");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
    }

    // ────────────────────────────────────────────────────────
    // Bağlantı setter'ları
    // ────────────────────────────────────────────────────────

    public void setLevelManager(AttackLevelManager levelManager) {
        this.levelManager = levelManager;
    }

    public void setAnomalyDetector(TrafficAnomalyDetector anomalyDetector) {
        this.anomalyDetector = anomalyDetector;
    }

    public void setMetricsCollector(DDoSMetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    public void setSessionRecorder(AttackSessionRecorder sessionRecorder) {
        this.sessionRecorder = sessionRecorder;
    }

    public void setClassifier(AttackClassifier classifier) {
        this.classifier = classifier;
    }

    // ────────────────────────────────────────────────────────
    // Tick
    // ────────────────────────────────────────────────────────

    private void tick() {
        int rate = connectionsThisSecond.getAndSet(0);
        peakRate.updateAndGet(p -> Math.max(p, rate));

        synchronized (rateHistory) {
            rateHistory.addLast(rate);
            if (rateHistory.size() > 60) rateHistory.pollFirst();
        }

        // Anomali dedektörüne CPS bildir
        if (anomalyDetector != null) {
            anomalyDetector.recordCps(rate);
        }

        // AttackLevelManager güncelle (hysteresis burada yönetilir)
        if (levelManager != null) {
            levelManager.update(rate);
        }

        // Metrik toplayıcısına bildir
        if (metricsCollector != null) {
            AttackLevelManager.AttackLevel level = levelManager != null
                    ? levelManager.getCurrentLevel()
                    : AttackLevelManager.AttackLevel.NONE;
            metricsCollector.tick(rate, 0, 0, level);
        }

        // Oturum kaydediciye CPS bildir
        if (sessionRecorder != null) {
            sessionRecorder.recordCps(rate);
        }

        // Sınıflandırıcıya snaphot gönder (her 5 tick'te bir)
        if (classifier != null && rate >= threshold / 2) {
            classifyCurrentTraffic(rate);
        }

        // Eski attack mode uyumluluk kontrolü (levelManager yoksa)
        if (levelManager == null) {
            legacyAttackModeCheck(rate);
        }
    }

    private void classifyCurrentTraffic(int rate) {
        AttackClassifier.TrafficSnapshot snapshot = new AttackClassifier.TrafficSnapshot(
                rate, 0, 0, 0.5, 0.3, 0, 10, 256, getVariance()
        );
        classifier.classify(snapshot, threshold);
    }

    private double getVariance() {
        synchronized (rateHistory) {
            if (rateHistory.size() < 2) return 0.0;
            double sum = 0;
            for (int v : rateHistory) sum += v;
            double mean = sum / rateHistory.size();
            double variance = 0;
            for (int v : rateHistory) variance += (v - mean) * (v - mean);
            return variance / rateHistory.size();
        }
    }

    /** Geriye uyumluluk: AttackLevelManager olmadan binary attack mode. */
    private void legacyAttackModeCheck(int rate) {
        if (rate >= threshold && !plugin.isAttackMode()) {
            plugin.setAttackMode(true);
            plugin.getAlertManager().alertAttackStarted(String.valueOf(rate), 0, null);
            plugin.getLogManager().warn("SYN flood tespit edildi! Hız: " + rate + "/s — Saldırı modu aktif.");
        } else if (rate < threshold / 2 && plugin.isAttackMode()) {
            // Gradual de-escalation — anlık kapatma değil, son 10 saniyeyi kontrol et
            boolean consistentlyLow = checkConsistentlyLow(threshold / 2, 10);
            if (consistentlyLow) {
                long duration = System.currentTimeMillis() - plugin.getAttackModeStartTime();
                plugin.setAttackMode(false);
                plugin.getAlertManager().alertAttackEnded(
                        formatDuration(duration),
                        plugin.getStatisticsManager().get("ddos_blocked"),
                        peakRate.get(),
                        null);
            }
        }
    }

    /**
     * Son N saniyenin hepsi eşiğin altında mı?
     */
    private boolean checkConsistentlyLow(int maxCps, int seconds) {
        synchronized (rateHistory) {
            if (rateHistory.size() < seconds) return false;
            int[] arr = rateHistory.stream().mapToInt(Integer::intValue).toArray();
            for (int i = arr.length - 1; i >= Math.max(0, arr.length - seconds); i--) {
                if (arr[i] >= maxCps) return false;
            }
            return true;
        }
    }

    private String formatDuration(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        seconds %= 60;
        return minutes > 0 ? minutes + "d " + seconds + "s" : seconds + "s";
    }

    // ────────────────────────────────────────────────────────
    // API
    // ────────────────────────────────────────────────────────

    /** Yeni bağlantıyı kaydet. */
    public void recordConnection() {
        connectionsThisSecond.incrementAndGet();
    }

    public int getCurrentRate() { return connectionsThisSecond.get(); }
    public int getPeakRate()    { return peakRate.get(); }
    public int getThreshold()   { return threshold; }

    /** Son 60 saniyelik CPS ortalaması. */
    public double getAverageCps() {
        synchronized (rateHistory) {
            if (rateHistory.isEmpty()) return 0.0;
            return rateHistory.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
