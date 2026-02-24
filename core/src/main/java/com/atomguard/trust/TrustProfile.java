package com.atomguard.trust;

import java.util.UUID;

/**
 * Oyuncu güven profili.
 * Hem kalıcı (JSON'a kaydedilen) hem de geçici (session) veriler içerir.
 *
 * @author AtomGuard Team
 * @version 1.2.0
 */
public class TrustProfile {

    private UUID uuid;
    private String lastKnownName;
    private double trustScore;
    private TrustTier currentTier;

    // Pozitif faktörler
    private long firstJoinTimestamp;
    private int totalPlaytimeMinutes;
    private int uniqueLoginDays;
    private int consecutiveCleanSessions;
    private long lastJoinTimestamp;

    // Negatif faktörler
    private int totalViolations;
    private int recentViolations;         // Son 24 saatteki ihlaller
    private int kickCount;
    private int suspiciousPacketCount;
    private long lastViolationTimestamp;
    private long lastRecentViolationReset;

    // Hesaplama meta
    private long lastCalculation;
    private double lastCalculatedScore;

    // Geçici (JSON'a kaydedilmez)
    private transient long currentSessionStart;
    private transient boolean hadViolationThisSession;

    public TrustProfile() {
        this.trustScore = 20.0;
        this.currentTier = TrustTier.NEW_PLAYER;
    }

    public TrustProfile(UUID uuid) {
        this();
        this.uuid = uuid;
        this.firstJoinTimestamp = System.currentTimeMillis();
        this.lastJoinTimestamp = System.currentTimeMillis();
        this.lastRecentViolationReset = System.currentTimeMillis();
    }

    /** Oturum başladığında çağrılır */
    public void markSessionStart() {
        this.currentSessionStart = System.currentTimeMillis();
        this.hadViolationThisSession = false;
    }

    /** Son 24 saat ihlal sayacını sıfırlar */
    public void resetRecentViolations() {
        this.recentViolations = 0;
        this.lastRecentViolationReset = System.currentTimeMillis();
    }

    // ─── Getters / Setters ───

    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }

    public String getLastKnownName() { return lastKnownName; }
    public void setLastKnownName(String name) { this.lastKnownName = name; }

    public double getTrustScore() { return trustScore; }
    public void setTrustScore(double score) {
        this.trustScore = Math.max(0.0, Math.min(100.0, score));
        this.currentTier = TrustTier.fromScore(this.trustScore);
    }

    public TrustTier getCurrentTier() { return currentTier; }
    public void setCurrentTier(TrustTier tier) { this.currentTier = tier; }

    public long getFirstJoinTimestamp() { return firstJoinTimestamp; }
    public void setFirstJoinTimestamp(long ts) { this.firstJoinTimestamp = ts; }

    public int getTotalPlaytimeMinutes() { return totalPlaytimeMinutes; }
    public void addPlaytimeMinutes(int minutes) { this.totalPlaytimeMinutes += minutes; }
    public void setTotalPlaytimeMinutes(int minutes) { this.totalPlaytimeMinutes = minutes; }

    public int getUniqueLoginDays() { return uniqueLoginDays; }
    public void incrementUniqueLoginDays() { this.uniqueLoginDays++; }
    public void setUniqueLoginDays(int days) { this.uniqueLoginDays = days; }

    public int getConsecutiveCleanSessions() { return consecutiveCleanSessions; }
    public void incrementConsecutiveCleanSessions() { this.consecutiveCleanSessions++; }
    public void resetConsecutiveCleanSessions() { this.consecutiveCleanSessions = 0; }
    public void setConsecutiveCleanSessions(int count) { this.consecutiveCleanSessions = count; }

    public long getLastJoinTimestamp() { return lastJoinTimestamp; }
    public void setLastJoinTimestamp(long ts) { this.lastJoinTimestamp = ts; }

    public int getTotalViolations() { return totalViolations; }
    public void incrementTotalViolations() { this.totalViolations++; }
    public void setTotalViolations(int count) { this.totalViolations = count; }

    public int getRecentViolations() { return recentViolations; }
    public void incrementRecentViolations() { this.recentViolations++; }
    public void setRecentViolations(int count) { this.recentViolations = count; }

    public int getKickCount() { return kickCount; }
    public void incrementKickCount() { this.kickCount++; }
    public void setKickCount(int count) { this.kickCount = count; }

    public int getSuspiciousPacketCount() { return suspiciousPacketCount; }
    public void incrementSuspiciousPacketCount() { this.suspiciousPacketCount++; }
    public void setSuspiciousPacketCount(int count) { this.suspiciousPacketCount = count; }

    public long getLastViolationTimestamp() { return lastViolationTimestamp; }
    public void setLastViolationTimestamp(long ts) { this.lastViolationTimestamp = ts; }

    public long getLastRecentViolationReset() { return lastRecentViolationReset; }
    public void setLastRecentViolationReset(long ts) { this.lastRecentViolationReset = ts; }

    public long getLastCalculation() { return lastCalculation; }
    public void setLastCalculation(long ts) { this.lastCalculation = ts; }

    public double getLastCalculatedScore() { return lastCalculatedScore; }
    public void setLastCalculatedScore(double score) { this.lastCalculatedScore = score; }

    public long getCurrentSessionStart() { return currentSessionStart; }
    public boolean isHadViolationThisSession() { return hadViolationThisSession; }
    public void setHadViolationThisSession(boolean had) { this.hadViolationThisSession = had; }
}
