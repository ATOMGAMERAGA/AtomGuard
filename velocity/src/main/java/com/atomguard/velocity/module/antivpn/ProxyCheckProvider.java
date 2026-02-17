package com.atomguard.velocity.module.antivpn;

import com.atomguard.velocity.util.NetworkUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * proxycheck.io API entegrasyonu.
 */
public class ProxyCheckProvider {

    private final String apiKey;
    private final AtomicInteger failures = new AtomicInteger(0);
    private volatile long circuitOpenUntil = 0;

    public ProxyCheckProvider(String apiKey) {
        this.apiKey = apiKey;
    }

    public CompletableFuture<Boolean> isVPN(String ip) {
        if (System.currentTimeMillis() < circuitOpenUntil) {
            return CompletableFuture.completedFuture(false);
        }

        String url = "https://proxycheck.io/v2/" + ip + "?key=" + apiKey + "&vpn=1&asn=1";
        return CompletableFuture.supplyAsync(() -> {
            try {
                String response = NetworkUtils.httpGet(url, 5000);
                if (response == null) { recordFailure(); return false; }
                failures.set(0);
                return response.contains("\"proxy\": \"yes\"") || response.contains("\"type\": \"VPN\"");
            } catch (Exception e) {
                recordFailure();
                return false;
            }
        });
    }

    private void recordFailure() {
        if (failures.incrementAndGet() >= 3) {
            circuitOpenUntil = System.currentTimeMillis() + 60_000L;
            failures.set(0);
        }
    }

    public boolean isAvailable() { return !apiKey.isBlank() && System.currentTimeMillis() >= circuitOpenUntil; }
}
