package com.atomguard.velocity.module.ratelimit;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global hız sınırlaması modülü.
 */
public class GlobalRateLimitModule extends VelocityModule {

    private ConnectionRateLimiter connectionLimiter;
    private PingRateLimiter pingLimiter;
    // Keeping old limiters if needed or just replacing
    // Assuming PerSubnetRateLimiter and LoginRateLimiter are still valid and useful
    // But since I don't see their code, I'll keep using them if they were working or replace if I can.
    // Given the prompt "Enhancement", I should probably keep existing functionality and add new ones.
    // However, PerIPRateLimiter is replaced by ConnectionRateLimiter.
    
    // Global counters
    private final AtomicInteger globalConnectionCounter = new AtomicInteger(0);
    private final AtomicInteger globalPingCounter = new AtomicInteger(0);
    private long lastGlobalReset = System.currentTimeMillis();

    public GlobalRateLimitModule(AtomGuardVelocity plugin) {
        super(plugin, "hiz-siniri");
    }

    @Override
    public void onEnable() {
        int connLimit = getConfigInt("baglanti-saniye.ip-basina-max", 3);
        int pingLimit = getConfigInt("ping-saniye.ip-basina-max", 5);
        
        // Window is 1 second for these "per second" limits
        this.connectionLimiter = new ConnectionRateLimiter(connLimit, 1);
        this.pingLimiter = new PingRateLimiter(pingLimit, 1);

        plugin.getProxyServer().getScheduler()
            .buildTask(plugin, this::periodicCleanup)
            .repeat(1, TimeUnit.SECONDS) // Frequent cleanup/reset for global counters
            .schedule();
            
        logger.info("Global Rate Limit module enabled.");
    }

    @Override
    public void onDisable() {
        // cleanup tasks are handled by scheduler shutdown on plugin disable usually
    }

    public boolean allowConnection(String ip) {
        if (!isEnabled()) return true;

        // Global Check
        int globalMax = getConfigInt("baglanti-saniye.global-max", 50);
        if (globalConnectionCounter.get() >= globalMax) {
            incrementBlocked();
            return false; 
        }

        // IP Check
        if (!connectionLimiter.allowConnection(ip)) {
            incrementBlocked();
            return false;
        }
        
        globalConnectionCounter.incrementAndGet();
        return true;
    }

    public boolean allowPing(String ip) {
        if (!isEnabled()) return true;

        // Global Check
        int globalMax = getConfigInt("ping-saniye.global-max", 200);
        if (globalPingCounter.get() >= globalMax) {
            return false;
        }

        // IP Check
        if (!pingLimiter.allowPing(ip)) {
            incrementBlocked();
            return false;
        }
        
        globalPingCounter.incrementAndGet();
        return true;
    }

    private void periodicCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastGlobalReset >= 1000) {
            globalConnectionCounter.set(0);
            globalPingCounter.set(0);
            lastGlobalReset = now;
        }
        
        // Cleanup maps less frequently
        if (now % 60000 < 1000) { // roughly every minute
            if (connectionLimiter != null) connectionLimiter.cleanup();
            if (pingLimiter != null) pingLimiter.cleanup();
        }
    }
}