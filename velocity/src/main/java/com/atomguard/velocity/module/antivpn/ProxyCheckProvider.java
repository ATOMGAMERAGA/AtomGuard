package com.atomguard.velocity.module.antivpn;

import com.atomguard.velocity.util.NetworkUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * proxycheck.io API entegrasyonu.
 */
public class ProxyCheckProvider {

    private final String apiKey;
    private final String baseUrl;
    private final AtomicInteger failures = new AtomicInteger(0);
    private volatile long circuitOpenUntil = 0;

    private static final String DEFAULT_BASE_URL = "https://proxycheck.io/v2/";

    public ProxyCheckProvider(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : DEFAULT_BASE_URL;
    }

    public CompletableFuture<Boolean> isVPN(String ip) {
        if (System.currentTimeMillis() < circuitOpenUntil) {
            return CompletableFuture.completedFuture(false);
        }

        String url = baseUrl + ip + "?key=" + apiKey + "&vpn=1&asn=1";
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
