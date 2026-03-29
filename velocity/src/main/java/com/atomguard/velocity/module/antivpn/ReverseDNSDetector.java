package com.atomguard.velocity.module.antivpn;

import com.atomguard.velocity.util.NetworkUtils;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Reverse DNS tabanlı VPN/Proxy/Hosting/Tor tespit motoru.
 *
 * <p>IP adresinin hostname'ini çözümler ve bilinen VPN/hosting/proxy
 * sağlayıcılarının hostname kalıplarıyla karşılaştırır.
 *
 * <p>Örnek tespitler:
 * <ul>
 *   <li>{@code *.vultr.com} → Vultr VPS (hosting)</li>
 *   <li>{@code *.nordvpn.com} → NordVPN (VPN)</li>
 *   <li>{@code *.tor-exit-*} → Tor çıkış noktası</li>
 *   <li>{@code *.proxy.*} → Genel proxy sunucusu</li>
 * </ul>
 *
 * <p>False-positive koruması:
 * <ul>
 *   <li>Yalnızca hostname eşleşmesi yeterli DEĞİL — confidence score olarak eklenir</li>
 *   <li>ISP/konut hostnameleri (*.comcast.net, *.telekom.de vb.) beyaz listelenmiştir</li>
 *   <li>Çözümlenemeyen hostname → nötr (ne pozitif ne negatif)</li>
 * </ul>
 */
public class ReverseDNSDetector {

    /**
     * Tespit sonucu.
     *
     * @param detected       hostname kalıbı eşleşti mi?
     * @param hostname       çözümlenen hostname
     * @param matchedPattern eşleşen kalıp
     * @param detectionType  tespit türü (vpn, hosting, proxy, tor)
     * @param confidence     güven skoru 0-100
     */
    public record DNSResult(
            boolean detected,
            String hostname,
            String matchedPattern,
            String detectionType,
            int confidence
    ) {
        public static DNSResult clean(String hostname) {
            return new DNSResult(false, hostname, "", "clean", 0);
        }

        public static DNSResult unresolved() {
            return new DNSResult(false, "", "", "unresolved", 0);
        }
    }

    // ─── Bilinen VPN sağlayıcı hostname kalıpları ───
    private static final List<HostnamePattern> VPN_PATTERNS = List.of(
            // Ticari VPN sağlayıcıları
            hp("nordvpn", "vpn", 95),
            hp("expressvpn", "vpn", 95),
            hp("surfshark", "vpn", 95),
            hp("cyberghost", "vpn", 95),
            hp("privateinternetaccess", "vpn", 90),
            hp("pia-", "vpn", 85),
            hp("mullvad", "vpn", 95),
            hp("protonvpn", "vpn", 95),
            hp("windscribe", "vpn", 90),
            hp("hide.me", "vpn", 90),
            hp("ipvanish", "vpn", 90),
            hp("purevpn", "vpn", 90),
            hp("tunnelbear", "vpn", 85),
            hp("hotspotshield", "vpn", 85),
            hp("astrill", "vpn", 90),
            hp("vypr", "vpn", 85),
            hp("strongvpn", "vpn", 90),
            hp("privatevpn", "vpn", 90),
            hp("zenmate", "vpn", 85),
            hp("torguard", "vpn", 90),
            hp("warp.cloudflare", "vpn", 70), // Cloudflare WARP — düşük confidence

            // Genel VPN anahtar kelimeleri
            hp(".vpn.", "vpn", 80),
            hp("-vpn-", "vpn", 80),
            hp("-vpn.", "vpn", 75),
            hp(".vpn-", "vpn", 75)
    );

    // ─── Hosting/VPS sağlayıcı hostname kalıpları ───
    private static final List<HostnamePattern> HOSTING_PATTERNS = List.of(
            // Büyük bulut sağlayıcıları
            hp(".amazonaws.com", "hosting", 85),
            hp(".compute.amazonaws", "hosting", 90),
            hp("ec2-", "hosting", 90),
            hp(".googleusercontent.com", "hosting", 85),
            hp(".cloud.google.com", "hosting", 85),
            hp(".azure.", "hosting", 80),
            hp(".cloudapp.azure.com", "hosting", 85),

            // VPS sağlayıcıları
            hp(".vultr.com", "hosting", 90),
            hp(".linode.", "hosting", 85),
            hp(".digitalocean.com", "hosting", 90),
            hp(".do.droplet", "hosting", 90),
            hp(".ovh.", "hosting", 85),
            hp(".hetzner.", "hosting", 90),
            hp(".contabo.", "hosting", 85),
            hp(".scaleway.", "hosting", 80),
            hp(".upcloud.", "hosting", 80),
            hp(".kamatera.", "hosting", 80),
            hp(".ionos.", "hosting", 75),
            hp(".hostinger.", "hosting", 75),
            hp(".vps.", "hosting", 70),
            hp(".dedicated.", "hosting", 70),
            hp(".server.", "hosting", 50), // Düşük confidence — ISP'ler de kullanır
            hp(".colo.", "hosting", 70),
            hp(".datacenter.", "hosting", 75),
            hp(".rack.", "hosting", 65),

            // Ek hosting sağlayıcıları
            hp(".choopa.net", "hosting", 85),
            hp(".colocrossing.", "hosting", 80),
            hp(".psychz.", "hosting", 80),
            hp(".quadranet.", "hosting", 80),
            hp(".m247.", "hosting", 80),
            hp(".servermania.", "hosting", 75),
            hp(".ramnode.", "hosting", 80),
            hp(".buyvm.", "hosting", 80)
    );

