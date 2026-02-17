package com.atomguard.velocity.module.antibot;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Katılma/ayrılma örüntüsü analizi - hızlı join/quit döngüsü tespiti.
 */
public class JoinPatternDetector {

    private final Map<String, Deque<Long>> joinTimes = new ConcurrentHashMap<>();
    private final Map<String, Integer> quitCounts = new ConcurrentHashMap<>();
    private final int joinWindowSeconds;
    private final int maxJoinsInWindow;
    private final int maxQuitsBeforeSuspect;

    public JoinPatternDetector(int joinWindowSeconds, int maxJoinsInWindow, int maxQuitsBeforeSuspect) {
        this.joinWindowSeconds = joinWindowSeconds;
        this.maxJoinsInWindow = maxJoinsInWindow;
        this.maxQuitsBeforeSuspect = maxQuitsBeforeSuspect;
    }

    public void recordJoin(String ip) {
        long now = System.currentTimeMillis();
        Deque<Long> times = joinTimes.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (times) {
            pruneOld(times, now);
            times.addLast(now);
        }
    }

    public void recordQuit(String ip) {
        quitCounts.merge(ip, 1, Integer::sum);
    }

    public boolean isRapidJoiner(String ip) {
        Deque<Long> times = joinTimes.get(ip);
        if (times == null) return false;
        long now = System.currentTimeMillis();
        synchronized (times) {
            pruneOld(times, now);
            return times.size() >= maxJoinsInWindow;
        }
    }

    public boolean isFrequentQuitter(String ip) {
        return quitCounts.getOrDefault(ip, 0) >= maxQuitsBeforeSuspect;
    }

    public int getJoinScore(String ip) {
        int score = 0;
        if (isRapidJoiner(ip)) score += 40;
        if (isFrequentQuitter(ip)) score += 20;
        return score;
    }

    private void pruneOld(Deque<Long> times, long now) {
        long cutoff = now - (joinWindowSeconds * 1000L);
        while (!times.isEmpty() && times.peekFirst() < cutoff) times.pollFirst();
    }

    public void cleanup() {
        joinTimes.entrySet().removeIf(e -> {
            synchronized (e.getValue()) { return e.getValue().isEmpty(); }
        });
    }
}
