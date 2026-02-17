package com.atomguard.velocity.module.antivpn;

import com.atomguard.velocity.util.NetworkUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ip-api.com API entegrasyonu (ücretsiz, dakikada 45 istek).
 */
public class IPApiProvider {

    private final AtomicInteger failures = new AtomicInteger(0);
    private volatile long circuitOpenUntil = 0;
    private volatile long rateLimitResetMs = 0;

    public CompletableFuture<Boolean> isVPN(String ip) {
        if (System.currentTimeMillis() < circuitOpenUntil) {
            return CompletableFuture.completedFuture(false);
        }
        if (System.currentTimeMillis() < rateLimitResetMs) {
            return CompletableFuture.completedFuture(false);
        }

        String url = "http://ip-api.com/json/" + ip + "?fields=proxy,hosting";
        return CompletableFuture.supplyAsync(() -> {
            try {
                String response = NetworkUtils.httpGet(url, 5000);
                if (response == null) { recordFailure(); return false; }
                failures.set(0);
                boolean proxy = response.contains("\"proxy\":true");
                boolean hosting = response.contains("\"hosting\":true");
                return proxy || hosting;
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

    public boolean isAvailable() { return System.currentTimeMillis() >= circuitOpenUntil; }
}
