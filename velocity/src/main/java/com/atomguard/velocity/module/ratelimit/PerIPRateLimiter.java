package com.atomguard.velocity.module.ratelimit;

import com.atomguard.velocity.module.antiddos.RateLimiter;

/**
 * IP başına genel hız sınırlayıcı wrapper.
 */
public class PerIPRateLimiter {

    private final RateLimiter rateLimiter;
    private final String limitType;

    public PerIPRateLimiter(int capacity, int refillPerSecond, String limitType) {
        this.rateLimiter = new RateLimiter(capacity, refillPerSecond);
        this.limitType = limitType;
    }

    public boolean allowRequest(String ip) {
        return rateLimiter.tryAcquire(ip);
    }

    public boolean allowRequest(String ip, int tokens) {
        return rateLimiter.tryAcquire(ip, tokens);
    }

    public void cleanup() { rateLimiter.cleanup(); }
    public void reset(String ip) { rateLimiter.reset(ip); }
    public String getLimitType() { return limitType; }
    public int getTokens(String ip) { return rateLimiter.getTokens(ip); }
}
