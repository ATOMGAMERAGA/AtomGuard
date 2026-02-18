package com.atomguard.velocity.module.iptables;

public class IPTablesRule {
    private final String ip;
    private final String subnet;
    private final String action;
    private final long expiry;

    public IPTablesRule(String ip, String subnet, String action, long expiry) {
        this.ip = ip;
        this.subnet = subnet;
        this.action = action;
        this.expiry = expiry;
    }

    public String getIp() { return ip; }
    public String getSubnet() { return subnet; }
    public String getAction() { return action; }
    public long getExpiry() { return expiry; }
    
    public boolean isExpired() {
        return System.currentTimeMillis() > expiry;
    }
}