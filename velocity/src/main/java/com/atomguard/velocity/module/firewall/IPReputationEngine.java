package com.atomguard.velocity.module.firewall;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IP itibar puanlama motoru - davranışa göre skor atar.
 */
public class IPReputationEngine {

    private final ConcurrentHashMap<String, AtomicInteger> scores = new ConcurrentHashMap<>();
    private final int autoBanThreshold;

    public IPReputationEngine(int autoBanThreshold) {
        this.autoBanThreshold = autoBanThreshold;
    }

    public void addScore(String ip, int points) {
        scores.computeIfAbsent(ip, k -> new AtomicInteger(0)).addAndGet(points);
    }

    public void reduceScore(String ip, int points) {
        AtomicInteger score = scores.get(ip);
        if (score != null) {
            int newVal = score.addAndGet(-points);
            if (newVal <= 0) scores.remove(ip);
        }
    }

    public int getScore(String ip) {
        AtomicInteger score = scores.get(ip);
        return score != null ? score.get() : 0;
    }

    public boolean shouldAutoBan(String ip) {
        return getScore(ip) >= autoBanThreshold;
    }

    public void reset(String ip) { scores.remove(ip); }

    public Map<String, Integer> getTopScores(int limit) {
        return scores.entrySet().stream()
                .sorted((a, b) -> b.getValue().get() - a.getValue().get())
                .limit(limit)
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().get()
                ));
    }

    public void decayAll(int decayAmount) {
        scores.entrySet().removeIf(e -> {
            int newVal = e.getValue().addAndGet(-decayAmount);
            return newVal <= 0;
        });
    }
}
