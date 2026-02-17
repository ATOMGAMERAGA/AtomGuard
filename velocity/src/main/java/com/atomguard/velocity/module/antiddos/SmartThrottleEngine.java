package com.atomguard.velocity.module.antiddos;

import com.atomguard.velocity.AtomGuardVelocity;

/**
 * Adaptif throttling motoru - saldırı yoğunluğuna göre limitler otomatik ayarlanır.
 */
public class SmartThrottleEngine {

    public enum ThrottleMode { NORMAL, CAREFUL, AGGRESSIVE, LOCKDOWN }

    private volatile ThrottleMode currentMode = ThrottleMode.NORMAL;
    private final int normalLimit;
    private final AtomGuardVelocity plugin;
    private volatile long lastModeChange = System.currentTimeMillis();

    public SmartThrottleEngine(AtomGuardVelocity plugin, int normalLimit,
                                int carefulLimit, int aggressiveLimit, int lockdownLimit) {
        this.plugin = plugin;
        this.normalLimit = normalLimit;
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
        if (currentMode == ThrottleMode.LOCKDOWN) return isVerified;
        return true;
    }

    public ThrottleMode getCurrentMode() { return currentMode; }

    public void relax() {
        long elapsed = System.currentTimeMillis() - lastModeChange;
        if (elapsed > 30_000 && currentMode != ThrottleMode.NORMAL) {
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
