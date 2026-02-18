package com.atomguard.velocity.module.antivpn;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * VPN/proxy tespit modülü. Config key: "vpn-proxy-engelleme"
 */
public class VPNDetectionModule extends VelocityModule {

    private VPNResultCache cache;
    private LocalProxyListChecker localChecker;
    private CIDRBlocker cidrBlocker;
    private ASNBlocker asnBlocker;
    private DNSBLChecker dnsblChecker;
    private ProxyCheckProvider proxyCheck;
    private IPApiProvider ipApi;
    private IPHubProvider ipHub;
    private boolean allowPremium;

    public VPNDetectionModule(AtomGuardVelocity plugin) {
        super(plugin, "vpn-proxy-engelleme");
    }

    @Override
    public void onEnable() {
        long cacheTtlMs = getConfigLong("onbellek-sure", 3600) * 1000L;
        allowPremium = getConfigBoolean("premium-izin", true);

        cache = new VPNResultCache(cacheTtlMs);
        localChecker = new LocalProxyListChecker(plugin.getDataDirectory(), logger);
        localChecker.load();

        cidrBlocker = new CIDRBlocker();
        cidrBlocker.addRanges(getConfigStringList("engelli-cidr"));

        asnBlocker = new ASNBlocker();
        getConfigStringList("engelli-asn").forEach(asnBlocker::addASN);

        List<String> dnsbls = getConfigStringList("dnsbl-listesi");
        dnsblChecker = new DNSBLChecker(dnsbls);

        proxyCheck = new ProxyCheckProvider(getConfigString("proxycheck-api-key", ""));
        ipApi = new IPApiProvider();
        ipHub = new IPHubProvider(getConfigString("iphub-api-key", ""));

        plugin.getProxyServer().getScheduler()
            .buildTask(plugin, cache::cleanup)
            .repeat(10, TimeUnit.MINUTES)
            .schedule();
    }

    @Override
    public void onDisable() {}

    public CompletableFuture<DetectionResult> check(String ip, boolean isPremium) {
        if (!enabled) return CompletableFuture.completedFuture(new DetectionResult(false, "disabled"));

        if (allowPremium && isPremium)
            return CompletableFuture.completedFuture(new DetectionResult(false, "premium-exempt"));

        // Önbellekte var mı?
        VPNResultCache.CacheResult cached = cache.get(ip);
        if (cached != null) {
            if (cached.isVPN()) incrementBlocked();
            return CompletableFuture.completedFuture(
                new DetectionResult(cached.isVPN(), "cache:" + cached.provider()));
        }

        // Yerel kontroller (hızlı)
        if (localChecker.isProxy(ip)) {
            cache.put(ip, true, "local-list");
            incrementBlocked();
            plugin.getStatisticsManager().increment("vpn_blocked");
            return CompletableFuture.completedFuture(new DetectionResult(true, "local-list"));
        }

        if (cidrBlocker.isBlocked(ip)) {
            cache.put(ip, true, "cidr");
            incrementBlocked();
            plugin.getStatisticsManager().increment("vpn_blocked");
            return CompletableFuture.completedFuture(new DetectionResult(true, "cidr"));
        }

        // Asenkron API kontrolleri
        return runApiChecks(ip);
    }

    private CompletableFuture<DetectionResult> runApiChecks(String ip) {
        CompletableFuture<Boolean> dnsblCheck = dnsblChecker.isListed(ip);
        CompletableFuture<Boolean> ipApiCheck = ipApi.isAvailable() ? ipApi.isVPN(ip) : CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> proxyCheckCheck = proxyCheck.isAvailable() ? proxyCheck.isVPN(ip) : CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> ipHubCheck = ipHub.isAvailable() ? ipHub.isVPN(ip) : CompletableFuture.completedFuture(false);

        return CompletableFuture.allOf(dnsblCheck, ipApiCheck, proxyCheckCheck, ipHubCheck).thenApply(v -> {
            boolean isVPN = false;
            String provider = "none";

            try {
                if (dnsblCheck.get()) { isVPN = true; provider = "dnsbl"; }
            } catch (Exception ignored) {}

            try {
                if (!isVPN && ipApiCheck.get()) { isVPN = true; provider = "ip-api"; }
            } catch (Exception ignored) {}

            try {
                if (!isVPN && proxyCheckCheck.get()) { isVPN = true; provider = "proxycheck"; }
            } catch (Exception ignored) {}

            try {
                if (!isVPN && ipHubCheck.get()) { isVPN = true; provider = "iphub"; }
            } catch (Exception ignored) {}

            cache.put(ip, isVPN, provider);
            if (isVPN) {
                incrementBlocked();
                plugin.getStatisticsManager().increment("vpn_blocked");
            }
            return new DetectionResult(isVPN, provider);
        });
    }

    public record DetectionResult(boolean isVPN, String provider) {}
}
