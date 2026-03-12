package com.atomguard.velocity.module.firewall;

/**
 * Harici liste girdisi — kaynak, IP/CIDR değeri ve zaman damgası bilgilerini taşıyan değer nesnesi.
 *
 * @author AtomGuard Team
 * @version 2.0.0
 */
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