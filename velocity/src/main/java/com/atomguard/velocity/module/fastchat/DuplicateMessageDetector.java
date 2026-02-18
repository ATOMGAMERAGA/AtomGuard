package com.atomguard.velocity.module.fastchat;

import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DuplicateMessageDetector {

    private final Map<String, LinkedList<MessageRecord>> history = new ConcurrentHashMap<>();
    private final int maxHistorySize;

    public DuplicateMessageDetector(int maxHistorySize) {
        this.maxHistorySize = maxHistorySize;
    }

    public boolean isDuplicate(String ip, String message) {
        LinkedList<MessageRecord> records = history.computeIfAbsent(ip, k -> new LinkedList<>());
        long now = System.currentTimeMillis();
        
        synchronized (records) {
            // Check existing
            for (MessageRecord record : records) {
                if (record.message.equalsIgnoreCase(message)) {
                    // Update timestamp to keep it fresh in cache? No, just detect.
                    return true;
                }
            }

            // Add new
            records.addFirst(new MessageRecord(message, now));
            if (records.size() > maxHistorySize) {
                records.removeLast();
            }
        }
        return false;
    }
    
    public void cleanup() {
        long now = System.currentTimeMillis();
        history.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                entry.getValue().removeIf(record -> (now - record.timestamp) > TimeUnit.MINUTES.toMillis(1));
                return entry.getValue().isEmpty();
            }
        });
    }

    private static class MessageRecord {
        String message;
        long timestamp;

        public MessageRecord(String message, long timestamp) {
            this.message = message;
            this.timestamp = timestamp;
        }
    }
}
