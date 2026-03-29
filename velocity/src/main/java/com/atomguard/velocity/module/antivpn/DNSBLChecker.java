package com.atomguard.velocity.module.antivpn;

import com.atomguard.velocity.util.IPUtils;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Gelişmiş DNSBL (DNS Blocklist) tabanlı IP kontrolü — v2.
 *
 * <p>Birden fazla DNSBL'i paralel olarak sorgular.
 * Varsayılan DNSBL listesi Minecraft botnet/proxy tespiti için optimize edilmiştir.
 *
 * <p>Varsayılan DNSBL'ler:
 * <ul>
 *   <li>{@code dnsbl.dronebl.org} — bot/drone tespiti (en güvenilir)</li>
 *   <li>{@code xbl.spamhaus.org} — exploit-based (hijack, trojan) listelemeler</li>
 *   <li>{@code zen.spamhaus.org} — kapsamlı kara liste</li>
 *   <li>{@code dnsbl.sorbs.net} — SOCKS proxy, HTTP proxy tespiti</li>
 *   <li>{@code socks.dnsbl.sorbs.net} — özellikle SOCKS proxy tespiti</li>
 *   <li>{@code http.dnsbl.sorbs.net} — HTTP proxy tespiti</li>
 *   <li>{@code all.s5h.net} — geniş kapsamlı kara liste</li>
 *   <li>{@code rbl.efnetrbl.org} — IRC bot/proxy tespiti</li>
 *   <li>{@code dnsbl.beetjevansen.nl} — açık proxy/SOCKS listesi</li>
 * </ul>
 *
 * <p>Sorgular paralel çalışır, 3 saniye toplam timeout.
 * En az 1 DNSBL'de listelenme → pozitif sonuç.
 */
public class DNSBLChecker {

    private static final List<String> DEFAULT_DNSBLS = List.of(
            "dnsbl.dronebl.org",
            "xbl.spamhaus.org",
            "zen.spamhaus.org",
            "dnsbl.sorbs.net",
            "socks.dnsbl.sorbs.net",
            "http.dnsbl.sorbs.net",
            "all.s5h.net",
            "rbl.efnetrbl.org",
            "dnsbl.beetjevansen.nl"
    );

    private final List<String> dnsbls;

    public DNSBLChecker(List<String> customDnsbls) {
        this.dnsbls = (customDnsbls != null && !customDnsbls.isEmpty())
                ? customDnsbls : DEFAULT_DNSBLS;
    }

    /**
     * IP'nin herhangi bir DNSBL'de listelenip listelenmediğini kontrol eder.
     * Tüm DNSBL'ler paralel sorgulanır.
     *
     * @param ip Kontrol edilecek IP adresi
     * @return true ise en az 1 DNSBL'de listelenmiş
     */
    public CompletableFuture<Boolean> isListed(String ip) {
        return checkDetailed(ip).thenApply(result -> !result.listedIn().isEmpty());
    }

    /**
     * Detaylı DNSBL kontrolü — hangi DNSBL'lerde listelendiğini döner.
     */
    public CompletableFuture<DNSBLResult> checkDetailed(String ip) {
        return CompletableFuture.supplyAsync(() -> {
            String reversed = IPUtils.reverseIP(ip);
            List<CompletableFuture<String>> futures = new ArrayList<>();

            for (String dnsbl : dnsbls) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    String lookup = reversed + "." + dnsbl;
                    try {
                        InetAddress result = InetAddress.getByName(lookup);
                        // 127.x.x.x yanıtı = listelenmiş
                        if (result.getHostAddress().startsWith("127.")) {
                            return dnsbl;
                        }
                    } catch (Exception ignored) {
                        // UnknownHostException = listelenMEMİŞ (normal durum)
                    }
                    return null;
                }));
            }

            // 3 saniye timeout
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .orTimeout(3, TimeUnit.SECONDS)
                        .exceptionally(e -> null)
                        .join();
            } catch (Exception ignored) {}

            List<String> listedIn = new ArrayList<>();
            for (CompletableFuture<String> f : futures) {
                if (f.isDone() && !f.isCompletedExceptionally()) {
                    try {
                        String result = f.join();
                        if (result != null) listedIn.add(result);
                    } catch (Exception ignored) {}
                }
            }

            return new DNSBLResult(listedIn, dnsbls.size());
        });
    }

    /**
     * Senkron DNSBL kontrolü.
     */
    public boolean isListedSync(String ip) {
        try {
            return isListed(ip).orTimeout(4, TimeUnit.SECONDS).join();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * DNSBL kontrol sonucu.
     *
     * @param listedIn     Listelendiği DNSBL'ler
     * @param totalChecked Toplam sorgulanan DNSBL sayısı
     */
    public record DNSBLResult(List<String> listedIn, int totalChecked) {
        public boolean isListed() { return !listedIn.isEmpty(); }
        public int listCount() { return listedIn.size(); }
    }
}
