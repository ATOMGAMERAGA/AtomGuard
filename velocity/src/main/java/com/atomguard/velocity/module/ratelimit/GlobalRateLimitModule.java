package com.atomguard.velocity.module.ratelimit;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;

import java.util.concurrent.TimeUnit;

/**
 * Global hız sınırlaması modülü. Config key: "hiz-siniri"
 */
public class GlobalRateLimitModule extends VelocityModule {

    private PerIPRateLimiter perIPLimiter;
    private PerSubnetRateLimiter perSubnetLimiter;
    private LoginRateLimiter loginLimiter;
    private PingRateLimiter pingLimiter;

    public GlobalRateLimitModule(AtomGuardVelocity plugin) {
        super(plugin, "hiz-siniri");
    }

    @Override
    public void onEnable() {
        int ipCapacity = getConfigInt("ip-kapasite", 20);
        int ipRefill = getConfigInt("ip-yenileme", 5);
        int subnetCapacity = getConfigInt("subnet-kapasite", 50);
        int subnetRefill = getConfigInt("subnet-yenileme", 15);
        int loginIpCap = getConfigInt("giris-ip-limit", 3);
        int loginGlobalCap = getConfigInt("giris-global-limit", 100);
        int pingLimit = getConfigInt("ping-limit", 5);

        perIPLimiter = new PerIPRateLimiter(ipCapacity, ipRefill, "ip");
        perSubnetLimiter = new PerSubnetRateLimiter(subnetCapacity, subnetRefill, 24);
        loginLimiter = new LoginRateLimiter(loginIpCap, 1, loginGlobalCap, 30);
        pingLimiter = new PingRateLimiter(pingLimit);

        plugin.getProxyServer().getScheduler()
            .buildTask(plugin, this::periodicCleanup)
            .repeat(1, TimeUnit.MINUTES)
            .schedule();
    }

    @Override
    public void onDisable() {}

    public boolean allowConnection(String ip) {
        if (!enabled) return true;
        if (!perSubnetLimiter.allowRequest(ip)) {
            incrementBlocked();
            plugin.getStatisticsManager().increment("rate_limited");
            return false;
        }
        if (!perIPLimiter.allowRequest(ip)) {
            incrementBlocked();
            plugin.getStatisticsManager().increment("rate_limited");
            return false;
        }
        return true;
    }

    public LoginRateLimiter.LoginCheckResult checkLogin(String ip) {
        if (!enabled) return new LoginRateLimiter.LoginCheckResult(true, "disabled");
        LoginRateLimiter.LoginCheckResult result = loginLimiter.checkLogin(ip);
        if (!result.allowed()) {
            incrementBlocked();
            plugin.getStatisticsManager().increment("login_rate_limited");
        }
        return result;
    }

    public boolean allowPing(String ip) {
        if (!enabled) return true;
        boolean allowed = pingLimiter.allowPing(ip);
        if (!allowed) {
            incrementBlocked();
            plugin.getStatisticsManager().increment("ping_rate_limited");
        }
        return allowed;
    }

    private void periodicCleanup() {
        if (perIPLimiter != null) perIPLimiter.cleanup();
        if (perSubnetLimiter != null) perSubnetLimiter.cleanup();
        if (loginLimiter != null) loginLimiter.cleanup();
        if (pingLimiter != null) pingLimiter.cleanup();
    }
}
