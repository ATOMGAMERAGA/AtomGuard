package com.atomguard.velocity.module.antivpn;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ASN (Otonom Sistem Numarası) tabanlı engelleme.
 * Bilinen hosting/VPN sağlayıcılarının ASN'lerini engeller.
 */
public class ASNBlocker {

    // Bilinen VPN/hosting sağlayıcılarının ASN'leri
    private static final Set<String> DEFAULT_BLOCKED_ASNS = Set.of(
        "AS4134", "AS9009", "AS14061", "AS16276", "AS20473",  // DigitalOcean, OVH, Vultr, etc.
        "AS16509", "AS15169", "AS8075", "AS13335"              // AWS, Google, Microsoft, Cloudflare
    );

    private final Set<String> blockedASNs = ConcurrentHashMap.newKeySet();

    public ASNBlocker() {
        blockedASNs.addAll(DEFAULT_BLOCKED_ASNS);
    }

    public void addASN(String asn) {
        String normalized = asn.toUpperCase().startsWith("AS") ? asn.toUpperCase() : "AS" + asn;
        blockedASNs.add(normalized);
    }

    public void removeASN(String asn) {
        String normalized = asn.toUpperCase().startsWith("AS") ? asn.toUpperCase() : "AS" + asn;
        blockedASNs.remove(normalized);
    }

    public boolean isBlocked(String asn) {
        if (asn == null) return false;
        String normalized = asn.toUpperCase().startsWith("AS") ? asn.toUpperCase() : "AS" + asn;
        return blockedASNs.contains(normalized);
    }

    public int size() { return blockedASNs.size(); }
}
