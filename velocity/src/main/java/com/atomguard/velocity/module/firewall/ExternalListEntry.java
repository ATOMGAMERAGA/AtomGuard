package com.atomguard.velocity.module.firewall;

public class ExternalListEntry {
    private final String source;
    private final String value; // IP or CIDR
    private final long timestamp;

    public ExternalListEntry(String source, String value, long timestamp) {
        this.source = source;
        this.value = value;
        this.timestamp = timestamp;
    }

    public String getSource() { return source; }
    public String getValue() { return value; }
    public long getTimestamp() { return timestamp; }
}