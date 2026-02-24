package com.atomguard.intelligence;

/**
 * Tehdit istihbaratı uyarı nesnesi.
 *
 * @author AtomGuard Team
 * @version 1.2.0
 */
public class IntelligenceAlert {

    public enum Level { ELEVATED, HIGH, CRITICAL }

    private final Level level;
    private final String metric;
    private final double zScore;
    private final double currentValue;
    private final double meanValue;
    private final String details;
    private final long timestamp;

    public IntelligenceAlert(Level level, String metric, double zScore,
                              double currentValue, double meanValue, String details) {
        this.level = level;
        this.metric = metric;
        this.zScore = zScore;
        this.currentValue = currentValue;
        this.meanValue = meanValue;
        this.details = details;
        this.timestamp = System.currentTimeMillis();
    }

    public Level getLevel() { return level; }
    public String getMetric() { return metric; }
    public double getZScore() { return zScore; }
    public double getCurrentValue() { return currentValue; }
    public double getMeanValue() { return meanValue; }
    public String getDetails() { return details; }
    public long getTimestamp() { return timestamp; }

    public ThreatLevel toThreatLevel() {
        return switch (level) {
            case ELEVATED -> ThreatLevel.ELEVATED;
            case HIGH -> ThreatLevel.HIGH;
            case CRITICAL -> ThreatLevel.CRITICAL;
        };
    }
}
