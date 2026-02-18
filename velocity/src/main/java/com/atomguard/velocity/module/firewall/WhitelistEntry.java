package com.atomguard.velocity.module.firewall;

public class WhitelistEntry {
    private final ExceptionType type;
    private final String value;
    private final String reason;
    private final long expiry;

    public WhitelistEntry(ExceptionType type, String value, String reason, long expiry) {
        this.type = type;
        this.value = value;
        this.reason = reason;
        this.expiry = expiry;
    }

    public ExceptionType getType() { return type; }
    public String getValue() { return value; }
    public String getReason() { return reason; }
    public long getExpiry() { return expiry; }

    public boolean isExpired() {
        return expiry > 0 && System.currentTimeMillis() > expiry;
    }
}