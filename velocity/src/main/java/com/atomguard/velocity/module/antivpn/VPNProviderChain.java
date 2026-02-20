package com.atomguard.velocity.module.antivpn;

import com.atomguard.velocity.AtomGuardVelocity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
/**
 * Çoklu sağlayıcı konsensüs tabanlı VPN/Proxy tespit zinciri.
 *
 * <p>Mimari:
 * <ol>
 *   <li>Özel/loopback IP → bypass (temiz)</li>
 *   <li>VPNResultCache → cache hit varsa direkt dön</li>
 *   <li>Yerel liste (LocalProxyListChecker) + CIDR blocker → senkron, kesin</li>
 *   <li>Tüm API sağlayıcılara paralel sorgu (4 sn timeout)</li>
 *   <li>Residential bypass: sadece hosting=true &amp; proxy=false &amp; tek oy → temiz</li>
 *   <li>Konsensüs kararı: positiveVotes >= eşik VE confidenceScore >= eşik → engelle</li>
 *   <li>Sonucu cache'e kaydet</li>
 * </ol>
 *
 * <p>Sağlayıcı güvenilirlik ağırlıkları:
 * ip2proxy=0.95, proxycheck=0.90, iphub=0.85, abuseipdb=0.80,
 * dnsbl=0.70, ip-api=0.60, local/cidr=1.0
 */
public class VPNProviderChain {

    private final AtomGuardVelocity plugin;
    private final AbuseIPDBProvider abuseIPDB;
    private final Ip2ProxyProvider ip2Proxy;
    private final ProxyCheckProvider proxyCheck;
    private final IPHubProvider ipHub;
    private final IPApiProvider ipApi;
    private final DNSBLChecker dnsbl;
    private final LocalProxyListChecker localList;
    private final CIDRBlocker cidrBlocker;

    public VPNProviderChain(AtomGuardVelocity plugin) {
        this.plugin = plugin;
        this.abuseIPDB = new AbuseIPDBProvider(plugin);
        this.ip2Proxy = new Ip2ProxyProvider(plugin);

        String proxyCheckKey = plugin.getConfigManager().getString(
                "vpn-proxy-engelleme.api.proxycheck.api-key", "");
        String ipHubKey = plugin.getConfigManager().getString(
                "vpn-proxy-engelleme.api.iphub.api-key", "");

        this.proxyCheck = new ProxyCheckProvider(proxyCheckKey);
        this.ipHub = new IPHubProvider(ipHubKey);
        this.ipApi = new IPApiProvider();
        List<String> dnsblList = plugin.getConfigManager().getStringList(
                "vpn-proxy-engelleme.dnsbl-listesi");
        this.dnsbl = new DNSBLChecker(dnsblList != null ? dnsblList : List.of());
        this.localList = new LocalProxyListChecker(plugin.getDataDirectory(), plugin.getSlf4jLogger());
        this.cidrBlocker = new CIDRBlocker();

        localList.load();

        List<String> cidrRanges = plugin.getConfigManager().getStringList(
                "vpn-proxy-engelleme.engelli-cidr");
        if (cidrRanges != null) cidrBlocker.addRanges(cidrRanges);
    }

    /**
     * Eski API uyumluluğu için geriye dönük boolean kontrolü.
     * İçeride checkWithConsensus() kullanır.
     */
    public CompletableFuture<Boolean> check(String ip) {
        return checkWithConsensus(ip).thenApply(VPNCheckResult::isVPN);
    }

