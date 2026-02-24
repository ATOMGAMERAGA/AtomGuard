package com.atomguard.forensics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Saldırı timeline'ındaki tek bir olay.
 *
 * @author AtomGuard Team
 * @version 1.2.0
 */
public class TimelineEvent {

    private long timestamp;
    private String type;        // ATTACK_START, PEAK_REACHED, MODULE_TRIGGERED, METRIC_SNAPSHOT, ATTACK_END
    private String description;
    private Map<String, Object> metadata;

    public TimelineEvent() {
        this.metadata = new LinkedHashMap<>();
    }

    public TimelineEvent(long timestamp, String type, String description) {
        this.timestamp = timestamp;
        this.type = type;
        this.description = description;
        this.metadata = new LinkedHashMap<>();
    }

    /**
     * Metadata ekleyerek fluent chaining sağlar.
     */
    public TimelineEvent withMeta(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
