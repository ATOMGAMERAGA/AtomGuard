package com.atomguard.module.honeypot;

/**
 * Honeypot portuna yapılan tek bir bağlantıyı temsil eder.
 * Bağlantının kaynağı, zamanlaması, protokol türü ve kara liste durumunu tutar.
 */
public class HoneypotConnection {

    private final String ip;
    private final int port;
    private final long timestamp;
    private final String protocol; // "SLP", "TCP_RAW", "UNKNOWN"
    private final boolean blacklisted;

    public HoneypotConnection(String ip, int port, String protocol, boolean blacklisted) {
        this.ip = ip;
        this.port = port;
        this.timestamp = System.currentTimeMillis();
        this.protocol = protocol;
        this.blacklisted = blacklisted;
    }

    /** Bağlanan IP adresi */
    public String getIp() { return ip; }

    /** Bağlantının geldiği honeypot port numarası */
    public int getPort() { return port; }

    /** Bağlantının gerçekleştiği epoch milisaniye zaman damgası */
    public long getTimestamp() { return timestamp; }

    /** Tespit edilen protokol: "SLP" (Server List Ping), "TCP_RAW", "UNKNOWN" */
    public String getProtocol() { return protocol; }

    /** Bu bağlantı sonucunda IP kara listeye alındı mı */
    public boolean isBlacklisted() { return blacklisted; }

    @Override
    public String toString() {
        return String.format("HoneypotConnection{ip='%s', port=%d, protocol='%s', blacklisted=%b}",
                ip, port, protocol, blacklisted);
    }
}