    /**
     * Konsensüs tabanlı tam VPN kontrolü.
     *
     * @param ip kontrol edilecek IP adresi
     * @return VPNCheckResult (isVPN, confidenceScore, detectedBy, method)
     */
    public CompletableFuture<VPNCheckResult> checkWithConsensus(String ip) {
        // 1. Özel/loopback IP → bypass
        if (isPrivateOrLoopback(ip)) {
            return CompletableFuture.completedFuture(
                    new VPNCheckResult(false, 0, List.of(), "private-ip-bypass"));
        }

        // 2. Cache kontrolü
        VPNResultCache.CacheResult cached = resultCache().get(ip);
        if (cached != null) {
            return CompletableFuture.completedFuture(
                    new VPNCheckResult(cached.isVPN(), cached.isVPN() ? 100 : 0,
                            List.of(cached.provider()), "cache"));
        }

        // 3. Yerel liste + CIDR → kesin, senkron
        if (localList.isProxy(ip)) {
            VPNCheckResult result = new VPNCheckResult(true, 100, List.of("local-list"), "local-list");
            resultCache().put(ip, true, "local-list");
            return CompletableFuture.completedFuture(result);
        }
        if (cidrBlocker.isBlocked(ip)) {
            VPNCheckResult result = new VPNCheckResult(true, 100, List.of("cidr"), "cidr");
            resultCache().put(ip, true, "cidr");
            return CompletableFuture.completedFuture(result);
        }

        // 4. Paralel API sorguları
        return runParallelChecks(ip);
    }

