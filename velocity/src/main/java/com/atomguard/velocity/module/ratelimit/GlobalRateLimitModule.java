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
    public int getPriority() { return 20; }

    @Override
    public void onConfigReload() {
        int connLimit = getConfigInt("baglanti-saniye.ip-basina-max", 3);
        int pingLimit = getConfigInt("ping-saniye.ip-basina-max", 5);
        
        // Sayaçlar sıfırlanabilir (Reload anında küçük bir tolerans), önemli olan yeni limitin devreye girmesi.
        this.connectionLimiter = new ConnectionRateLimiter(connLimit, 1);
        this.pingLimiter = new PingRateLimiter(pingLimit, 1);
        logger.info("Hız sınırlaması ayarları dinamik olarak yenilendi.");
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
        return allowConnectionWithInfo(ip).allowed();
    }

    public ConnectionRateLimiter.RateLimitResult allowConnectionWithInfo(String ip) {
        if (!isEnabled()) return ConnectionRateLimiter.RateLimitResult.ALLOWED;

        // Global Check
        int globalMax = getConfigInt("baglanti-saniye.global-max", 50);
        if (globalConnectionCounter.get() >= globalMax) {
            incrementBlocked();
            return new ConnectionRateLimiter.RateLimitResult(false, 1000); 
        }

        // IP Check
        ConnectionRateLimiter.RateLimitResult result = connectionLimiter.check(ip);
        if (!result.allowed()) {
            incrementBlocked();
        } else {
            globalConnectionCounter.incrementAndGet();
        }
        
        return result;
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

    public void cleanup() {
        if (connectionLimiter != null) connectionLimiter.cleanup();
        if (pingLimiter != null) pingLimiter.cleanup();
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