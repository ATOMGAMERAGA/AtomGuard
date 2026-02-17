package com.atomguard.velocity.module.ratelimit;

import com.atomguard.velocity.module.antiddos.RateLimiter;

/**
 * Ping/status isteği hız sınırlaması.
 */
public class PingRateLimiter {

    private final RateLimiter rateLimiter;

    public PingRateLimiter(int pingsPerSecond) {
        this.rateLimiter = new RateLimiter(pingsPerSecond, pingsPerSecond);
    }

    public boolean allowPing(String ip) {
        return rateLimiter.tryAcquire(ip);
    }

    public void cleanup() { rateLimiter.cleanup(); }
}
