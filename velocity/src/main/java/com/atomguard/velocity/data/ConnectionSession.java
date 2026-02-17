package com.atomguard.velocity.data;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bağlantı oturumu metadata bilgileri.
 */
public class ConnectionSession {

    private final String ip;
    private volatile String hostname;
    private volatile int protocolVersion;
    private volatile String clientBrand;
    private final long firstSeen;
    private final AtomicInteger connectionCount;
    private volatile long lastConnectionTime;

    public ConnectionSession(String ip) {
        this.ip = ip;
        this.firstSeen = System.currentTimeMillis();
        this.lastConnectionTime = System.currentTimeMillis();
        this.connectionCount = new AtomicInteger(1);
        this.clientBrand = "unknown";
        this.hostname = "";
        this.protocolVersion = 0;
    }

    public String getIp() { return ip; }
    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }
    public int getProtocolVersion() { return protocolVersion; }
    public void setProtocolVersion(int protocolVersion) { this.protocolVersion = protocolVersion; }
    public String getClientBrand() { return clientBrand; }
    public void setClientBrand(String clientBrand) { this.clientBrand = clientBrand; }
    public long getFirstSeen() { return firstSeen; }
    public long getLastConnectionTime() { return lastConnectionTime; }
    public int getConnectionCount() { return connectionCount.get(); }

    public void incrementConnectionCount() {
        connectionCount.incrementAndGet();
        lastConnectionTime = System.currentTimeMillis();
    }

    /**
     * Belirtilen zaman penceresindeki bağlantı hızını döndürür (bağlantı/saniye).
     */
    public double getConnectionRate(long windowMs) {
        long elapsed = Math.max(1, System.currentTimeMillis() - firstSeen);
        if (elapsed < windowMs) {
            return connectionCount.get() / (elapsed / 1000.0);
        }
        return connectionCount.get() / (windowMs / 1000.0);
    }
}
