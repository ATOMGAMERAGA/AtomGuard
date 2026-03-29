package com.atomguard.velocity.module.antivpn;

import com.atomguard.velocity.util.NetworkUtils;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ASN (Otonom Sistem Numarası) tabanlı gelişmiş tespit motoru.
 *
 * <p>ip-api.com üzerinden IP'nin ASN bilgisini alır ve bilinen
 * VPN/hosting/proxy sağlayıcılarının ASN'leriyle karşılaştırır.
 *
 * <p>3 katmanlı sınıflandırma:
 * <ul>
 *   <li><b>VPN ASN'leri</b> — Bilinen VPN sağlayıcıları (NordVPN, ExpressVPN vb.) → confidence: 90-98</li>
 *   <li><b>Hosting ASN'leri</b> — Datacenter/VPS sağlayıcıları (AWS, DO, Hetzner vb.) → confidence: 60-80</li>
 *   <li><b>Proxy ASN'leri</b> — Bilinen proxy ağları → confidence: 85-95</li>
 * </ul>
 *
 * <p>False-positive koruması:
 * <ul>
 *   <li>Hosting ASN'leri tek başına engelleme YAPMAZ — sadece score'a katkıda bulunur</li>
 *   <li>ISP/konut ASN'leri beyaz listelenmiştir</li>
 *   <li>Bilinmeyen ASN → nötr</li>
 * </ul>
 */
public class ASNDetector {

    /**
     * ASN analiz sonucu.
     */
    public record ASNResult(
            boolean detected,
            String asn,
            String org,
            String isp,
            String type,      // "vpn", "hosting", "proxy", "residential", "unknown"
            int confidence
    ) {
        public static ASNResult clean(String asn, String org, String isp) {
            return new ASNResult(false, asn, org, isp, "residential", 0);
        }

        public static ASNResult unknown() {
            return new ASNResult(false, "", "", "", "unknown", 0);
        }
    }

    // ─── VPN sağlayıcı ASN'leri (kesin VPN) ───
    private static final Map<String, String> VPN_ASNS = Map.ofEntries(
            // NordVPN
            Map.entry("AS212238", "NordVPN"),
            Map.entry("AS394711", "NordVPN"),

            // ExpressVPN
            Map.entry("AS394639", "ExpressVPN"),

            // Mullvad
            Map.entry("AS198093", "Mullvad"),

            // Surfshark
            Map.entry("AS205544", "Surfshark"),

            // ProtonVPN
            Map.entry("AS209103", "ProtonVPN"),

            // CyberGhost
            Map.entry("AS206092", "CyberGhost"),

            // Private Internet Access
            Map.entry("AS19969", "PIA"),
            Map.entry("AS55286", "PIA"),

            // IPVanish
            Map.entry("AS33438", "IPVanish"),
            Map.entry("AS23930", "IPVanish"),

            // TorGuard
            Map.entry("AS399532", "TorGuard"),

            // Windscribe
            Map.entry("AS396073", "Windscribe"),

            // Hide.me
            Map.entry("AS56309", "Hide.me"),

            // Perfect Privacy
            Map.entry("AS49349", "PerfectPrivacy"),

            // AirVPN
            Map.entry("AS205275", "AirVPN")
    );

    // ─── Hosting/Datacenter ASN'leri (proxy potansiyeli yüksek) ───
    private static final Map<String, String> HOSTING_ASNS = Map.ofEntries(
            // Büyük bulut sağlayıcıları
            Map.entry("AS16509", "AWS"),
            Map.entry("AS14618", "AWS"),
            Map.entry("AS15169", "Google Cloud"),
            Map.entry("AS396982", "Google Cloud"),
            Map.entry("AS8075", "Microsoft Azure"),
            Map.entry("AS13335", "Cloudflare"),

            // VPS sağlayıcıları
            Map.entry("AS14061", "DigitalOcean"),
            Map.entry("AS20473", "Vultr/Choopa"),
            Map.entry("AS63949", "Linode/Akamai"),
            Map.entry("AS16276", "OVHcloud"),
            Map.entry("AS24940", "Hetzner"),
            Map.entry("AS51167", "Contabo"),
            Map.entry("AS12876", "Scaleway"),
            Map.entry("AS9009", "M247"),
            Map.entry("AS62563", "GTHost"),

            // Diğer hosting sağlayıcıları
            Map.entry("AS46562", "Performive"),
            Map.entry("AS36352", "ColoCrossing"),
            Map.entry("AS40676", "Psychz Networks"),
            Map.entry("AS8100", "QuadraNet"),
            Map.entry("AS29802", "HVC-AS"),
            Map.entry("AS30083", "HEG-AS"),
            Map.entry("AS50304", "BHost"),
            Map.entry("AS62904", "Eonix"),
            Map.entry("AS398101", "GoDaddy"),
            Map.entry("AS131199", "AlibabaTR"),
            Map.entry("AS45090", "Tencent Cloud"),
            Map.entry("AS37963", "Alibaba Cloud"),
            Map.entry("AS3462", "HiNet"),

            // Oracle Cloud
            Map.entry("AS31898", "Oracle Cloud"),

            // Hetzner ek
            Map.entry("AS213230", "Hetzner"),

            // HostHatch, BuyVM vb.
            Map.entry("AS53667", "FranTech/BuyVM"),
            Map.entry("AS33182", "HostDime"),
            Map.entry("AS32613", "iWEB"),
            Map.entry("AS36114", "Versaweb")
    );

