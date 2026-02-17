package com.atomguard.velocity.module.antivpn;

import com.atomguard.velocity.util.NetworkUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * iphub.info API entegrasyonu.
 */
public class IPHubProvider {

    private final String apiKey;
    private final AtomicInteger failures = new AtomicInteger(0);
    private volatile long circuitOpenUntil = 0;

    public IPHubProvider(String apiKey) {
        this.apiKey = apiKey;
    }

    public CompletableFuture<Boolean> isVPN(String ip) {
        if (!isAvailable()) return CompletableFuture.completedFuture(false);

        String url = "https://v2.api.iphub.info/ip/" + ip;
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
