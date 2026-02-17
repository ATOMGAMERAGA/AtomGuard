package com.atomguard.velocity.module.antivpn;

import com.atomguard.velocity.util.IPUtils;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * DNSBL (DNS Blocklist) tabanlı IP kontrolü.
 */
public class DNSBLChecker {

    private static final List<String> DEFAULT_DNSBLS = List.of(
        "dnsbl.sorbs.net",
        "xbl.spamhaus.org",
        "zen.spamhaus.org"
    );

    private final List<String> dnsbls;

    public DNSBLChecker(List<String> customDnsbls) {
        this.dnsbls = customDnsbls.isEmpty() ? DEFAULT_DNSBLS : customDnsbls;
    }

    public CompletableFuture<Boolean> isListed(String ip) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String reversed = IPUtils.reverseIP(ip);
                for (String dnsbl : dnsbls) {
                    String lookup = reversed + "." + dnsbl;
                    try {
                        InetAddress.getByName(lookup);
                        return true; // Listelendi
                    } catch (Exception ignored) {
                        // Bulunamadı = temiz
                    }
                }
            } catch (Exception ignored) {}
            return false;
        });
    }

    public boolean isListedSync(String ip) {
        try {
            return isListed(ip).get();
        } catch (Exception e) {
            return false;
        }
    }
}
