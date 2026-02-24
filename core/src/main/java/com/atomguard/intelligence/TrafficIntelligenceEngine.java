package com.atomguard.intelligence;

import com.atomguard.AtomGuard;
import com.atomguard.api.event.IntelligenceAlertEvent;
import org.bukkit.Bukkit;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Adaptif trafik istihbarat motoru.
 * 168 saatlik (24×7) EMA profili, gerçek zamanlı anomali tespiti ve
 * ardışık anomali sayacı ile yanlış pozitif koruması.
 *
 * @author AtomGuard Team
 * @version 1.2.0
 */
public class TrafficIntelligenceEngine {

    private static final int HOUR_SLOTS = 168; // 24 × 7

    private final AtomGuard plugin;

    // Saatlik EMA profilleri (0-167)
    private final TrafficProfile[] hourlyProfiles = new TrafficProfile[HOUR_SLOTS];

    // Gerçek zamanlı 1 dakikalık pencere tamponları
    private final TimeSeriesBuffer connectionsBuffer;
    private final TimeSeriesBuffer uniqueIpsBuffer;
    private final TimeSeriesBuffer packetRateBuffer;
    private final TimeSeriesBuffer joinLeaveRatioBuffer;

    // Anlık sayaçlar (1 dk pencere için sıfırlanır)
    private final AtomicLong windowConnections = new AtomicLong(0);
    private final AtomicLong windowUniqueIps   = new AtomicLong(0);
    private final AtomicLong windowPackets     = new AtomicLong(0);
    private final AtomicLong windowJoins       = new AtomicLong(0);
    private final AtomicLong windowLeaves      = new AtomicLong(0);

    // Ardışık anomali sayacı (3 dk = 3 ardışık 1 dk tick gerekli)
    private final AtomicInteger consecutiveAnomalyMinutes = new AtomicInteger(0);
    private static final int CONSECUTIVE_REQUIRED = 3;

    // Güncel tehdit seviyesi
    private volatile ThreatLevel currentThreatLevel = ThreatLevel.NORMAL;
    private volatile IntelligenceAlert lastAlert = null;
    private volatile long lastAlertTimestamp = 0;

    // Konfigürasyon
    private final boolean enabled;
    private final boolean learningMode;
    private final int learningPeriodHours;
    private final int minSamplesForBaseline;
    private final double emaAlpha;
    private final double sensitivityMultiplier;
    private final long alertCooldownMs;

    private final AnomalyDetector anomalyDetector;
    private final ScheduledExecutorService scheduler;

    // Toplam örnekler
    private final AtomicLong totalSamples = new AtomicLong(0);

    public TrafficIntelligenceEngine(AtomGuard plugin) {
        this.plugin = plugin;

        this.enabled = plugin.getConfigManager().getConfig()
                .getBoolean("tehdit-istihbarati.aktif", true);
        this.learningMode = plugin.getConfigManager().getConfig()
                .getBoolean("tehdit-istihbarati.ogrenme-modu", false);
        this.learningPeriodHours = Math.max(1, plugin.getConfigManager().getConfig()
                .getInt("tehdit-istihbarati.ogrenme-suresi-saat", 48));
        this.minSamplesForBaseline = Math.max(3, plugin.getConfigManager().getConfig()
                .getInt("tehdit-istihbarati.min-ornek-sayisi", 10));
        this.emaAlpha = clamp(plugin.getConfigManager().getConfig()
                .getDouble("tehdit-istihbarati.ema-alpha", 0.1), 0.001, 1.0);
        this.sensitivityMultiplier = clamp(plugin.getConfigManager().getConfig()
                .getDouble("tehdit-istihbarati.hassasiyet-carpani", 1.0), 0.1, 5.0);
        long cooldownSec = plugin.getConfigManager().getConfig()
                .getLong("tehdit-istihbarati.uyari-bekleme-saniye", 300);
        this.alertCooldownMs = cooldownSec * 1000L;

        int bufferSize = Math.max(10, plugin.getConfigManager().getConfig()
                .getInt("tehdit-istihbarati.tampon-boyutu", 60));

        this.connectionsBuffer  = new TimeSeriesBuffer(bufferSize);
        this.uniqueIpsBuffer    = new TimeSeriesBuffer(bufferSize);
        this.packetRateBuffer   = new TimeSeriesBuffer(bufferSize);
        this.joinLeaveRatioBuffer = new TimeSeriesBuffer(bufferSize);

        for (int i = 0; i < HOUR_SLOTS; i++) {
            hourlyProfiles[i] = new TrafficProfile();
        }

        this.anomalyDetector = new AnomalyDetector(sensitivityMultiplier);

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AtomGuard-Intelligence");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (!enabled) {
            plugin.getLogger().info("[İstihbarat] Tehdit istihbaratı motoru devre dışı.");
            return;
        }
        // Her dakika pencereyi işle + anomali kontrolü
        scheduler.scheduleAtFixedRate(this::tick, 60, 60, TimeUnit.SECONDS);
        plugin.getLogger().info("[İstihbarat] Tehdit istihbarat motoru başlatıldı. " +
                (learningMode ? "ÖĞRENME MODU aktif." : "Anomali tespiti aktif."));
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    // ─── Olay Kaydediciler ───

    /** Yeni bağlantı kaydı. */
    public void recordConnection(String ip) {
        windowConnections.incrementAndGet();
    }

    /** Farklı IP sayısını güncelle (her çağrıda artımlı sayar, tick'te kullanılır). */
    public void recordUniqueIp() {
        windowUniqueIps.incrementAndGet();
    }

    /** Paket kaydı. */
    public void recordPacket() {
        windowPackets.incrementAndGet();
    }

