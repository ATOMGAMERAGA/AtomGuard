package com.atomguard.velocity.module.antivpn;

import com.atomguard.velocity.AtomGuardVelocity;

import java.util.concurrent.CompletableFuture;

public class VPNProviderChain {

    private final AtomGuardVelocity plugin;
    private final AbuseIPDBProvider abuseIPDB;
    private final Ip2ProxyProvider ip2Proxy;

    public VPNProviderChain(AtomGuardVelocity plugin) {
        this.plugin = plugin;
        this.abuseIPDB = new AbuseIPDBProvider(plugin);
        this.ip2Proxy = new Ip2ProxyProvider(plugin);
    }

    public CompletableFuture<Boolean> check(String ip) {
        // 1. IP2Proxy (Local/Fast)
        if (plugin.getConfigManager().getBoolean("vpn-proxy-engelleme.ip2proxy.aktif", false)) {
            if (ip2Proxy.isProxy(ip)) {
                return CompletableFuture.completedFuture(true);
            }
        }

        // 2. AbuseIPDB (Remote/Slow)
        if (plugin.getConfigManager().getBoolean("vpn-proxy-engelleme.abuseipdb.aktif", false)) {
            return abuseIPDB.check(ip).thenApply(score -> {
                int threshold = plugin.getConfigManager().getInt("vpn-proxy-engelleme.abuseipdb.guven-esigi", 50);
                return score >= threshold;
            });
        }

        return CompletableFuture.completedFuture(false);
    }
    
    public void close() {
        ip2Proxy.close();
    }
}