package com.atomguard.velocity.module.latency;

public class LatencyProfile {
    private final String ip;
    private long handshakeTime;
    private long loginTime;
    private long joinTime;
    private int connectionCount;
    private double averageLatency;

    public LatencyProfile(String ip) {
        this.ip = ip;
    }

    public void addMeasurement(long latency) {
        averageLatency = (averageLatency * connectionCount + latency) / (connectionCount + 1);
        connectionCount++;
    }

    public double getAverageLatency() { return averageLatency; }
    public int getConnectionCount() { return connectionCount; }
}