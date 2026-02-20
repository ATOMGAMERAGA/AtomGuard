package com.atomguard.velocity.communication;

import com.google.gson.Gson;
import java.util.Map;

/**
 * Sunucular arası JSON tabanlı standart iletişim modeli.
 */
public class RedisMessage {
    
    private final String type;
    private final String sourceProxyId;
    private final long timestamp;
    private final Map<String, String> data;

    public RedisMessage(String type, String sourceProxyId, Map<String, String> data) {
        this.type = type;
        this.sourceProxyId = sourceProxyId;
        this.timestamp = System.currentTimeMillis();
        this.data = data;
    }

    public String getType() { return type; }
    public String getSourceProxyId() { return sourceProxyId; }
    public long getTimestamp() { return timestamp; }
    public Map<String, String> getData() { return data; }

    public String serialize() {
        return new Gson().toJson(this);
    }

    public static RedisMessage deserialize(String json) {
        return new Gson().fromJson(json, RedisMessage.class);
    }
}
