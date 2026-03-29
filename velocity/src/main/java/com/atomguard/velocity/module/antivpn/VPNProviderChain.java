package com.atomguard.velocity.module.antivpn;

import com.atomguard.velocity.AtomGuardVelocity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Çok katmanlı konsensüs tabanlı VPN/Proxy tespit zinciri — v2.
 *
 * <h2>Tespit Katmanları (Sıralı)</h2>
 * <ol>
 *   <li><b>Özel/loopback IP</b> → anında bypass</li>
 *   <li><b>Önbellek</b> → daha önce kontrol edilmiş IP → anında karar</li>
 *   <li><b>Yerel proxy listesi</b> → dosya tabanlı IP listesi (kesin)</li>
 *   <li><b>CIDR blocker</b> → bilinen VPN/hosting IP aralıkları (kesin)</li>
 *   <li><b>IP2Proxy veritabanı</b> → offline veritabanı (çok yüksek doğruluk)</li>
 *   <li><b>DNSBL</b> → DNS kara listeleri (güvenilir, az false positive)</li>
 *   <li><b>ASN analizi</b> → bilinen VPN/hosting ASN'leri (orta-yüksek)</li>
 *   <li><b>Reverse DNS</b> → hostname kalıp analizi (orta)</li>
 *   <li><b>Port tarama</b> → proxy portları açık mı? (yüksek, yavaş)</li>
 *   <li><b>API sağlayıcıları</b> → proxycheck.io, iphub, abuseipdb (yüksek)</li>
 * </ol>
 *
 * <h2>Konsensüs Algoritması</h2>
 * <p>Her katman bir "oy" verir. Ağırlıklı oy toplamı eşik değeri aşarsa IP engellenir.
 *
 * <h2>False-Positive Koruması</h2>
 * <ul>
 *   <li>Tek bir sağlayıcı TEK BAŞINA yeterli DEĞİL (yerel liste ve CIDR hariç)</li>
 *   <li>Residential ISP hostname'leri ve ASN'leri beyaz listelenmiş</li>
 *   <li>Hosting ASN'leri tek başına engelleme yapmaz</li>
 *   <li>Port tarama sonucu tek başına yeterli değil (en az 2 anlamlı port)</li>
 *   <li>API hataları → fail-open (geçir)</li>
 * </ul>
 *
 * <h2>Performans</h2>
 * <ul>
 *   <li>Tüm harici sorgular paralel çalışır (virtual threads)</li>
 *   <li>Toplam max timeout: 5 saniye</li>
 *   <li>Yerel kontroller (liste, CIDR, IP2Proxy) senkron ve anında</li>
 *   <li>Sonuçlar 1 saat önbelleklenir</li>
 * </ul>
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
    private final ASNBlocker legacyASNBlocker;

    // ─── Yeni Tespit Motorları ───
    private final ReverseDNSDetector reverseDNS;
    private final PortScanDetector portScanner;
    private final ASNDetector asnDetector;

    // ─── Önbellek ───
    private final VPNResultCache cachedCache;

    // ─── İstatistikler ───
    private final AtomicLong totalChecks = new AtomicLong(0);
    private final AtomicLong totalBlocked = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);

    public VPNProviderChain(AtomGuardVelocity plugin) {
        this.plugin = plugin;
        this.abuseIPDB = new AbuseIPDBProvider(plugin);
        this.ip2Proxy = new Ip2ProxyProvider(plugin);

        String proxyCheckKey = plugin.getConfigManager().getString(
                "vpn-proxy-block.api.proxycheck.api-key", "");
        String ipHubKey = plugin.getConfigManager().getString(
                "vpn-proxy-block.api.iphub.api-key", "");

        String proxyCheckUrl = plugin.getConfigManager().getString(
                "external-services.proxycheck-url", "");
        String ipHubUrl = plugin.getConfigManager().getString(
                "external-services.iphub-url", "");
        String ipApiUrl = plugin.getConfigManager().getString(
                "external-services.ip-api-url", "");

        this.proxyCheck = new ProxyCheckProvider(proxyCheckKey, proxyCheckUrl);
        this.ipHub = new IPHubProvider(ipHubKey, ipHubUrl);
        this.ipApi = new IPApiProvider(ipApiUrl);

        List<String> dnsblList = plugin.getConfigManager().getStringList("vpn-proxy-block.dnsbl-list");
        this.dnsbl = new DNSBLChecker(dnsblList != null ? dnsblList : List.of());

        this.localList = new LocalProxyListChecker(plugin.getDataDirectory(), plugin.getSlf4jLogger());
        this.cidrBlocker = new CIDRBlocker();
        this.legacyASNBlocker = new ASNBlocker();

        // Yeni motorlar
        this.reverseDNS = new ReverseDNSDetector();
        this.portScanner = new PortScanDetector();
        this.asnDetector = new ASNDetector(ipApiUrl);

        // Önbellek — 1 saat TTL, max 50k kayıt
        long cacheTtl = plugin.getConfigManager().getLong("vpn-proxy-block.cache-ttl-ms", 3_600_000L);
        int cacheMaxSize = plugin.getConfigManager().getInt("vpn-proxy-block.cache-max-size", 50000);
        this.cachedCache = new VPNResultCache(cacheTtl, cacheMaxSize);

        // Verileri yükle
        localList.load();

        List<String> cidrRanges = plugin.getConfigManager().getStringList("vpn-proxy-block.blocked-cidr");
        if (cidrRanges != null) cidrBlocker.addRanges(cidrRanges);
    }

    // ─────────────────────────── Ana Kontrol ───────────────────────────

    /**
     * Eski API uyumluluğu.
     */
    public CompletableFuture<Boolean> check(String ip) {
        return checkWithConsensus(ip).thenApply(VPNCheckResult::isVPN);
    }

    /**
     * Tam konsensüs tabanlı VPN/Proxy kontrolü.
     */
    public CompletableFuture<VPNCheckResult> checkWithConsensus(String ip) {
        totalChecks.incrementAndGet();

        // ── Katman 1: Özel/loopback IP ──
        if (isPrivateOrLoopback(ip)) {
            return CompletableFuture.completedFuture(
                    new VPNCheckResult(false, 0, List.of(), "private-ip-bypass"));
        }

        // ── Katman 2: Önbellek ──
        VPNResultCache.CacheResult cached = cachedCache.get(ip);
        if (cached != null) {
            cacheHits.incrementAndGet();
            if (cached.isVPN()) totalBlocked.incrementAndGet();
            return CompletableFuture.completedFuture(
                    new VPNCheckResult(cached.isVPN(), cached.isVPN() ? 100 : 0,
                            List.of(cached.provider()), "cache"));
        }

        // ── Katman 3: Yerel proxy listesi (kesin, senkron) ──
        if (localList.isProxy(ip)) {
            VPNCheckResult result = new VPNCheckResult(true, 100, List.of("local-list"), "local-list");
            cachedCache.put(ip, true, "local-list");
            totalBlocked.incrementAndGet();
            return CompletableFuture.completedFuture(result);
        }

        // ── Katman 4: CIDR blocker (kesin, senkron) ──
        if (cidrBlocker.isBlocked(ip)) {
            VPNCheckResult result = new VPNCheckResult(true, 100, List.of("cidr"), "cidr");
            cachedCache.put(ip, true, "cidr");
            totalBlocked.incrementAndGet();
            return CompletableFuture.completedFuture(result);
        }

        // ── Katman 5: IP2Proxy veritabanı (kesin, senkron) ──
        if (ip2Proxy.isProxy(ip)) {
            VPNCheckResult result = new VPNCheckResult(true, 98, List.of("ip2proxy"), "ip2proxy-db");
            cachedCache.put(ip, true, "ip2proxy");
            totalBlocked.incrementAndGet();
            return CompletableFuture.completedFuture(result);
        }

        // ── Katman 6+: Paralel asenkron kontroller ──
        return runMultiLayerChecks(ip);
    }

    /**
     * Tüm asenkron kontrolleri paralel olarak çalıştır ve konsensüs kararı ver.
     */
    private CompletableFuture<VPNCheckResult> runMultiLayerChecks(String ip) {
        int consensusThreshold = plugin.getConfigManager().getInt(
                "vpn-proxy-block.consensus-threshold", 2);
        int confidenceThreshold = plugin.getConfigManager().getInt(
                "vpn-proxy-block.trust-score-threshold", 55);
        boolean portScanEnabled = plugin.getConfigManager().getBoolean(
                "vpn-proxy-block.port-scan.enabled", true);
        boolean reverseDNSEnabled = plugin.getConfigManager().getBoolean(
                "vpn-proxy-block.reverse-dns.enabled", true);
        boolean asnEnabled = plugin.getConfigManager().getBoolean(
                "vpn-proxy-block.asn-detection.enabled", true);

        List<CompletableFuture<ProviderVote>> futures = new ArrayList<>();

        // ── DNSBL (ağırlık: 0.80) ──
        futures.add(dnsbl.isListed(ip)
                .thenApply(v -> new ProviderVote("dnsbl", v, 0.80, false, v ? 80 : 0)));

        // ── ASN Analizi (ağırlık: 0.75) ──
        if (asnEnabled) {
            futures.add(asnDetector.analyze(ip).thenApply(asn -> {
                if (!asn.detected()) return ProviderVote.clean("asn-detector");
                return switch (asn.type()) {
                    case "vpn" -> new ProviderVote("asn:" + asn.asn(), true, 0.90, false, asn.confidence());
                    case "proxy" -> new ProviderVote("asn:" + asn.asn(), true, 0.85, false, asn.confidence());
                    case "hosting" -> new ProviderVote("asn:" + asn.asn(), true, 0.50, true, asn.confidence());
                    default -> ProviderVote.clean("asn-detector");
                };
            }));
        }

        // ── Reverse DNS (ağırlık: 0.70) ──
        if (reverseDNSEnabled) {
            futures.add(reverseDNS.analyze(ip).thenApply(dns -> {
                if (!dns.detected()) return ProviderVote.clean("rdns");
                return switch (dns.detectionType()) {
                    case "tor" -> new ProviderVote("rdns:" + dns.matchedPattern(), true, 0.95, false, dns.confidence());
                    case "vpn" -> new ProviderVote("rdns:" + dns.matchedPattern(), true, 0.85, false, dns.confidence());
                    case "proxy" -> new ProviderVote("rdns:" + dns.matchedPattern(), true, 0.80, false, dns.confidence());
                    case "hosting" -> new ProviderVote("rdns:" + dns.matchedPattern(), true, 0.45, true, dns.confidence());
                    default -> ProviderVote.clean("rdns");
                };
            }));
        }

        // ── Port Tarama (ağırlık: 0.85) ──
        if (portScanEnabled) {
            futures.add(portScanner.scan(ip).thenApply(scan -> {
                if (!scan.suspicious()) return ProviderVote.clean("port-scan");
                return new ProviderVote("port-scan:" + scan.openPorts(), true, 0.85, false, scan.confidence());
            }));
        }

        // ── API Sağlayıcıları ──

        // proxycheck.io (ağırlık: 0.90)
        if (proxyCheck.isAvailable()) {
            futures.add(proxyCheck.isVPN(ip)
                    .thenApply(v -> new ProviderVote("proxycheck", v, 0.90, false, v ? 90 : 0)));
        }

        // iphub (ağırlık: 0.85)
        if (ipHub.isAvailable()) {
            futures.add(ipHub.isVPN(ip)
                    .thenApply(v -> new ProviderVote("iphub", v, 0.85, false, v ? 85 : 0)));
        }

        // abuseipdb (ağırlık: 0.75)
        if (plugin.getConfigManager().getBoolean("vpn-proxy-block.abuseipdb.enabled", false)) {
            int threshold = plugin.getConfigManager().getInt(
                    "vpn-proxy-block.abuseipdb.trust-threshold", 50);
            futures.add(abuseIPDB.check(ip)
                    .thenApply(score -> new ProviderVote("abuseipdb", score >= threshold, 0.75,
                            false, score)));
        }

        // ip-api (ağırlık: 0.60 — ücretsiz, daha az güvenilir)
        if (ipApi.isAvailable()) {
            futures.add(ipApi.checkDetailed(ip).thenApply(detail -> {
                if (detail.isProxy()) {
                    return new ProviderVote("ip-api", true, 0.65, false, 75);
                }
                if (detail.isHostingOnly()) {
                    return new ProviderVote("ip-api", true, 0.35, true, 40);
                }
                return ProviderVote.clean("ip-api");
            }));
        }

        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new VPNCheckResult(false, 0, List.of(), "no-providers"));
        }

        // ── Tüm sonuçları 5 saniye timeout ile bekle ──
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));

        return allOf.orTimeout(5, TimeUnit.SECONDS)
                .exceptionally(e -> null)
                .thenApply(ignored -> {
                    List<ProviderVote> votes = new ArrayList<>();
                    for (CompletableFuture<ProviderVote> f : futures) {
                        if (f.isDone() && !f.isCompletedExceptionally()) {
                            try {
                                ProviderVote vote = f.join();
                                if (vote != null) votes.add(vote);
                            } catch (Exception ignored2) {}
                        }
                    }
                    return buildDecision(votes, consensusThreshold, confidenceThreshold, ip);
                });
    }

    // ─────────────────────────── Konsensüs Kararı ───────────────────────────

    private VPNCheckResult buildDecision(List<ProviderVote> votes, int consensusThreshold,
                                          int confidenceThreshold, String ip) {
        if (votes.isEmpty()) {
            // Fail-open: hiçbir sağlayıcı cevap vermediyse geçir
            VPNCheckResult result = new VPNCheckResult(false, 0, List.of(), "no-response-failopen");
            cachedCache.put(ip, false, "no-response");
            return result;
        }

        int positiveVotes = 0;
        int strongPositiveVotes = 0;  // hosting-only DEĞİL olan pozitif oylar
        List<String> detectedBy = new ArrayList<>();
        double weightedScoreSum = 0.0;
        double totalWeight = 0.0;
        boolean onlyHostingFlags = true;
        int maxIndividualConfidence = 0;
        boolean hasTorDetection = false;

        for (ProviderVote vote : votes) {
            totalWeight += vote.weight;
            if (vote.isVPN) {
                positiveVotes++;
                detectedBy.add(vote.provider);
                weightedScoreSum += vote.weight * vote.individualConfidence;

                if (!vote.hostingOnly) {
                    onlyHostingFlags = false;
                    strongPositiveVotes++;
                }

                maxIndividualConfidence = Math.max(maxIndividualConfidence, vote.individualConfidence);

                if (vote.provider.startsWith("rdns:") && vote.provider.contains("tor")) {
                    hasTorDetection = true;
                }
            }
        }

        // Toplam confidence score hesapla
        int confidenceScore = totalWeight > 0
                ? (int) Math.min(100, weightedScoreSum / totalWeight)
                : 0;

        // ── Karar Mantığı ──

        boolean isVPN;
        String method;

        // Kural 0: Hiçbir pozitif oy yok → temiz
        if (positiveVotes == 0) {
            isVPN = false;
            method = "consensus-clean";
        }
        // Kural 1: Tor tespiti → kesin engel
        else if (hasTorDetection) {
            isVPN = true;
            method = "tor-detection";
            confidenceScore = Math.max(confidenceScore, 98);
        }
        // Kural 2: 3+ güçlü (non-hosting) pozitif oy → kesin engel
        else if (strongPositiveVotes >= 3) {
            isVPN = true;
            method = "strong-consensus";
            confidenceScore = Math.max(confidenceScore, 90);
        }
        // Kural 3: 2+ güçlü pozitif oy VE yeterli confidence → engel
        else if (strongPositiveVotes >= 2 && confidenceScore >= confidenceThreshold) {
            isVPN = true;
            method = "consensus-block";
        }
        // Kural 4: Tek güçlü oy ama çok yüksek confidence (90+)
        else if (strongPositiveVotes == 1 && maxIndividualConfidence >= 90 && confidenceScore >= 70) {
            isVPN = true;
            method = "single-provider-high-confidence";
        }
        // Kural 5: Sadece hosting flagleri — engelleme YAPMA (false positive riski)
        else if (onlyHostingFlags && positiveVotes > 0) {
            isVPN = false;
            method = "hosting-only-bypass";
        }
        // Kural 6: Konsensüs eşiğini karşılıyor
        else if (positiveVotes >= consensusThreshold && confidenceScore >= confidenceThreshold) {
            isVPN = true;
            method = "consensus-block";
        }
        // Kural 7: Yeterli konsensüs yok → geçir
        else {
            isVPN = false;
            method = "insufficient-consensus";
        }

        VPNCheckResult result = new VPNCheckResult(isVPN, confidenceScore, detectedBy, method);
        cachedCache.put(ip, isVPN, String.join(",", detectedBy));

        if (isVPN) totalBlocked.incrementAndGet();

        return result;
    }

    // ─────────────────────────── Senkron / Yardımcı ───────────────────────────

    /**
     * Senkron VPN kontrolü — sadece cache, yerel liste ve CIDR kontrolü.
     */
    public boolean isVPN(String ip) {
        if (isPrivateOrLoopback(ip)) return false;

        VPNResultCache.CacheResult cached = cachedCache.get(ip);
        if (cached != null) return cached.isVPN();

        if (localList.isProxy(ip)) return true;
        if (cidrBlocker.isBlocked(ip)) return true;
        if (ip2Proxy.isProxy(ip)) return true;

        return false;
    }

    private boolean isPrivateOrLoopback(String ip) {
        return ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.")
                || ip.startsWith("172.16.") || ip.startsWith("172.17.") || ip.startsWith("172.18.")
                || ip.startsWith("172.19.") || ip.startsWith("172.2") || ip.startsWith("172.30.")
                || ip.startsWith("172.31.") || ip.equals("::1")
                || ip.startsWith("fc") || ip.startsWith("fd")
                || ip.startsWith("fe80:") || ip.equals("0.0.0.0");
    }

    public void close() {
        ip2Proxy.close();
        portScanner.shutdown();
    }

    public void cleanup() {
        cachedCache.cleanup();
        asnDetector.cleanup();
    }

    public VPNResultCache getCache() { return cachedCache; }
    public LocalProxyListChecker getLocalList() { return localList; }
    public CIDRBlocker getCIDRBlocker() { return cidrBlocker; }
    public long getTotalChecks() { return totalChecks.get(); }
    public long getTotalBlocked() { return totalBlocked.get(); }
    public long getCacheHits() { return cacheHits.get(); }

    // ─────────────────────────── Vote Model ───────────────────────────

    /**
     * Tek bir sağlayıcıdan gelen oy.
     */
    record ProviderVote(
            String provider,
            boolean isVPN,
            double weight,
            boolean hostingOnly,
            int individualConfidence
    ) {
        static ProviderVote clean(String provider) {
            return new ProviderVote(provider, false, 0, false, 0);
        }
    }
}
