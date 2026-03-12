package com.atomguard.velocity.module.antivpn;

import com.atomguard.velocity.util.NetworkUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * iphub.info API entegrasyonu.
 */
public class IPHubProvider {

    private final String apiKey;
    private final String baseUrl;
    private final AtomicInteger failures = new AtomicInteger(0);
    private volatile long circuitOpenUntil = 0;

    private static final String DEFAULT_BASE_URL = "https://v2.api.iphub.info/ip/";

    public IPHubProvider(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : DEFAULT_BASE_URL;
    }

    public CompletableFuture<Boolean> isVPN(String ip) {
        if (!isAvailable()) return CompletableFuture.completedFuture(false);

        String url = baseUrl + ip;
        return CompletableFuture.supplyAsync(() -> {
            try {
                String response = NetworkUtils.httpGet(url, 5000, "X-Key", apiKey);
                if (response == null) { recordFailure(); return false; }
                failures.set(0);
                // block=1 means hosting/non-residential
                return response.contains("\"block\":1") || response.contains("\"block\":2");
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

    public boolean isAvailable() {
        return !apiKey.isBlank() && System.currentTimeMillis() >= circuitOpenUntil;
    }
}