    /** Oyuncu katılması. */
    public void recordJoin() {
        windowJoins.incrementAndGet();
    }

    /** Oyuncu ayrılması. */
    public void recordLeave() {
        windowLeaves.incrementAndGet();
    }

    // ─── Ana Döngü ───

    private void tick() {
        try {
            // Pencere değerlerini al ve sıfırla
            double connections   = windowConnections.getAndSet(0);
            double uniqueIps     = windowUniqueIps.getAndSet(0);
            double packetRate    = windowPackets.getAndSet(0);
            long joins           = windowJoins.getAndSet(0);
            long leaves          = windowLeaves.getAndSet(0);
            double joinLeaveRatio = (leaves > 0) ? (double) joins / leaves : joins;

            // Tamponlara ekle
            connectionsBuffer.add(connections);
            uniqueIpsBuffer.add(uniqueIps);
            packetRateBuffer.add(packetRate);
            joinLeaveRatioBuffer.add(joinLeaveRatio);

            // Saatlik profili güncelle
            int slot = currentHourSlot();
            TrafficProfile profile = hourlyProfiles[slot];
            profile.update(connections, uniqueIps, packetRate, joinLeaveRatio, emaAlpha);

            totalSamples.incrementAndGet();

            // Öğrenme modunda anomali tespiti yapma
            if (learningMode) return;

            // Yeterli örnek yoksa anomali tespiti yapma
            if (!profile.isReliable(minSamplesForBaseline)) return;

            // Anomali tespiti
            IntelligenceAlert alert = anomalyDetector.analyzeAll(
                    profile, connections, uniqueIps, packetRate, joinLeaveRatio);

            if (alert != null) {
                int consecutive = consecutiveAnomalyMinutes.incrementAndGet();

                // 3 ardışık dakika gerekli (yanlış pozitif önleme)
                if (consecutive >= CONSECUTIVE_REQUIRED) {
                    handleAnomaly(alert);
                }
            } else {
                // Anomali yok → sayacı azalt (hızlı düşüş için)
                if (consecutiveAnomalyMinutes.get() > 0) {
                    consecutiveAnomalyMinutes.decrementAndGet();
                }
                // Normal seviyeye dön
                if (consecutiveAnomalyMinutes.get() == 0) {
                    currentThreatLevel = ThreatLevel.NORMAL;
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[İstihbarat] Tick hatası", e);
        }
    }

    private void handleAnomaly(IntelligenceAlert alert) {
        ThreatLevel newLevel = alert.toThreatLevel();
        currentThreatLevel = newLevel;
        lastAlert = alert;

        long now = System.currentTimeMillis();
        boolean cooldownExpired = (now - lastAlertTimestamp) >= alertCooldownMs;

        if (cooldownExpired) {
            lastAlertTimestamp = now;

            // Bukkit event fırlat (async)
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                IntelligenceAlertEvent event = new IntelligenceAlertEvent(
                        newLevel.name(), alert.getDetails(), alert.getZScore());
                Bukkit.getPluginManager().callEvent(event);
            });

            // Discord bildirimi
            if (plugin.getDiscordWebhookManager() != null) {
                plugin.getDiscordWebhookManager().notifyIntelligenceAlert(alert);
            }

            // Loglama
            plugin.getLogManager().info(String.format(
                    "[İSTİHBARAT] %s uyarısı | Metrik: %s | Z=%.2f | %s",
                    newLevel.name(), alert.getMetric(), alert.getZScore(), alert.getDetails()));
        }

        // KRİTİK: Otomatik saldırı modu aktifleştirme
        if (newLevel == ThreatLevel.CRITICAL && plugin.getAttackModeManager() != null) {
            if (!plugin.getAttackModeManager().isAttackMode()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.getAttackModeManager().forceEnable();
                    plugin.getLogger().warning(
                            "[İstihbarat] KRİTİK anomali → Saldırı modu otomatik aktifleştirildi.");
                });
            }
        }
    }

    // ─── Yardımcılar ───

    private int currentHourSlot() {
        // Haftanın saati (0-167): Pazartesi=0, ..., Pazar=6; saat*24+gün
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int dayOfWeek = (cal.get(java.util.Calendar.DAY_OF_WEEK) - 2 + 7) % 7; // Mon=0
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        return dayOfWeek * 24 + hour;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    // ─── Sıfırlama ───

    /** Tüm profilleri ve sayaçları sıfırlar. */
    public void resetProfiles() {
        for (int i = 0; i < HOUR_SLOTS; i++) {
            hourlyProfiles[i] = new TrafficProfile();
        }
        consecutiveAnomalyMinutes.set(0);
        currentThreatLevel = ThreatLevel.NORMAL;
        lastAlert = null;
        totalSamples.set(0);
        plugin.getLogger().info("[İstihbarat] Tüm trafik profilleri sıfırlandı.");
    }

    // ─── Getters ───

    public ThreatLevel getCurrentThreatLevel() { return currentThreatLevel; }
    public IntelligenceAlert getLastAlert() { return lastAlert; }
    public long getLastAlertTimestamp() { return lastAlertTimestamp; }
    public int getConsecutiveAnomalyMinutes() { return consecutiveAnomalyMinutes.get(); }
    public long getTotalSamples() { return totalSamples.get(); }
    public boolean isEnabled() { return enabled; }
    public boolean isLearningMode() { return learningMode; }
    public double getSensitivityMultiplier() { return sensitivityMultiplier; }
    public TrafficProfile getCurrentHourProfile() { return hourlyProfiles[currentHourSlot()]; }
    public TimeSeriesBuffer getConnectionsBuffer() { return connectionsBuffer; }
}
