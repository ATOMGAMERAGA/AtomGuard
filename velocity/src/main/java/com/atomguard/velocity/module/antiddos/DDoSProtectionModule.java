package com.atomguard.velocity.module.antiddos;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Ana DDoS Koruma Modülü — tamamen yeniden yazılmış, tüm alt sistemler entegre.
 * <p>
 * Config anahtarı: {@code moduller.ddos-koruma}
 * <p>
 * Alt sistemler:
 * <ol>
 *   <li>{@link AttackLevelManager} — 5 kademeli saldırı seviyesi (hysteresis)</li>
 *   <li>{@link SynFloodDetector} — CPS ölçümü ve flood tespiti</li>
 *   <li>{@link TrafficAnomalyDetector} — istatistiksel anomali tespiti</li>
 *   <li>{@link SubnetAnalyzer} — /24 ve /16 subnet bazlı analiz</li>
 *   <li>{@link ConnectionFingerprinter} — bağlantı parmak izi sistemi</li>
 *   <li>{@link EnhancedSlowlorisDetector} — gelişmiş slowloris tespiti</li>
 *   <li>{@link IPReputationTracker} — DDoS'a özel IP itibar sistemi</li>
 *   <li>{@link AttackSessionRecorder} — saldırı oturum kaydedici</li>
 *   <li>{@link AttackClassifier} — saldırı tipi sınıflandırıcı</li>
 *   <li>{@link VerifiedPlayerShield} — doğrulanmış oyuncu koruma katmanı</li>
 *   <li>{@link DDoSMetricsCollector} — gerçek zamanlı metrikler</li>
 *   <li>{@link ConnectionThrottler} — IP başına bağlantı throttle</li>
 *   <li>{@link SmartThrottleEngine} — adaptif throttle motoru</li>
 *   <li>{@link PingFloodDetector} — ping flood tespiti</li>
 *   <li>{@link NullPingDetector} — geçersiz handshake tespiti</li>
 *   <li>{@link GeoBlocker} — ülke bazlı engelleme</li>
 * </ol>
 */
public class DDoSProtectionModule extends VelocityModule {

    // ────────────────────────────────────────────────────────
    // Alt sistem referansları
    // ────────────────────────────────────────────────────────

    private AttackLevelManager        levelManager;
    private SynFloodDetector          synFloodDetector;
    private TrafficAnomalyDetector    anomalyDetector;
    private SubnetAnalyzer            subnetAnalyzer;
    private ConnectionFingerprinter   fingerprinter;
    private EnhancedSlowlorisDetector slowlorisDetector;
    private IPReputationTracker       reputationTracker;
    private AttackSessionRecorder     sessionRecorder;
    private AttackClassifier          classifier;
    private VerifiedPlayerShield      playerShield;
    private DDoSMetricsCollector      metricsCollector;
    private ConnectionThrottler       throttler;
    private SmartThrottleEngine       throttleEngine;
    private PingFloodDetector         pingFloodDetector;
    private NullPingDetector          nullPingDetector;
    private GeoBlocker                geoBlocker;

    public DDoSProtectionModule(AtomGuardVelocity plugin) {
        super(plugin, "ddos-koruma");
    }

    @Override
    public int getPriority() { return 30; }

    // ────────────────────────────────────────────────────────
    // Yaşam döngüsü
    // ────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        // ── 1. Temel parametreler ──────────────────────────
        int baseCpsThreshold   = getConfigInt("saldiri-modu.esik", 30);
        int connPerMinute      = getConfigInt("baglanti-limiti.ip-basina-dakika", 5);
        int maxPingPerSecond   = getConfigInt("ping-limit", 3);

        // ── 2. AttackLevelManager ──────────────────────────
        long hysteresisUpMs   = getConfigLong("saldiri-seviyeleri.hysteresis-yukselme-sn", 3) * 1000L;
        long hysteresisDownMs = getConfigLong("saldiri-seviyeleri.hysteresis-dusme-sn", 15) * 1000L;
        boolean levelsEnabled = getConfigBoolean("saldiri-seviyeleri.aktif", true);

