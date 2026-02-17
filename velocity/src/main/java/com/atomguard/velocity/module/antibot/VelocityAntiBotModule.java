package com.atomguard.velocity.module.antibot;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.data.ThreatScore;
import com.atomguard.velocity.module.VelocityModule;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Anti-bot modülü. Config key: "bot-koruma"
 */
public class VelocityAntiBotModule extends VelocityModule {

    private BotDetectionEngine engine;
    private CaptchaVerification captcha;
    private boolean captchaEnabled;

    public VelocityAntiBotModule(AtomGuardVelocity plugin) {
        super(plugin, "bot-koruma");
    }

    @Override
    public void onEnable() {
        int windowSec = getConfigInt("analiz-penceresi", 10);
        int suspiciousThreshold = getConfigInt("supheli-esik", 5);
        int highRisk = getConfigInt("yuksek-risk-esik", 70);
        int mediumRisk = getConfigInt("orta-risk-esik", 40);
        boolean enforceProtocols = getConfigBoolean("protokol-dogrulama", true);
        boolean allowUnknownBrands = getConfigBoolean("bilinmeyen-brand-izin", true);
        List<String> blockedBrands = getConfigStringList("engelli-brandlar");

        captchaEnabled = getConfigBoolean("captcha.aktif", false);
        int captchaTimeout = getConfigInt("captcha.sure", 60);

        ConnectionAnalyzer connAnalyzer = new ConnectionAnalyzer(windowSec, suspiciousThreshold);
        HandshakeValidator hsValidator = new HandshakeValidator(enforceProtocols);
        BrandAnalyzer brandAnalyzer = new BrandAnalyzer(blockedBrands, allowUnknownBrands);
        JoinPatternDetector joinDetector = new JoinPatternDetector(60, 5, 10);

        engine = new BotDetectionEngine(connAnalyzer, hsValidator, brandAnalyzer, joinDetector, highRisk, mediumRisk);
        captcha = new CaptchaVerification(captchaTimeout);

        plugin.getProxyServer().getScheduler()
            .buildTask(plugin, this::periodicCleanup)
            .repeat(2, TimeUnit.MINUTES)
            .schedule();
    }

    @Override
    public void onDisable() {}

    public ThreatScore analyzePreLogin(String ip, String username, String hostname, int port, int protocol) {
        engine.recordConnection(ip);
        return engine.analyze(ip, username, null, hostname, port, protocol);
    }

    public void recordBrand(String ip, String brand) {
        engine.analyze(ip, null, brand, null, 0, 0);
    }

    public void recordJoin(String ip) { engine.recordJoin(ip); }
    public void recordQuit(String ip) { engine.recordQuit(ip); }

    public boolean isHighRisk(String ip) { return engine.isHighRisk(ip); }
    public boolean isMediumRisk(String ip) { return engine.isMediumRisk(ip); }
    public ThreatScore getScore(String ip) { return engine.getScore(ip); }

    public boolean isCaptchaEnabled() { return captchaEnabled; }
    public CaptchaVerification getCaptcha() { return captcha; }

    private void periodicCleanup() {
        engine.cleanup();
        captcha.cleanupExpired();
    }
}
