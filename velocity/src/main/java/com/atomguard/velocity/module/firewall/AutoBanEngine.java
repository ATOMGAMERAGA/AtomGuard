package com.atomguard.velocity.module.firewall;

import com.atomguard.velocity.AtomGuardVelocity;

/**
 * Otomatik yasaklama motoru — itibar puanı eşiğini aşan IP'leri yasaklar.
 *
 * <p>Güncelleme: {@link #recordViolation} artık bağlamsal skorlama kullanır
 * ({@link IPReputationEngine#addContextualScore}).
 */
public class AutoBanEngine {

    private final AtomGuardVelocity plugin;
    private final IPReputationEngine reputationEngine;
    private final TempBanManager tempBanManager;
    private final BlacklistManager blacklistManager;
    private final long autoBanDurationMs;
    private final int permanentBanThreshold;

    public AutoBanEngine(AtomGuardVelocity plugin, IPReputationEngine reputationEngine,
                          TempBanManager tempBanManager, BlacklistManager blacklistManager,
                          long autoBanDurationMs, int permanentBanThreshold) {
        this.plugin = plugin;
        this.reputationEngine = reputationEngine;
        this.tempBanManager = tempBanManager;
        this.blacklistManager = blacklistManager;
        this.autoBanDurationMs = autoBanDurationMs;
        this.permanentBanThreshold = permanentBanThreshold;
    }

    public BanResult checkAndBan(String ip, String reason) {
        int score = reputationEngine.getScore(ip);

        if (score >= permanentBanThreshold) {
            blacklistManager.add(ip);
            reputationEngine.reset(ip);
            plugin.getAlertManager().alertBanned(ip, reason + " (kalıcı)");
            plugin.getLogManager().warn("Kalıcı yasak: " + ip + " - " + reason);
            plugin.getStatisticsManager().increment("permanent_bans");
            return new BanResult(BanType.PERMANENT, score);
        }

        if (reputationEngine.shouldAutoBan(ip)) {
            tempBanManager.ban(ip, autoBanDurationMs, reason);
            reputationEngine.reset(ip);
            plugin.getAlertManager().alertBanned(ip, reason + " (geçici)");
            plugin.getLogManager().warn("Geçici yasak: " + ip + " - " + reason);
            plugin.getStatisticsManager().increment("temp_bans");
            return new BanResult(BanType.TEMPORARY, score);
        }

        return new BanResult(BanType.NONE, score);
    }

    /**
     * İhlal kaydet — bağlamsal skor ekler (ihlal türüne göre çarpan uygulanır).
     *
     * @param ip          IP adresi
     * @param scorePoints temel puan
     * @param reason      ihlal türü (Türkçe key, örn: "bot-tespiti", "exploit")
     */
    public void recordViolation(String ip, int scorePoints, String reason) {
        reputationEngine.addContextualScore(ip, scorePoints, reason);
        checkAndBan(ip, reason);
    }

    public enum BanType { NONE, TEMPORARY, PERMANENT }
    public record BanResult(BanType type, int scoreAtBan) {}
}