    // ─── Proxy / SOCKS hostname kalıpları ───
    private static final List<HostnamePattern> PROXY_PATTERNS = List.of(
            hp(".proxy.", "proxy", 85),
            hp("-proxy.", "proxy", 85),
            hp("-proxy-", "proxy", 85),
            hp(".socks.", "proxy", 90),
            hp("-socks-", "proxy", 90),
            hp(".squid.", "proxy", 80),
            hp("proxy-", "proxy", 75),
            hp("socks5-", "proxy", 85),
            hp("socks4-", "proxy", 85),
            hp("http-proxy", "proxy", 85),
            hp("anon-proxy", "proxy", 95),
            hp("anonymizer", "proxy", 90),
            hp("anonymiser", "proxy", 90)
    );

    // ─── Tor çıkış noktaları ───
    private static final List<HostnamePattern> TOR_PATTERNS = List.of(
            hp("tor-exit", "tor", 98),
            hp(".tor.", "tor", 85),
            hp(".torproject.", "tor", 95),
            hp("exit-node", "tor", 95),
            hp("exitrelay", "tor", 95),
            hp("tor-relay", "tor", 90),
            hp(".onion.", "tor", 90)
    );

    // ─── Residential ISP beyaz listesi (false positive önleme) ───
    private static final Set<String> RESIDENTIAL_SUFFIXES = Set.of(
            ".comcast.net", ".comcastbusiness.net",
            ".verizon.net", ".fios.verizon.net",
            ".att.net", ".sbcglobal.net",
            ".charter.com", ".spectrum.com",
            ".cox.net", ".coxinet.net",
            ".rr.com", ".roadrunner.com",
            ".centurylink.net", ".centurytel.net",
            ".frontier.com", ".frontiernet.net",
            ".windstream.net",
            ".optimum.net", ".cablevision.com",
            ".btinternet.com", ".bt.com",
            ".virginmedia.com", ".virgin.net",
            ".sky.com", ".talktalk.net",
            ".plus.net", ".ee.co.uk",
            ".telekom.de", ".t-online.de", ".t-ipconnect.de",
            ".vodafone.de", ".unitymediagroup.de",
            ".orange.fr", ".wanadoo.fr",
            ".free.fr", ".sfr.net",
            ".telia.com", ".telenor.com",
            ".turkcell.com.tr", ".ttnet.com.tr",
            ".turktelekom.com.tr", ".superonline.net",
            ".kablonet.com.tr",
            ".ntt.net", ".ocn.ne.jp",
            ".kpn.net", ".ziggo.nl",
            ".swisscom.com", ".bluewin.ch",
            ".shaw.ca", ".rogers.com", ".bell.ca",
            ".telstra.com.au", ".optusnet.com.au",
            ".tpg.com.au"
    );

    /**
     * IP adresinin reverse DNS analizi — asenkron.
     */
    public CompletableFuture<DNSResult> analyze(String ip) {
        return NetworkUtils.reverseDNS(ip).thenApply(hostname -> {
            if (hostname == null || hostname.isEmpty() || hostname.equals(ip)
                    || "Bulunamadı".equals(hostname)) {
                return DNSResult.unresolved();
            }

            String lower = hostname.toLowerCase();

            // Beyaz liste kontrolü — residential ISP ise temiz
            for (String suffix : RESIDENTIAL_SUFFIXES) {
                if (lower.endsWith(suffix)) {
                    return DNSResult.clean(hostname);
                }
            }

            // Tor kontrolü (en yüksek öncelik)
            for (HostnamePattern p : TOR_PATTERNS) {
                if (lower.contains(p.keyword)) {
                    return new DNSResult(true, hostname, p.keyword, p.type, p.confidence);
                }
            }

            // VPN kontrolü
            for (HostnamePattern p : VPN_PATTERNS) {
                if (lower.contains(p.keyword)) {
                    return new DNSResult(true, hostname, p.keyword, p.type, p.confidence);
                }
            }

            // Proxy kontrolü
            for (HostnamePattern p : PROXY_PATTERNS) {
                if (lower.contains(p.keyword)) {
                    return new DNSResult(true, hostname, p.keyword, p.type, p.confidence);
                }
            }

            // Hosting kontrolü
            for (HostnamePattern p : HOSTING_PATTERNS) {
                if (lower.contains(p.keyword)) {
                    return new DNSResult(true, hostname, p.keyword, p.type, p.confidence);
                }
            }

            return DNSResult.clean(hostname);
        }).exceptionally(e -> DNSResult.unresolved());
    }

    // ─── Yardımcı ───

    private static HostnamePattern hp(String keyword, String type, int confidence) {
        return new HostnamePattern(keyword.toLowerCase(), type, confidence);
    }

    private record HostnamePattern(String keyword, String type, int confidence) {}
}
