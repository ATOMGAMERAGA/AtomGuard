package com.atomguard.velocity.data;

import java.util.List;

/**
 * Saldırı anlık görüntüsü - saldırı sırasındaki ağ durumunun kaydı.
 */
public class AttackSnapshot {

    private final long timestamp;
    private final int peakConnectionRate;
    private final int blockedConnections;
    private final String dominantSourceCIDR;
    private final List<String> topAttackerIPs;
    private final boolean attackModeTriggered;

    public AttackSnapshot(long timestamp, int peakConnectionRate, int blockedConnections,
                          String dominantSourceCIDR, List<String> topAttackerIPs,
                          boolean attackModeTriggered) {
        this.timestamp = timestamp;
        this.peakConnectionRate = peakConnectionRate;
        this.blockedConnections = blockedConnections;
        this.dominantSourceCIDR = dominantSourceCIDR;
        this.topAttackerIPs = List.copyOf(topAttackerIPs);
        this.attackModeTriggered = attackModeTriggered;
    }

    public long getTimestamp() { return timestamp; }
    public int getPeakConnectionRate() { return peakConnectionRate; }
    public int getBlockedConnections() { return blockedConnections; }
    public String getDominantSourceCIDR() { return dominantSourceCIDR; }
    public List<String> getTopAttackerIPs() { return topAttackerIPs; }
    public boolean isAttackModeTriggered() { return attackModeTriggered; }

    /**
     * İnsan tarafından okunabilir özet döndürür.
     */
    public String getSummary() {
        return String.format(
            "Saldırı Özeti: Tepe hızı=%d/s | Engellenen=%d | Kaynak=%s | Saldırı modu=%s",
            peakConnectionRate, blockedConnections,
            dominantSourceCIDR != null ? dominantSourceCIDR : "Bilinmiyor",
            attackModeTriggered ? "AKTİF" : "KAPALI"
        );
    }

    /**
     * Factory metodu: mevcut saldırı snapshot'u oluşturur.
     */
    public static AttackSnapshot current(int peakRate, int blockedCount, String sourceCIDR,
                                         List<String> topIPs, boolean attackMode) {
        return new AttackSnapshot(System.currentTimeMillis(), peakRate, blockedCount,
                                  sourceCIDR, topIPs, attackMode);
    }

    @Override
    public String toString() {
        return "AttackSnapshot{" + getSummary() + "}";
    }
}
