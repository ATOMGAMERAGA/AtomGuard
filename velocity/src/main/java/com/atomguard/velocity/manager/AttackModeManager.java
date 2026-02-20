package com.atomguard.velocity.manager;

import com.atomguard.velocity.AtomGuardVelocity;
import org.slf4j.Logger;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class AttackModeManager {

    public enum AttackLevel {
        NONE(1.0),       // Normal işlem
        ELEVATED(0.7),   // Hafif kısıtlama
        HIGH(0.4),       // Sıkı kısıtlama
        CRITICAL(0.1),   // Sadece doğrulanmış oyuncular
        LOCKDOWN(0.0);   // Tüm bağlantılar kapalı (emergency)

        public final double allowRatio;
        AttackLevel(double allowRatio) { this.allowRatio = allowRatio; }
    }

    private final AtomGuardVelocity plugin;
    private final Logger logger;
    private volatile AttackLevel currentLevel = AttackLevel.NONE;
    private final Deque<Integer> rateHistory = new ConcurrentLinkedDeque<>();
    private final int normalThreshold;

    public AttackModeManager(AtomGuardVelocity plugin) {
        this.plugin = plugin;
        this.logger = plugin.getSlf4jLogger();
        this.normalThreshold = plugin.getConfigManager().getInt("moduller.ddos-koruma.saldiri-modu.esik", 30);
    }

    public void updateFromRate(int connectionsPerSecond) {
        rateHistory.offerFirst(connectionsPerSecond);
        while (rateHistory.size() > 30) rateHistory.pollLast();

        // Son 10 ölçümün ortalaması (stabilizasyon için)
        double avgRate = rateHistory.stream().limit(10)
            .mapToInt(Integer::intValue).average().orElse(0);

        AttackLevel newLevel;
        if (avgRate >= normalThreshold * 5) newLevel = AttackLevel.LOCKDOWN;
        else if (avgRate >= normalThreshold * 3) newLevel = AttackLevel.CRITICAL;
        else if (avgRate >= normalThreshold * 2) newLevel = AttackLevel.HIGH;
        else if (avgRate >= normalThreshold * 1.2) newLevel = AttackLevel.ELEVATED;
        else if (avgRate >= normalThreshold * 0.8 && plugin.isAttackMode()) newLevel = AttackLevel.ELEVATED; // hysteresis
        else newLevel = AttackLevel.NONE;

        if (newLevel != currentLevel) {
            AttackLevel prev = currentLevel;
            currentLevel = newLevel;
            
            if (newLevel != AttackLevel.NONE) {
                if (!plugin.isAttackMode()) plugin.setAttackMode(true, (int)avgRate);
            } else {
                if (plugin.isAttackMode()) plugin.setAttackMode(false);
            }

            logger.warn("Saldırı seviyesi değişti: {} → {} (Hız: {}/sn)", prev, newLevel, (int)avgRate);
            plugin.getAlertManager().alertAttackLevelChanged(prev, newLevel, (int) avgRate);
            
            // Audit Log
            if (plugin.getAuditLogger() != null) {
                plugin.getAuditLogger().log(
                    com.atomguard.velocity.audit.AuditLogger.EventType.ATTACK_STARTED,
                    null, null, "ddos", "Level changed: " + prev + " -> " + newLevel + ", Rate: " + (int)avgRate,
                    com.atomguard.velocity.audit.AuditLogger.Severity.CRITICAL
                );
            }

            // Event fire
            plugin.getEventBus().fireAttackModeToggle(newLevel != AttackLevel.NONE, (int)avgRate);
        }
    }

    public AttackLevel getCurrentLevel() {
        return currentLevel;
    }

    public boolean isLockdown() {
        return currentLevel == AttackLevel.LOCKDOWN;
    }
}