        levelManager = new AttackLevelManager(plugin, baseCpsThreshold,
                hysteresisUpMs, hysteresisDownMs);

        // ── 3. Anomali dedektörü ───────────────────────────
        boolean anomalyEnabled   = getConfigBoolean("anomali-tespiti.aktif", true);
        double  zScoreThreshold  = getConfigDouble("anomali-tespiti.z-skoru-esigi", 3.0);
        double  slowRampPct      = getConfigDouble("anomali-tespiti.yavas-rampa-esigi-yuzde", 10.0);
        boolean pulseDetection   = getConfigBoolean("anomali-tespiti.pulse-tespit-aktif", true);
        int     profileWindow    = getConfigInt("anomali-tespiti.profil-penceresi-saat", 1) * 3600;

        anomalyDetector = anomalyEnabled
                ? new TrafficAnomalyDetector(profileWindow, zScoreThreshold, slowRampPct, pulseDetection)
                : null;

        // ── 4. Subnet analizi ─────────────────────────────
        int     subnetMultiplier   = getConfigInt("baglanti-limiti.subnet-basina-dakika", 20);
        boolean subnetBanEnabled   = getConfigBoolean("baglanti-limiti.subnet-ban-aktif", false);
        int     subnetBanThreshold = getConfigInt("baglanti-limiti.subnet-ban-esik", 10);

        subnetAnalyzer = new SubnetAnalyzer(5, 10, subnetBanEnabled, subnetBanThreshold);

        // ── 5. Parmak izi sistemi ──────────────────────────
        boolean fpEnabled      = getConfigBoolean("parmak-izi.aktif", true);
        int     fpMassThreshold = getConfigInt("parmak-izi.kitle-esigi", 20);
        List<String> botPatterns = getConfigStringList("parmak-izi.bilinen-botnet-patternler");

        fingerprinter = fpEnabled
                ? new ConnectionFingerprinter(fpMassThreshold, botPatterns)
                : null;

        // ── 6. Slowloris dedektörü ─────────────────────────
        int    maxPendingPerIP   = getConfigInt("slowloris-gelismis.maks-pending-ip", 5);
        long   connTimeoutMs     = getConfigLong("slowloris-gelismis.zaman-asimi-ms", 10_000L);
        double pendingRatioAlarm = getConfigDouble("slowloris-gelismis.pending-oran-alarm", 0.30);
        boolean keepAliveCheck   = getConfigBoolean("slowloris-gelismis.keep-alive-kontrol", true);

        slowlorisDetector = new EnhancedSlowlorisDetector(
                maxPendingPerIP, connTimeoutMs, pendingRatioAlarm, keepAliveCheck);

        // ── 7. IP itibar takibi ────────────────────────────
        boolean repEnabled = getConfigBoolean("ip-itibar.aktif", true);
        if (repEnabled) {
            reputationTracker = new IPReputationTracker(
                    getConfigInt("ip-itibar.baslangic-skoru", 50),
                    getConfigInt("ip-itibar.basarili-baglanti-bonus", 5),
                    getConfigInt("ip-itibar.rate-limit-ceza", 10),
                    getConfigInt("ip-itibar.gecersiz-handshake-ceza", 15),
                    getConfigInt("ip-itibar.slowloris-ceza", 25),
                    getConfigInt("ip-itibar.saldiri-baglanti-ceza", 20),
                    getConfigInt("ip-itibar.otomatik-ban-esik-1saat", 20),
                    getConfigInt("ip-itibar.otomatik-ban-esik-24saat", 5),
                    getConfigInt("ip-itibar.decay-saat-puan", 3),
                    getConfigInt("ip-itibar.dogrulanmis-minimum-skor", 30)
            );
            // Firewall modülü bağlantısı (geç bağlanma — modüller birbirinden bağımsız yüklenir)
            if (plugin.getFirewallModule() != null) {
                reputationTracker.setFirewallModule(plugin.getFirewallModule());
            }
        }