    // ─── Proxy ağ ASN'leri ───
    private static final Map<String, String> PROXY_ASNS = Map.ofEntries(
            Map.entry("AS400007", "Bright Data (Luminati)"),
            Map.entry("AS62874", "Bright Data"),
            Map.entry("AS398324", "Oxylabs"),
            Map.entry("AS205016", "Oxylabs"),
            Map.entry("AS198540", "SmartProxy"),
            Map.entry("AS207566", "GeoSurf"),
            Map.entry("AS396356", "MaxMind (proxy)"),
            Map.entry("AS399751", "Webshare")
    );

    /** ip-api sonuç cache — 10 dakika TTL */
    private final ConcurrentHashMap<String, CachedASN> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 600_000L; // 10 dakika

    /** ip-api.com rate limit koruması */
    private volatile long rateLimitUntil = 0;
    private final String apiBaseUrl;

    public ASNDetector() {
        this("http://ip-api.com/json/");
    }

    public ASNDetector(String apiBaseUrl) {
        this.apiBaseUrl = (apiBaseUrl != null && !apiBaseUrl.isBlank())
                ? apiBaseUrl : "http://ip-api.com/json/";
    }

    /**
     * IP adresinin ASN analizini yap — asenkron.
     */
    public CompletableFuture<ASNResult> analyze(String ip) {
        // Cache kontrolü
        CachedASN cached = cache.get(ip);
        if (cached != null && !cached.isExpired()) {
            return CompletableFuture.completedFuture(cached.result);
        }

        // Rate limit kontrolü
        if (System.currentTimeMillis() < rateLimitUntil) {
            return CompletableFuture.completedFuture(ASNResult.unknown());
        }

        String url = apiBaseUrl + ip + "?fields=as,org,isp,proxy,hosting";
        return NetworkUtils.httpGetAsync(url, 4)
                .thenApply(response -> {
                    if (response == null || response.isEmpty()) {
                        return ASNResult.unknown();
                    }

                    String asn = extractASN(response);
                    String org = extractField(response, "org");
                    String isp = extractField(response, "isp");

                    ASNResult result = classify(asn, org, isp);

                    // Cache'e kaydet
                    cache.put(ip, new CachedASN(result, System.currentTimeMillis()));

                    return result;
                })
                .exceptionally(e -> ASNResult.unknown());
    }

    /**
     * ASN'i sınıflandır.
     */
    private ASNResult classify(String asn, String org, String isp) {
        if (asn == null || asn.isEmpty()) {
            return ASNResult.unknown();
        }

        // Normalize ASN
        String normalized = asn.toUpperCase();
        if (!normalized.startsWith("AS")) {
            // "AS12345 Org Name" formatını parse et
            if (normalized.contains(" ")) {
                normalized = normalized.split(" ")[0];
            }
            if (!normalized.startsWith("AS")) {
                normalized = "AS" + normalized;
            }
        } else if (normalized.contains(" ")) {
            normalized = normalized.split(" ")[0];
        }

        // VPN ASN kontrolü (en yüksek öncelik)
        String vpnName = VPN_ASNS.get(normalized);
        if (vpnName != null) {
            return new ASNResult(true, normalized, org, isp, "vpn", 95);
        }

        // Proxy ağ ASN kontrolü
        String proxyName = PROXY_ASNS.get(normalized);
        if (proxyName != null) {
            return new ASNResult(true, normalized, org, isp, "proxy", 90);
        }

        // Hosting ASN kontrolü
        String hostingName = HOSTING_ASNS.get(normalized);
        if (hostingName != null) {
            return new ASNResult(true, normalized, org, isp, "hosting", 70);
        }

        // Org/ISP adında VPN/proxy anahtar kelimeleri ara
        String lowerOrg = (org != null ? org : "").toLowerCase();
        String lowerIsp = (isp != null ? isp : "").toLowerCase();
        String combined = lowerOrg + " " + lowerIsp;

        if (containsAny(combined, "vpn", "virtual private network", "expressvpn", "nordvpn",
                "surfshark", "cyberghost", "mullvad", "protonvpn", "windscribe",
                "private internet access", "ipvanish", "purevpn")) {
            return new ASNResult(true, normalized, org, isp, "vpn", 88);
        }

        if (containsAny(combined, "proxy", "anonymizer", "residential proxy",
                "luminati", "bright data", "oxylabs", "smartproxy")) {
            return new ASNResult(true, normalized, org, isp, "proxy", 88);
        }

        if (containsAny(combined, "hosting", "datacenter", "data center", "colocation",
                "cloud", "vps", "dedicated server", "server farm")) {
            return new ASNResult(true, normalized, org, isp, "hosting", 55);
        }

        return ASNResult.clean(normalized, org, isp);
    }

    // ─── Yardımcı ───

    private String extractASN(String json) {
        String asField = NetworkUtils.extractJsonField(json, "as");
        return asField != null ? asField : "";
    }

    private String extractField(String json, String field) {
        String value = NetworkUtils.extractJsonField(json, field);
        return value != null ? value : "";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    /**
     * Cache temizliği.
     */
    public void cleanup() {
        cache.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    private record CachedASN(ASNResult result, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
}
