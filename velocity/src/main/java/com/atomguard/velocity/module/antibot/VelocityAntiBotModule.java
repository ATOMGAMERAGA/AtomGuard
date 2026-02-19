package com.atomguard.velocity.module.antibot;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.data.ThreatScore;
import com.atomguard.velocity.module.VelocityModule;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Anti-bot modülü. Config key: "bot-koruma"
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
        super(plugin, "bot-koruma");
    }

    @Override
    public void onEnable() {
        int windowSec = getConfigInt("analiz-penceresi", 15);
        int suspiciousThreshold = getConfigInt("supheli-esik", 8);
        int highRisk = getConfigInt("yuksek-risk-esik", 75);
        int mediumRisk = getConfigInt("orta-risk-esik", 45);
        boolean enforceProtocols = getConfigBoolean("protokol-dogrulama", true);
        boolean allowUnknownBrands = getConfigBoolean("bilinmeyen-brand-izin", true);
        List<String> blockedBrands = getConfigStringList("engelli-brandlar");

        captchaEnabled = getConfigBoolean("captcha.aktif", false);
        int captchaTimeout = getConfigInt("captcha.sure", 60);

        nicknameBlocker = new NicknameBlocker(plugin);

        ConnectionAnalyzer connAnalyzer = new ConnectionAnalyzer(windowSec, suspiciousThreshold);
        HandshakeValidator hsValidator = new HandshakeValidator(enforceProtocols);
        BrandAnalyzer brandAnalyzer = new BrandAnalyzer(blockedBrands, allowUnknownBrands);
        // maxJoinsInWindow=8, maxQuitsBeforeSuspect=15 (minimum değerler)
        JoinPatternDetector joinDetector = new JoinPatternDetector(120, 8, 15);

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
     */
    public ThreatScore analyzePreLogin(String ip, String username, String hostname, int port, int protocol) {
        // Doğrulanmış oyuncu bypass
        if (engine.isVerified(ip)) {
            return new ThreatScore();
        }

        NicknameBlocker.NicknameCheckResult nickResult = nicknameBlocker.check(username);
        if (nickResult.isBlocked()) {
            ThreatScore score = new ThreatScore();
            score.setUsernameScore(100);
            score.setConnectionRateScore(100);
            score.setHandshakeScore(100);
            score.setBrandScore(100);
            score.setJoinPatternScore(100);
            score.setGeoScore(100);
            score.setProtocolScore(100);
            score.calculate();
            return score;
        }

        engine.recordConnection(ip);
        return engine.analyze(ip, username, null, hostname, port, protocol);
    }

    public void recordBrand(String ip, String brand) {
        if (!engine.isVerified(ip)) {
            engine.analyze(ip, null, brand, null, 0, 0);
        }
    }

    public void recordJoin(String ip) { engine.recordJoin(ip); }
    public void recordQuit(String ip) { engine.recordQuit(ip); }

    public boolean isHighRisk(String ip) { return engine.isHighRisk(ip); }
    public boolean isMediumRisk(String ip) { return engine.isMediumRisk(ip); }
    public ThreatScore getScore(String ip) { return engine.getScore(ip); }

    /** IP'yi doğrulanmış oyuncu olarak işaretle (engine'e yönlendir) */
    public void markVerified(String ip) { engine.markVerified(ip); }

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
