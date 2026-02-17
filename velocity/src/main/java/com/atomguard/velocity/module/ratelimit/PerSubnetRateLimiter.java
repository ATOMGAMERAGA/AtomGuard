package com.atomguard.velocity.module.ratelimit;

import com.atomguard.velocity.module.antiddos.RateLimiter;
import com.atomguard.velocity.util.IPUtils;

/**
 * Subnet (/24) başına hız sınırlaması - distributed bot saldırılarına karşı.
 */
public class PerSubnetRateLimiter {

    private final RateLimiter rateLimiter;
    private final int subnetBits;

    public PerSubnetRateLimiter(int capacity, int refillPerSecond, int subnetBits) {
        this.rateLimiter = new RateLimiter(capacity, refillPerSecond);
        this.subnetBits = subnetBits;
    }

    public boolean allowRequest(String ip) {
        String subnet = getSubnet(ip);
        return rateLimiter.tryAcquire(subnet);
    }

    private String getSubnet(String ip) {
        return switch (subnetBits) {
            case 24 -> IPUtils.getSubnet24(ip);
            case 16 -> IPUtils.getSubnet16(ip);
            default -> IPUtils.getSubnet24(ip);
        };
    }

    public void cleanup() { rateLimiter.cleanup(); }
}