        // ── 8. Saldırı oturum kaydedici ───────────────────
        boolean sessionEnabled  = getConfigBoolean("saldiri-kayit.aktif", true);
        boolean jsonSave        = getConfigBoolean("saldiri-kayit.json-kaydet", true);
        int     maxHistory      = getConfigInt("saldiri-kayit.maks-gecmis", 50);
        long    snapshotIntervalMs = getConfigLong("saldiri-kayit.snapshot-araligi-sn", 5) * 1000L;

        sessionRecorder = sessionEnabled
                ? new AttackSessionRecorder(maxHistory, snapshotIntervalMs, jsonSave,
                                            plugin.getDataDirectory(), logger)
                : null;

        // ── 9. Saldırı sınıflandırıcı ─────────────────────
        classifier = new AttackClassifier();

        // ── 10. Doğrulanmış oyuncu kalkanı ─────────────────
        int  guaranteedSlots = getConfigInt("saldiri-seviyeleri.dogrulanmis-ayrilmis-slot", 50);
        playerShield = new VerifiedPlayerShield(plugin, guaranteedSlots, 3_600_000L);

        // ── 11. Metrik toplayıcı ───────────────────────────
        boolean metricsEnabled = getConfigBoolean("metrikler.aktif", true);
        int     metricsHistMin = getConfigInt("metrikler.gecmis-dakika", 60);
        metricsCollector = metricsEnabled ? new DDoSMetricsCollector(metricsHistMin) : null;

        // ── 12. Throttle bileşenleri ───────────────────────
        throttler     = new ConnectionThrottler(connPerMinute);
        pingFloodDetector = new PingFloodDetector(maxPingPerSecond);
        nullPingDetector  = new NullPingDetector();

        int normalLimit     = getConfigInt("akilli-throttle.normal-limit", 10);
        int carefulLimit    = getConfigInt("akilli-throttle.dikkatli-limit", 5);
        int aggressiveLimit = getConfigInt("akilli-throttle.agresif-limit", 2);
        int lockdownLimit   = getConfigInt("akilli-throttle.lockdown-limit", 0);
        throttleEngine = new SmartThrottleEngine(plugin, normalLimit, carefulLimit,
                aggressiveLimit, lockdownLimit);
        if (levelsEnabled) throttleEngine.setLevelManager(levelManager);

        // ── 13. GeoBlocker ─────────────────────────────────
        if (getConfigBoolean("geo-engelleme.aktif", false)) {
            List<String> blocked = getConfigStringList("geo-engelleme.engelli-ulkeler");
            List<String> allowed = getConfigStringList("geo-engelleme.izinli-ulkeler");
            boolean wlMode       = getConfigBoolean("geo-engelleme.izinli-ulkeler-modu", false);
            geoBlocker = new GeoBlocker(plugin.getDataDirectory(), blocked, allowed, wlMode, logger);
            try {
                geoBlocker.initialize();
            } catch (Exception e) {
                logger.error("GeoIP başlatılamadı: {}", e.getMessage());
                geoBlocker = null;
            }
        }

        // ── 14. SynFloodDetector — tüm bileşenleri bağla ──
        synFloodDetector = new SynFloodDetector(plugin, baseCpsThreshold);
        if (levelsEnabled) synFloodDetector.setLevelManager(levelManager);
        if (anomalyDetector != null) synFloodDetector.setAnomalyDetector(anomalyDetector);
        if (metricsCollector != null) synFloodDetector.setMetricsCollector(metricsCollector);
        if (sessionRecorder != null)  synFloodDetector.setSessionRecorder(sessionRecorder);
        synFloodDetector.setClassifier(classifier);

