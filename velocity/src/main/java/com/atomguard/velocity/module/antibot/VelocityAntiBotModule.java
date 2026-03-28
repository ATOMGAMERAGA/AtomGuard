package com.atomguard.velocity.module.antibot;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.data.ThreatScore;
import com.atomguard.velocity.module.VelocityModule;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Anti-bot modülü. Config key: "bot-protection"  (config.yml ile eşleşir)
 *
 * <p>Düzeltmeler (false positive önleme):
 * <ul>
 *   <li>Varsayılan eşikler güncellendi: windowSec=15, suspicious=8, highRisk=75, mediumRisk=45</li>
 *   <li>JoinPatternDetector: (120, 8, 15)</li>
 *   <li>{@link #markVerified}, {@link #isVerified}, {@link #revokeVerification} proxy metodları</li>
 *   <li>{@link #analyzePreLogin}: Doğrulanmış oyuncular → sıfır ThreatScore döner</li>
 * </ul>
 */
public class VelocityAntiBotModule extends VelocityModule {

    private BotDetectionEngine engine;
    private CaptchaVerification captcha;
    private boolean captchaEnabled;
    private NicknameBlocker nicknameBlocker;

    public VelocityAntiBotModule(AtomGuardVelocity plugin) {
        super(plugin, "bot-protection");
    }

    @Override
    public int getPriority() { return 50; }

    @Override
    public void onEnable() {
        int windowSec = getConfigInt("analysis-window", 15);
        int suspiciousThreshold = getConfigInt("suspicious-threshold", 8);
        int highRisk = getConfigInt("high-risk-threshold", 75);
        int mediumRisk = getConfigInt("medium-risk-threshold", 45);
        boolean enforceProtocols = getConfigBoolean("protocol-validation", true);
        boolean allowUnknownBrands = getConfigBoolean("allow-unknown-brands", true);
        List<String> blockedBrands = getConfigStringList("blocked-brands");
        List<String> allowedBrands = getConfigStringList("allowed-brands");

        // Config'den ek protokol numaraları — yeni MC sürümleri için plugin güncellemesi gerektirmez
        List<String> rawProtocols = getConfigStringList("extra-protocols");
        Set<Integer> extraProtocols = rawProtocols.stream()
            .map(s -> { try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; } })
            .filter(p -> p != null && p > 0)
            .collect(Collectors.toSet());

        captchaEnabled = getConfigBoolean("captcha.enabled", false);
        int captchaTimeout = getConfigInt("captcha.timeout", 60);

        nicknameBlocker = new NicknameBlocker(plugin);

        ConnectionAnalyzer connAnalyzer = new ConnectionAnalyzer(windowSec, suspiciousThreshold);
        HandshakeValidator hsValidator = new HandshakeValidator(enforceProtocols, extraProtocols);
        BrandAnalyzer brandAnalyzer = new BrandAnalyzer(blockedBrands, allowedBrands, allowUnknownBrands);
        // JoinPatternDetector parametreleri config'den okunur; minimum değerler detector içinde uygulanır
        int joinWindow = getConfigInt("join-window-seconds", 120);
        int maxJoins   = getConfigInt("max-joins-in-window", 8);
        int maxQuits   = getConfigInt("max-quits-before-suspect", 20);
        JoinPatternDetector joinDetector = new JoinPatternDetector(joinWindow, maxJoins, maxQuits);

        engine = new BotDetectionEngine(connAnalyzer, hsValidator, brandAnalyzer, joinDetector, highRisk, mediumRisk);
        captcha = new CaptchaVerification(captchaTimeout);

        plugin.getProxyServer().getScheduler()
            .buildTask(plugin, this::periodicCleanup)
            .repeat(2, TimeUnit.MINUTES)
            .schedule();
    }

    @Override
    public void onDisable() {}

    /**
     * Ön-giriş analizi. Doğrulanmış oyuncular → sıfır ThreatScore döner.
     * Nickname kontrolü artık AntiBotCheck pipeline adımında yapılıyor.
     */
    public ThreatScore analyzePreLogin(String ip, String username, String hostname, int port, int protocol) {
        // Doğrulanmış oyuncu bypass
        if (engine.isVerified(ip)) {
            return new ThreatScore();
        }

        engine.recordConnection(ip);
        return engine.analyze(ip, username, null, hostname, port, protocol);
    }

    /**
     * Kullanıcı adı yasaklı mı? AntiBotCheck pipeline adımı tarafından kullanılır.
     */
    public boolean isNicknameBlocked(String username) {
        return nicknameBlocker.check(username).isBlocked();
    }

    /**
     * Kullanıcı adı yasak sebebi. null → yasak yok.
     */
    public String getNicknameBlockReason(String username) {
        NicknameBlocker.NicknameCheckResult r = nicknameBlocker.check(username);
        return r.isBlocked() ? r.getReason() : null;
    }

    public void recordBrand(String ip, String brand) {
        if (!engine.isVerified(ip)) {
            // updateBrandScore kullan — tam analyze() çağırma.
            // Aksi hâlde resetForNewAnalysis() önceki analyzePreLogin() skorlarını siler.
            engine.updateBrandScore(ip, brand);
        }
    }

    public void recordJoin(String ip) { engine.recordJoin(ip); }
    public void recordQuit(String ip) { engine.recordQuit(ip); }

    public boolean isHighRisk(String ip) { return engine.isHighRisk(ip); }
    public boolean isMediumRisk(String ip) { return engine.isMediumRisk(ip); }
    public ThreatScore getScore(String ip) { return engine.getScore(ip); }

    /** IP'yi doğrulanmış oyuncu olarak işaretle (engine'e yönlendir) */
    public void markVerified(String ip) { 
        if (!engine.isVerified(ip)) {
            markVerifiedLocal(ip);

            // 2. Redis Sync
            if (plugin.getBackendCommunicator() != null) {
                plugin.getBackendCommunicator().broadcastPlayerVerified("Unknown", ip);
            }

            // 3. Velocity Event
            if (plugin.getEventBus() != null) {
                plugin.getEventBus().firePlayerVerified(ip, "Unknown");
            }
        }
    }

    public void markVerifiedLocal(String ip) {
        engine.markVerified(ip);
        if (plugin.getAuditLogger() != null) {
            plugin.getAuditLogger().log(com.atomguard.velocity.audit.AuditLogger.EventType.PLAYER_VERIFIED, ip, null, "antibot", "Verified", com.atomguard.velocity.audit.AuditLogger.Severity.INFO);
        }
    }

    @Override
    public void onConfigReload() {
        onDisable();
        onEnable();
        logger.info("Anti-Bot yapılandırması dinamik olarak yenilendi.");
    }

    /** IP doğrulanmış mı? (engine'e yönlendir) */
    public boolean isVerified(String ip) { return engine.isVerified(ip); }

    /** Doğrulama statüsünü iptal et (engine'e yönlendir) */
    public void revokeVerification(String ip) { engine.revokeVerification(ip); }

    public boolean isCaptchaEnabled() { return captchaEnabled; }
    public CaptchaVerification getCaptcha() { return captcha; }

    private void periodicCleanup() {
        engine.cleanup();
        captcha.cleanupExpired();
    }
}
