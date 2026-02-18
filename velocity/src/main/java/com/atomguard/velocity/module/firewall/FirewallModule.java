package com.atomguard.velocity.module.firewall;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;

import java.util.concurrent.TimeUnit;

/**
 * Güvenlik duvarı modülü. Config key: "guvenlik-duvari"
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
    public void onEnable() {
        int autoBanThreshold = getConfigInt("oto-yasak-esik", 100);
        long autoBanDurationMs = getConfigLong("oto-yasak-sure", 3600) * 1000L;
        int permanentBanThreshold = getConfigInt("kalici-yasak-esik", 500);

        reputationEngine = new IPReputationEngine(autoBanThreshold);
        tempBanManager = new TempBanManager(plugin.getDataDirectory(), logger);
        blacklistManager = new BlacklistManager(plugin.getDataDirectory(), logger);
        whitelistManager = new WhitelistManager(plugin);
        autoBanEngine = new AutoBanEngine(plugin, reputationEngine, tempBanManager, blacklistManager,
                autoBanDurationMs, permanentBanThreshold);

        tempBanManager.load();
        blacklistManager.load();
        whitelistManager.load();

        plugin.getProxyServer().getScheduler()
            .buildTask(plugin, this::periodicMaintenance)
            .repeat(10, TimeUnit.MINUTES)
            .schedule();
    }

    @Override
    public void onDisable() {
        if (tempBanManager != null) tempBanManager.save();
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
        tempBanManager.ban(ip, durationMs, reason);
    }

    public void unbanIP(String ip) { tempBanManager.unban(ip); }
    public void blacklistIP(String ip) { blacklistManager.add(ip); }
    public void whitelistIP(String ip) { whitelistManager.add(ip); }

    private void periodicMaintenance() {
        tempBanManager.cleanup();
        tempBanManager.save();
        reputationEngine.decayAll(5);
    }

    public IPReputationEngine getReputationEngine() { return reputationEngine; }
    public TempBanManager getTempBanManager() { return tempBanManager; }
    public BlacklistManager getBlacklistManager() { return blacklistManager; }
    public WhitelistManager getWhitelistManager() { return whitelistManager; }

    public enum FirewallVerdict { ALLOW, DENY }
    public record FirewallCheckResult(FirewallVerdict verdict, String reason) {}
}
