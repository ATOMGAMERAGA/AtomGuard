package com.atomguard.velocity.data;

import java.util.UUID;

/**
 * Proxy üzerindeki oyuncu oturum verileri.
 */
public class ProxyPlayerData {

    private final UUID uuid;
    private final String ip;
    private final String username;
    private final long connectTime;
    private final ThreatScore threatScore;
    private final ConnectionSession session;
    private volatile boolean verified;
    private volatile int loginAttempts;

    public ProxyPlayerData(UUID uuid, String ip, String username) {
        this.uuid = uuid;
        this.ip = ip;
        this.username = username;
        this.connectTime = System.currentTimeMillis();
        this.threatScore = new ThreatScore();
        this.session = new ConnectionSession(ip);
        this.verified = false;
        this.loginAttempts = 0;
    }

    public UUID getUuid() { return uuid; }
    public String getIp() { return ip; }
    public String getUsername() { return username; }
    public long getConnectTime() { return connectTime; }
    public ThreatScore getThreatScore() { return threatScore; }
    public ConnectionSession getSession() { return session; }
    public boolean isVerified() { return verified; }
    public int getLoginAttempts() { return loginAttempts; }

    public void markVerified() { this.verified = true; }
    public void incrementLoginAttempts() { this.loginAttempts++; }

    /**
     * Bağlantı süresini milisaniye cinsinden döndürür.
     */
    public long getSessionDuration() {
        return System.currentTimeMillis() - connectTime;
    }

    /**
     * Belirli bir süreyi aşıp aşmadığını kontrol eder.
     */
    public boolean isExpired(long maxMillis) {
        return getSessionDuration() > maxMillis;
    }

    @Override
    public String toString() {
        return "ProxyPlayerData{uuid=" + uuid + ", ip=" + ip + ", username=" + username +
               ", verified=" + verified + ", duration=" + getSessionDuration() + "ms}";
    }
}
