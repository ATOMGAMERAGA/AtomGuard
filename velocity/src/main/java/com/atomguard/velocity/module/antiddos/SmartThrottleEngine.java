package com.atomguard.velocity.module.antiddos;

import com.atomguard.velocity.AtomGuardVelocity;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adaptif throttling motoru - saldırı yoğunluğuna göre limitler otomatik ayarlanır.
 */
public class SmartThrottleEngine {

    public enum ThrottleMode { NORMAL, CAREFUL, AGGRESSIVE, LOCKDOWN }

    private volatile ThrottleMode currentMode = ThrottleMode.NORMAL;
    private final int normalLimit;
    private final int carefulLimit;
    private final int aggressiveLimit;
    private final int lockdownLimit;
    private final Map<String, AtomicInteger> connectionCounts = new ConcurrentHashMap<>();
    private final AtomGuardVelocity plugin;
    private volatile long lastModeChange = System.currentTimeMillis();

    public SmartThrottleEngine(AtomGuardVelocity plugin, int normalLimit,
                                int carefulLimit, int aggressiveLimit, int lockdownLimit) {
        this.plugin = plugin;
        this.normalLimit = normalLimit;
        this.carefulLimit = carefulLimit;
        this.aggressiveLimit = aggressiveLimit;
        this.lockdownLimit = lockdownLimit;
    }

    public void update(int currentRate) {
        ThrottleMode newMode;
        if (currentRate >= normalLimit * 3) newMode = ThrottleMode.LOCKDOWN;
        else if (currentRate >= normalLimit * 2) newMode = ThrottleMode.AGGRESSIVE;
        else if (currentRate >= normalLimit) newMode = ThrottleMode.CAREFUL;
        else newMode = ThrottleMode.NORMAL;

        if (newMode != currentMode) {
            currentMode = newMode;
            lastModeChange = System.currentTimeMillis();
            plugin.getLogManager().log("Throttle modu değişti: " + getModeDisplayName());
        }
    }

    public boolean shouldAllow(String ip, boolean isVerified) {
        int count = connectionCounts.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
        int limit = switch (currentMode) {
            case NORMAL -> normalLimit;
            case CAREFUL -> carefulLimit;
            case AGGRESSIVE -> isVerified ? aggressiveLimit : aggressiveLimit / 2;
            case LOCKDOWN -> isVerified ? lockdownLimit : 0;
        };
        if (limit <= 0) return isVerified;
        return count <= limit;
    }

    public ThrottleMode getCurrentMode() { return currentMode; }

    public void relax() {
        long elapsed = System.currentTimeMillis() - lastModeChange;
        if (elapsed > 30_000 && currentMode != ThrottleMode.NORMAL) {
            connectionCounts.clear();
            ThrottleMode prev = currentMode;
            currentMode = switch (currentMode) {
                case LOCKDOWN -> ThrottleMode.AGGRESSIVE;
                case AGGRESSIVE -> ThrottleMode.CAREFUL;
                case CAREFUL -> ThrottleMode.NORMAL;
                default -> ThrottleMode.NORMAL;
            };
            if (currentMode != prev) {
                lastModeChange = System.currentTimeMillis();
                plugin.getLogManager().log("Throttle gevşetildi: " + getModeDisplayName());
            }
        }
    }

    public String getModeDisplayName() {
        return switch (currentMode) {
            case NORMAL -> "Normal";
            case CAREFUL -> "Dikkatli";
            case AGGRESSIVE -> "Agresif";
            case LOCKDOWN -> "Kilitleme";
        };
    }
}
