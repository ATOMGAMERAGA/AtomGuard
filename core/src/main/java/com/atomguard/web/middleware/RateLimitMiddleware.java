package com.atomguard.web.middleware;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sun.net.httpserver.HttpExchange;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP istek hiz sinirlandirma middleware'i.
 * IP bazli saniyede maksimum istek sayisini kontrol eder.
 *
 * @author AtomGuard Team
 * @version 2.0.0
 */
public class RateLimitMiddleware {

    private final Cache<String, RateLimitTracker> rateLimits;
    private final int maxRequestsPerSecond;

    public RateLimitMiddleware(int maxRequestsPerSecond) {
        this.maxRequestsPerSecond = maxRequestsPerSecond;
        this.rateLimits = Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .build();
    }

    /**
     * Istek hiz limitini kontrol eder.
     *
     * @param exchange HTTP exchange
     * @return Limit asilmamissa true
     */
    public boolean checkRateLimit(HttpExchange exchange) {
        String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
        RateLimitTracker tracker = rateLimits.get(ip, k -> new RateLimitTracker());
        return tracker.tryAcquire(maxRequestsPerSecond);
    }

    static class RateLimitTracker {
        private final AtomicInteger requests = new AtomicInteger(0);
        private volatile long lastReset = System.currentTimeMillis();

        boolean tryAcquire(int maxRequests) {
            long now = System.currentTimeMillis();
            if (now - lastReset > 1000) {
                lastReset = now;
                requests.set(0);
            }
            return requests.incrementAndGet() <= maxRequests;
        }
    }
}
