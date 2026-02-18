package com.atomguard.velocity.module.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PasswordSimilarityDetector {

    private final int maxSame;
    // IP -> Map<Hash, Count>
    private final Map<String, Map<String, Integer>> ipHashes = new ConcurrentHashMap<>();
    private final Map<String, Long> lastCleanup = new ConcurrentHashMap<>();

    public PasswordSimilarityDetector(int maxSame) {
        this.maxSame = maxSame;
    }

    public boolean check(String ip, String hash) {
        Map<String, Integer> hashes = ipHashes.computeIfAbsent(ip, k -> new ConcurrentHashMap<>());
        
        // Cleanup old entries for this IP periodically
        long now = System.currentTimeMillis();
        if (now - lastCleanup.computeIfAbsent(ip, k -> now) > TimeUnit.MINUTES.toMillis(10)) {
            hashes.clear();
            lastCleanup.put(ip, now);
        }

        int count = hashes.merge(hash, 1, Integer::sum);
        return count > maxSame;
    }
}