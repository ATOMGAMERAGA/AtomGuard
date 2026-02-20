package com.atomguard.velocity.module.firewall;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;

import java.util.concurrent.TimeUnit;

/**
 * Güvenlik duvarı modülü. Config key: "guvenlik-duvari"
 *
 * <p>Güncellemeler (false positive önleme):
 * <ul>
 *   <li>Periyodik bakım: 10dk → 5dk</li>
 *   <li>Decay miktarı: 5 → 10</li>
 *   <li>{@link #recordViolation}: bağlamsal skor türü iletilir</li>
 * </ul>
 */
public class FirewallModule extends VelocityModule {

    private IPReputationEngine reputationEngine;
    private TempBanManager tempBanManager;
    private BlacklistManager blacklistManager;
    private WhitelistManager whitelistManager;
    private AutoBanEngine autoBanEngine;

    public FirewallModule(AtomGuardVelocity plugin) {
        super(plugin, "guvenlik-duvari");
    }

    @Override
    public int getPriority() { return 10; }

    @Override
    public void onConfigReload() {
        // Yeni değerleri configden çek ve engine'i güncelle
        int autoBanThreshold = getConfigInt("oto-yasak-esik", 150);
        int permanentBanThreshold = getConfigInt("kalici-yasak-esik", 500);
        
        if (reputationEngine != null) {
            reputationEngine.setThreshold(autoBanThreshold);
        }
        logger.info("Firewall yapılandırması dinamik olarak yenilendi.");
    }

    @Override
    public void onEnable() {
        int autoBanThreshold = getConfigInt("oto-yasak-esik", 150);
        long autoBanDurationMs = getConfigLong("oto-yasak-sure", 3600) * 1000L;
        int permanentBanThreshold = getConfigInt("kalici-yasak-esik", 500);
        int decayMinutes = getConfigInt("decay-dakika", 5);

        reputationEngine = new IPReputationEngine(plugin, autoBanThreshold);
        tempBanManager = new TempBanManager(plugin.getDataDirectory(), logger);
        blacklistManager = new BlacklistManager(plugin.getDataDirectory(), logger);
        whitelistManager = new WhitelistManager(plugin);
        autoBanEngine = new AutoBanEngine(plugin, reputationEngine, tempBanManager, blacklistManager,
                autoBanDurationMs, permanentBanThreshold);

        tempBanManager.load();
        blacklistManager.load();
        whitelistManager.load();

        // Whitelist'i Veritabanından Yükle
        if (plugin.getStorageProvider() != null) {
            plugin.getStorageProvider().loadWhitelist().thenAccept(ips -> {
                if (ips != null) ips.forEach(whitelistManager::add);
            });
        }

        // IP İtibarlarını Yükle
        if (plugin.getStorageProvider() != null) {
            plugin.getStorageProvider().loadIPReputations().thenAccept(reputationEngine::loadScores);
        }

        // Config'den gelen dakika aralığıyla bakım yap
        plugin.getProxyServer().getScheduler()
            .buildTask(plugin, this::periodicMaintenance)
            .repeat(decayMinutes, TimeUnit.MINUTES)
            .schedule();
    }

    @Override
    public void onDisable() {
        if (tempBanManager != null) tempBanManager.save();
        
        // IP İtibarlarını Kaydet
        if (plugin.getStorageProvider() != null && reputationEngine != null) {
            reputationEngine.getAllScores().forEach((ip, score) -> 
                plugin.getStorageProvider().saveIPReputation(ip, score));
        }
    }

    public FirewallCheckResult check(String ip) {
        if (!enabled) return new FirewallCheckResult(FirewallVerdict.ALLOW, "disabled");

        if (whitelistManager.isWhitelisted(ip))
            return new FirewallCheckResult(FirewallVerdict.ALLOW, "whitelist");

        if (blacklistManager.isBlacklisted(ip)) {
            incrementBlocked();
            return new FirewallCheckResult(FirewallVerdict.DENY, "blacklist");
        }

        if (tempBanManager.isBanned(ip)) {
            incrementBlocked();
            long remaining = tempBanManager.getRemainingMs(ip);
            return new FirewallCheckResult(FirewallVerdict.DENY, "temp-ban:" + remaining);
        }

        return new FirewallCheckResult(FirewallVerdict.ALLOW, "ok");
    }

    public void recordViolation(String ip, int points, String reason) {
        if (enabled && !whitelistManager.isWhitelisted(ip))
            autoBanEngine.recordViolation(ip, points, reason);
    }

    public void banIP(String ip, long durationMs, String reason) {
        banIPLocal(ip, durationMs, reason);
        
        // 2. Redis Sync (With real duration)
        if (plugin.getBackendCommunicator() != null) {
            plugin.getBackendCommunicator().broadcastIPBlock(ip, durationMs, reason);
        }

        // 3. Velocity Event
        if (plugin.getEventBus() != null) {
            plugin.getEventBus().fireIPBlocked(ip, reason, "firewall");
        }
    }

    public void banIPLocal(String ip, long durationMs, String reason) {
        tempBanManager.ban(ip, durationMs, reason);
        if (plugin.getAuditLogger() != null) {
            plugin.getAuditLogger().log(
                com.atomguard.velocity.audit.AuditLogger.EventType.IP_BANNED,
                ip, null, "firewall", "duration=" + (durationMs / 1000) + "s, reason=" + reason,
                com.atomguard.velocity.audit.AuditLogger.Severity.WARN
            );
        }
    }

    public void unbanIP(String ip) { 
        unbanIPLocal(ip);
        if (plugin.getBackendCommunicator() != null) {
            plugin.getBackendCommunicator().broadcastIPUnblock(ip);
        }
    }

    public void unbanIPLocal(String ip) {
        tempBanManager.unban(ip);
        if (plugin.getAuditLogger() != null) {
            plugin.getAuditLogger().log(
                com.atomguard.velocity.audit.AuditLogger.EventType.IP_UNBANNED,
                ip, null, "firewall", "Manual unban",
                com.atomguard.velocity.audit.AuditLogger.Severity.INFO
            );
        }
    }

    public void blacklistIP(String ip) { 
        blacklistIPLocal(ip);
        if (plugin.getBackendCommunicator() != null) {
            plugin.getBackendCommunicator().broadcastBlacklist(ip);
        }
    }

    public void blacklistIPLocal(String ip) {
        blacklistManager.add(ip);
    }
    
    public void whitelistIP(String ip) { 
        whitelistIPLocal(ip);
        if (plugin.getBackendCommunicator() != null) {
            plugin.getBackendCommunicator().broadcastWhitelistSync(ip);
        }
    }

    public void whitelistIPLocal(String ip) {
        whitelistManager.add(ip); 
        if (plugin.getStorageProvider() != null) {
            plugin.getStorageProvider().saveWhitelistIP(ip, "Sync/Manual");
        }
    }
    private void periodicMaintenance() {
        tempBanManager.cleanup();
        tempBanManager.save();
        // 5 → 10 puan decay (daha agresif temizleme)
        reputationEngine.decayAll(10);
    }

    public IPReputationEngine getReputationEngine() { return reputationEngine; }
    public TempBanManager getTempBanManager() { return tempBanManager; }
    public BlacklistManager getBlacklistManager() { return blacklistManager; }
    public WhitelistManager getWhitelistManager() { return whitelistManager; }

    public enum FirewallVerdict { ALLOW, DENY }
    public record FirewallCheckResult(FirewallVerdict verdict, String reason) {}
}