        // ── 15. Periyodik görevler ─────────────────────────
        plugin.getProxyServer().getScheduler()
                .buildTask(plugin, this::periodicCleanup)
                .repeat(1, TimeUnit.MINUTES)
                .schedule();

        plugin.getProxyServer().getScheduler()
                .buildTask(plugin, this::hourlyMaintenance)
                .repeat(1, TimeUnit.HOURS)
                .schedule();

        logger.info("DDoS Koruma Modülü başlatıldı. CPS eşiği: {}, Seviye sistemi: {}",
                baseCpsThreshold, levelsEnabled ? "aktif" : "kapalı");
    }

    @Override
    public void onDisable() {
        if (geoBlocker != null)     geoBlocker.close();
        if (synFloodDetector != null) synFloodDetector.shutdown();
        if (sessionRecorder != null)  sessionRecorder.shutdown();
    }

    @Override
    public void onConfigReload() {
        logger.info("DDoS koruma ayarları yenilendi — modülü yeniden başlatın.");
    }

    // ────────────────────────────────────────────────────────
    // Ana kontrol metotları
    // ────────────────────────────────────────────────────────

    /**
     * Gelen bağlantıyı kontrol et.
     * <p>
     * Kontrol sırası: Slowloris → Level/Shield → Subnet → Fingerprint →
     * Throttle Engine → Throttler → Reputation → GeoBlock
     *
     * @param ip         Kaynak IP
     * @param isVerified Oyuncu daha önce başarıyla giriş yapmış mı?
     * @return Bağlantı izni ve sebep
     */
    public ConnectionCheckResult checkConnection(String ip, boolean isVerified) {
        if (!enabled) return new ConnectionCheckResult(true, "disabled", null);

        // CPS sayacını artır
        synFloodDetector.recordConnection();

        // ── 1. Slowloris kontrolü ──────────────────────────
        if (slowlorisDetector.isSlowlorisIP(ip)) {
            return deny(ip, "slowloris", "kick.ddos");
        }

        if (slowlorisDetector.isSystemUnderSlowlorisLoad()) {
            // Sistem geneli yüklü — verified bypass
            if (!isVerified) return deny(ip, "slowloris-system", "kick.ddos");
        }

        // ── 2. Saldırı seviyesi + Oyuncu kalkanı ──────────
        if (levelManager != null) {
            AttackLevelManager.AttackLevel level = levelManager.getCurrentLevel();

            // PlayerShield slot kontrolü (CRITICAL/LOCKDOWN)
            if (!playerShield.shouldAllow(ip, level)) {
                return deny(ip, "shield-slot", "kick.ddos");
            }

            // Seviye bazlı izin kontrolü
            if (!levelManager.shouldAllowConnection(ip, isVerified)) {
                return deny(ip, "attack-level-" + level.name().toLowerCase(), "kick.ddos");
            }
        }

        // ── 3. Subnet kontrolü ────────────────────────────
        if (subnetAnalyzer.recordAndCheck(ip)) {
            return deny(ip, "subnet-ban", "kick.ddos");
        }

        // ── 4. Parmak izi kontrolü ────────────────────────
        if (fingerprinter != null && fingerprinter.isIPFlaggedByFingerprint(ip)) {
            return deny(ip, "fingerprint", "kick.ddos");
        }

        // ── 5. Adaptif throttle ───────────────────────────
        if (!throttleEngine.shouldAllow(ip, isVerified)) {
            return deny(ip, "throttle-engine", "kick.ddos");
        }

        // ── 6. Bağlantı throttle ──────────────────────────
        boolean throttled = plugin.isAttackMode()
                ? !throttler.tryConnectAttackMode(ip)
                : !throttler.tryConnect(ip);

        if (throttled) {
            if (reputationTracker != null) reputationTracker.recordRateLimitViolation(ip);
            return deny(ip, "throttle", "kick.rate-limit");
        }

        // ── 7. IP itibar kontrolü ─────────────────────────
        if (reputationTracker != null) {
            if (reputationTracker.isLowReputation(ip)) {
                return deny(ip, "low-reputation", "kick.ddos");
            }
            // Saldırı sırasında bağlantı → itibar cezası
            if (plugin.isAttackMode()) {
                reputationTracker.recordAttackConnection(ip);
            }
        }

        // ── 8. Geo engelleme ──────────────────────────────
        if (geoBlocker != null && geoBlocker.isBlocked(ip)) {
            return deny(ip, "geo", "kick.genel");
        }

        // ── Başarılı bağlantı ─────────────────────────────
        if (reputationTracker != null) reputationTracker.recordSuccess(ip);
        if (metricsCollector != null)  metricsCollector.recordAllowed(ip);
        if (sessionRecorder != null)   sessionRecorder.recordCps(synFloodDetector.getCurrentRate());

        return new ConnectionCheckResult(true, "ok", null);
    }

    private ConnectionCheckResult deny(String ip, String reason, String kickKey) {
        incrementBlocked();
        plugin.getStatisticsManager().increment("ddos_blocked");
        if (metricsCollector != null) metricsCollector.recordBlocked(ip);
        if (sessionRecorder != null)  sessionRecorder.recordBlockedConnection(ip);
        if (subnetAnalyzer != null)   subnetAnalyzer.penalizeSubnet(
                SubnetAnalyzer.getSubnet24(ip), 5, reason);
        return new ConnectionCheckResult(false, reason, kickKey);
    }

    /**
     * Ping isteğine izin ver ya da reddet.
     *
     * @param ip Kaynak IP
     * @return true ise ping kabul edildi
     */
    public boolean checkPing(String ip) {
        if (!enabled) return true;
        boolean allowed = pingFloodDetector.allowPing(ip);
        if (!allowed) {
            plugin.getStatisticsManager().increment("ddos_ping_blocked");
        }
        return allowed;
    }

    /**
     * Handshake parametrelerini doğrula.
     *
     * @param ip              Kaynak IP
     * @param hostname        Handshake hostname'i
     * @param port            Handshake portu
     * @param protocolVersion Minecraft protokol versiyonu
     * @return Handshake geçerli mi?
     */
    public HandshakeCheckResult checkHandshake(String ip, String hostname, int port, int protocolVersion) {
        if (!enabled) return new HandshakeCheckResult(true, "ok");

        if (!nullPingDetector.isValidHandshake(hostname, port, protocolVersion)) {
            nullPingDetector.recordInvalid(ip);
            if (reputationTracker != null) reputationTracker.recordInvalidHandshake(ip);
            incrementBlocked();
            return new HandshakeCheckResult(false, "Geçersiz handshake parametreleri");
        }

        if (nullPingDetector.isBlocked(ip)) {
            incrementBlocked();
            return new HandshakeCheckResult(false, "Tekrarlayan geçersiz handshake");
        }

        // Parmak izi kaydet
        if (fingerprinter != null) {
            String fp = fingerprinter.recordAndGetFingerprint(ip, hostname, protocolVersion, -1);
            if (fingerprinter.matchesKnownBotPattern(fp)) {
                incrementBlocked();
                return new HandshakeCheckResult(false, "Bilinen botnet parmak izi");
            }
        }

        return new HandshakeCheckResult(true, "ok");
    }

    // ────────────────────────────────────────────────────────
    // Slowloris yaşam döngüsü API
    // ────────────────────────────────────────────────────────

    /** Yeni bağlantı başladığında çağrılır. */
    public void onConnectionStarted(String ip, String connId) {
        if (slowlorisDetector != null) slowlorisDetector.onConnectionStarted(ip, connId);
    }

    /** Handshake tamamlandığında çağrılır. */
    public void onHandshakeComplete(String ip, String connId) {
        if (slowlorisDetector != null) slowlorisDetector.onHandshakeComplete(ip, connId);
    }

    /** Bağlantı kapandığında çağrılır. */
    public void onConnectionClosed(String ip, String connId) {
        if (slowlorisDetector != null) slowlorisDetector.onConnectionClosed(ip, connId);
        AttackLevelManager.AttackLevel level = levelManager != null
                ? levelManager.getCurrentLevel() : AttackLevelManager.AttackLevel.NONE;
        if (playerShield != null) playerShield.onConnectionClosed(ip, level);
    }

    // ────────────────────────────────────────────────────────
    // Olaylar
    // ────────────────────────────────────────────────────────

    /** Başarılı giriş olayı — tüm itibar sistemlerine bildir. */
    public void onSuccessfulLogin(String ip) {
        if (reputationTracker != null) {
            reputationTracker.recordSuccess(ip);
            reputationTracker.markVerified(ip);
        }
        if (subnetAnalyzer != null) {
            subnetAnalyzer.rewardSubnet(ip, 2);
        }
    }

    // ────────────────────────────────────────────────────────
    // Periyodik bakım
    // ────────────────────────────────────────────────────────

    private void periodicCleanup() {
        if (throttler != null)        throttler.cleanup();
        if (nullPingDetector != null) nullPingDetector.cleanup();
        if (pingFloodDetector != null) pingFloodDetector.cleanup();
        if (slowlorisDetector != null) slowlorisDetector.cleanupExpiredConnections();
        if (throttleEngine != null)   throttleEngine.cleanup();
        if (subnetAnalyzer != null)   subnetAnalyzer.cleanup();
        if (fingerprinter != null)    fingerprinter.cleanup();
        if (metricsCollector != null) metricsCollector.cleanup();
    }

    private void hourlyMaintenance() {
        if (reputationTracker != null) reputationTracker.periodicMaintenance();
    }

    // ────────────────────────────────────────────────────────
    // Getters (alt sistemlere dışarıdan erişim)
    // ────────────────────────────────────────────────────────

    public AttackLevelManager        getLevelManager()       { return levelManager; }
    public SynFloodDetector          getSynFloodDetector()   { return synFloodDetector; }
    public TrafficAnomalyDetector    getAnomalyDetector()    { return anomalyDetector; }
    public SubnetAnalyzer            getSubnetAnalyzer()     { return subnetAnalyzer; }
    public ConnectionFingerprinter   getFingerprinter()      { return fingerprinter; }
    public EnhancedSlowlorisDetector getSlowlorisDetector()  { return slowlorisDetector; }
    public IPReputationTracker       getReputationTracker()  { return reputationTracker; }
    public AttackSessionRecorder     getSessionRecorder()    { return sessionRecorder; }
    public AttackClassifier          getClassifier()         { return classifier; }
    public VerifiedPlayerShield      getPlayerShield()       { return playerShield; }
    public DDoSMetricsCollector      getMetricsCollector()   { return metricsCollector; }
    public ConnectionThrottler       getThrottler()          { return throttler; }
    public SmartThrottleEngine       getThrottleEngine()     { return throttleEngine; }
    public PingFloodDetector         getPingFloodDetector()  { return pingFloodDetector; }
    public GeoBlocker                getGeoBlocker()         { return geoBlocker; }

    // ────────────────────────────────────────────────────────
    // Kayıt tipleri (public — dışarıdan erişilebilir)
    // ────────────────────────────────────────────────────────

    /**
     * Bağlantı kontrol sonucu.
     *
     * @param allowed       Bağlantıya izin verildi mi?
     * @param reason        Sebep (log için)
     * @param kickMessageKey Kick mesaj anahtarı (null ise kick yok)
     */
    public record ConnectionCheckResult(boolean allowed, String reason, String kickMessageKey) {}

    /**
     * Handshake kontrol sonucu.
     *
     * @param valid  Handshake geçerli mi?
     * @param reason Sebep
     */
    public record HandshakeCheckResult(boolean valid, String reason) {}
}
