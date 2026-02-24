package com.atomguard.forensics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detaylı saldırı snapshot'ı — saldırı sırasında toplanan tüm verileri içerir.
 *
 * @author AtomGuard Team
 * @version 1.2.0
 */
public class AttackSnapshot {

    // Temel Bilgiler
    private String snapshotId;
    private long startTime;
    private long endTime;
    private AttackClassification classification = AttackClassification.UNKNOWN;

    // Trafik İstatistikleri
    private int peakConnectionRate;
    private long totalConnectionAttempts;
    private long totalBlocked;
    private long totalAllowed;
    private double avgConnectionRate;

    // IP Analizi
    private int uniqueIpCount;
    private int uniqueSubnetCount;
    private Map<String, Integer> topIps = new LinkedHashMap<>();
    private Map<String, Integer> topSubnets = new LinkedHashMap<>();

    // Coğrafi Dağılım (GeoIP varsa)
    private Map<String, Integer> countryDistribution = new LinkedHashMap<>();

    // Modül İstatistikleri
    private Map<String, Long> moduleBlockCounts = new LinkedHashMap<>();
    private List<String> triggeredModules = new ArrayList<>();

    // Zaman Çizelgesi
    private List<TimelineEvent> timeline = new ArrayList<>();

    // Sunucu Durumu
    private double avgTps = 20.0;
    private long avgMemoryUsageMb;
    private int onlinePlayerCount;
    private int onlinePlayerCountEnd;

    // Sonuç
    private String resolution = "unknown";
    private long durationSeconds;
    private String severity = "LOW";

    /**
     * Şiddet hesapla: threshold ile karşılaştırarak LOW/MEDIUM/HIGH/CRITICAL döner.
     */
    public String calculateSeverity(int threshold) {
        if (durationSeconds >= 900 && peakConnectionRate >= threshold * 10) return "CRITICAL";
        if (durationSeconds >= 900 || peakConnectionRate >= threshold * 5) return "HIGH";
        if (durationSeconds >= 300 || peakConnectionRate >= threshold * 2) return "MEDIUM";
        return "LOW";
    }

    // ─── Getters / Setters ───

    public String getSnapshotId() { return snapshotId; }
    public void setSnapshotId(String id) { this.snapshotId = id; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long t) { this.startTime = t; }

    public long getEndTime() { return endTime; }
    public void setEndTime(long t) { this.endTime = t; }

    public AttackClassification getClassification() { return classification; }
    public void setClassification(AttackClassification c) { this.classification = c; }

    public int getPeakConnectionRate() { return peakConnectionRate; }
    public void setPeakConnectionRate(int r) { this.peakConnectionRate = r; }

    public long getTotalConnectionAttempts() { return totalConnectionAttempts; }
    public void setTotalConnectionAttempts(long c) { this.totalConnectionAttempts = c; }

    public long getTotalBlocked() { return totalBlocked; }
    public void setTotalBlocked(long b) { this.totalBlocked = b; }

    public long getTotalAllowed() { return totalAllowed; }
    public void setTotalAllowed(long a) { this.totalAllowed = a; }

    public double getAvgConnectionRate() { return avgConnectionRate; }
    public void setAvgConnectionRate(double r) { this.avgConnectionRate = r; }

    public int getUniqueIpCount() { return uniqueIpCount; }
    public void setUniqueIpCount(int c) { this.uniqueIpCount = c; }

    public int getUniqueSubnetCount() { return uniqueSubnetCount; }
    public void setUniqueSubnetCount(int c) { this.uniqueSubnetCount = c; }

    public Map<String, Integer> getTopIps() { return topIps; }
    public void setTopIps(Map<String, Integer> m) { this.topIps = m; }

    public Map<String, Integer> getTopSubnets() { return topSubnets; }
    public void setTopSubnets(Map<String, Integer> m) { this.topSubnets = m; }

    public Map<String, Integer> getCountryDistribution() { return countryDistribution; }
    public void setCountryDistribution(Map<String, Integer> m) { this.countryDistribution = m; }

    public Map<String, Long> getModuleBlockCounts() { return moduleBlockCounts; }
    public void setModuleBlockCounts(Map<String, Long> m) { this.moduleBlockCounts = m; }

    public List<String> getTriggeredModules() { return triggeredModules; }
    public void setTriggeredModules(List<String> l) { this.triggeredModules = l; }

    public List<TimelineEvent> getTimeline() { return timeline; }
    public void setTimeline(List<TimelineEvent> t) { this.timeline = t; }

    public double getAvgTps() { return avgTps; }
    public void setAvgTps(double t) { this.avgTps = t; }

    public long getAvgMemoryUsageMb() { return avgMemoryUsageMb; }
    public void setAvgMemoryUsageMb(long m) { this.avgMemoryUsageMb = m; }

    public int getOnlinePlayerCount() { return onlinePlayerCount; }
    public void setOnlinePlayerCount(int c) { this.onlinePlayerCount = c; }

    public int getOnlinePlayerCountEnd() { return onlinePlayerCountEnd; }
    public void setOnlinePlayerCountEnd(int c) { this.onlinePlayerCountEnd = c; }

    public String getResolution() { return resolution; }
    public void setResolution(String r) { this.resolution = r; }

    public long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(long d) { this.durationSeconds = d; }

    public String getSeverity() { return severity; }
    public void setSeverity(String s) { this.severity = s; }

    /** Snapshot ID'nin ilk 8 karakterini döner (kısaltılmış gösterim). */
    public String getShortId() {
        return snapshotId != null && snapshotId.length() >= 8 ? snapshotId.substring(0, 8) : snapshotId;
    }
}
