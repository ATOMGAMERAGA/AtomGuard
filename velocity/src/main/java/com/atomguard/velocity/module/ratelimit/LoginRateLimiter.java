package com.atomguard.velocity.module.ratelimit;

import com.atomguard.velocity.module.antiddos.RateLimiter;

/**
 * Giriş (login/pre-login) hız sınırlaması.
 */
public class LoginRateLimiter {

    private final RateLimiter ipLimiter;
    private final RateLimiter globalLimiter;
    private final int globalCapacity;

    public LoginRateLimiter(int ipCapacity, int ipRefillPerSec, int globalCapacity, int globalRefillPerSec) {
        this.ipLimiter = new RateLimiter(ipCapacity, ipRefillPerSec);
        this.globalLimiter = new RateLimiter(globalCapacity, globalRefillPerSec);
        this.globalCapacity = globalCapacity;
    }

    public LoginCheckResult checkLogin(String ip) {
        if (!globalLimiter.tryAcquire("global"))
            return new LoginCheckResult(false, "global-limit");
        if (!ipLimiter.tryAcquire(ip))
            return new LoginCheckResult(false, "ip-limit");
        return new LoginCheckResult(true, "ok");
    }

    public void cleanup() {
        ipLimiter.cleanup();
    }

    public record LoginCheckResult(boolean allowed, String reason) {}
}