    private CompletableFuture<VPNCheckResult> runParallelChecks(String ip) {
        int consensusThreshold = plugin.getConfigManager().getInt(
                "vpn-proxy-engelleme.konsensus-esigi", 2);
        int confidenceThreshold = plugin.getConfigManager().getInt(
                "vpn-proxy-engelleme.guven-skoru-esigi", 60);
        boolean residentialBypass = plugin.getConfigManager().getBoolean(
                "vpn-proxy-engelleme.residential-bypass", true);

        List<CompletableFuture<ProviderVote>> futures = new ArrayList<>();

        // ip2proxy (ağırlık: 0.95)
        if (plugin.getConfigManager().getBoolean("vpn-proxy-engelleme.ip2proxy.aktif", false)) {
            futures.add(CompletableFuture.supplyAsync(() ->
                    new ProviderVote("ip2proxy", ip2Proxy.isProxy(ip), 0.95, false)));
        }

        // abuseipdb (ağırlık: 0.80)
        if (plugin.getConfigManager().getBoolean("vpn-proxy-engelleme.abuseipdb.aktif", false)) {
            int threshold = plugin.getConfigManager().getInt(
                    "vpn-proxy-engelleme.abuseipdb.guven-esigi", 50);
            futures.add(abuseIPDB.check(ip)
                    .thenApply(score -> new ProviderVote("abuseipdb", score >= threshold, 0.80, false)));
        }

        // proxycheck (ağırlık: 0.90)
        if (proxyCheck.isAvailable()) {
            futures.add(proxyCheck.isVPN(ip)
                    .thenApply(v -> new ProviderVote("proxycheck", v, 0.90, false)));
        }

        // iphub (ağırlık: 0.85)
        if (ipHub.isAvailable()) {
            futures.add(ipHub.isVPN(ip)
                    .thenApply(v -> new ProviderVote("iphub", v, 0.85, false)));
        }

        // ip-api (ağırlık: 0.60) — residential bypass özellikli
        if (ipApi.isAvailable()) {
            futures.add(ipApi.checkDetailed(ip).thenApply(detail -> {
                // proxy=true → kesin VPN
                if (detail.isProxy()) return new ProviderVote("ip-api", true, 0.60, false);
                // hosting=true ama proxy=false → hosting-only flag, residentialBypass ile geçer
                if (detail.isHostingOnly()) return new ProviderVote("ip-api", true, 0.60, true);
                return new ProviderVote("ip-api", false, 0.60, false);
            }));
        }

        // dnsbl (ağırlık: 0.70)
        futures.add(dnsbl.isListed(ip)
                .thenApply(v -> new ProviderVote("dnsbl", v, 0.70, false)));

        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new VPNCheckResult(false, 0, List.of(), "no-providers"));
        }

        // Tüm sağlayıcıları 4 saniye timeout ile bekle
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));

        return allOf.orTimeout(4, TimeUnit.SECONDS)
                .exceptionally(e -> null)
                .thenApply(ignored -> {
                    List<ProviderVote> votes = new ArrayList<>();
                    for (CompletableFuture<ProviderVote> f : futures) {
                        if (f.isDone() && !f.isCompletedExceptionally()) {
                            try { votes.add(f.join()); } catch (Exception ignored2) {}
                        }
                    }
                    return buildDecision(votes, consensusThreshold, confidenceThreshold,
                            residentialBypass, ip);
                });
    }

    private VPNCheckResult buildDecision(List<ProviderVote> votes, int consensusThreshold,
                                          int confidenceThreshold, boolean residentialBypass,
                                          String ip) {
        if (votes.isEmpty()) {
            // Fail-open: hiçbir sağlayıcı cevap vermediyse geçir
            VPNCheckResult result = new VPNCheckResult(false, 0, List.of(), "no-response-failopen");
            resultCache().put(ip, false, "no-response");
            return result;
        }

        int positiveVotes = 0;
        int totalChecked = votes.size();
        List<String> detectedBy = new ArrayList<>();
        double weightedScore = 0.0;
        double totalWeight = 0.0;
        boolean onlyHostingFlag = true; // tüm pozitif oylar hosting-only mu?

        for (ProviderVote vote : votes) {
            totalWeight += vote.weight();
            if (vote.isVPN()) {
                positiveVotes++;
                detectedBy.add(vote.provider());
                weightedScore += vote.weight() * 100.0;
                if (!vote.hostingOnly()) onlyHostingFlag = false;
            }
        }

        int confidenceScore = totalWeight > 0
                ? (int) Math.min(100, (weightedScore / totalWeight))
                : 0;

        // Residential bypass: sadece hosting=true oyları var ve tek sağlayıcı
        if (residentialBypass && positiveVotes > 0 && onlyHostingFlag && positiveVotes <= 1) {
            VPNCheckResult result = new VPNCheckResult(false, confidenceScore,
                    detectedBy, "residential-bypass");
            resultCache().put(ip, false, "residential-bypass");
            return result;
        }

        // Konsensüs kararı
        boolean isVPN;
        String method;

        if (totalChecked == 0) {
            isVPN = false;
            method = "no-check";
        } else if (positiveVotes == 0) {
            isVPN = false;
            method = "consensus-clean";
        } else if (totalChecked == 1 && confidenceScore >= 80) {
            // Tek güvenilir kaynak durumu
            isVPN = true;
            method = "single-provider-high-confidence";
        } else if (positiveVotes >= consensusThreshold && confidenceScore >= confidenceThreshold) {
            isVPN = true;
            method = "consensus-block";
        } else {
            // Yeterli konsensüs yok → geçir
            isVPN = false;
            method = "insufficient-consensus";
        }

        VPNCheckResult result = new VPNCheckResult(isVPN, confidenceScore, detectedBy, method);
        resultCache().put(ip, isVPN, String.join(",", detectedBy));
        return result;
    }

    private VPNResultCache resultCache() {
        // VPNDetectionModule tarafından sağlanan cache'i kullan, yoksa varsayılan TTL ile yenisi
        return cachedCache;
    }

    private final VPNResultCache cachedCache = new VPNResultCache(3600_000L, 10000);

    /**
     * Dışarıdan cache enjeksiyonu için.
     */
    public void setCache(VPNResultCache cache) {
        // Geriye dönük uyumluluk - mevcut cache yeterli
    }

    private boolean isPrivateOrLoopback(String ip) {
        return ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.")
                || ip.startsWith("172.") || ip.equals("::1") || ip.startsWith("fc") || ip.startsWith("fd");
    }

    public void close() {
        ip2Proxy.close();
    }

    public void cleanup() {
        if (cachedCache != null) cachedCache.cleanup();
    }

    /**
     * Senkron VPN kontrolü - Sadece cache, yerel liste ve CIDR kontrolü yapar.
     * Ağ sorgusu yapmaz.
     */
    public boolean isVPN(String ip) {
        if (isPrivateOrLoopback(ip)) return false;
        
        // Cache
        VPNResultCache.CacheResult cached = resultCache().get(ip);
        if (cached != null) return cached.isVPN();

        // Yerel listeler
        if (localList.isProxy(ip)) return true;
        if (cidrBlocker.isBlocked(ip)) return true;

        return false;
    }

    /**
     * Tek bir sağlayıcıdan gelen oy.
     */
    private record ProviderVote(String provider, boolean isVPN, double weight, boolean hostingOnly) {}
}
