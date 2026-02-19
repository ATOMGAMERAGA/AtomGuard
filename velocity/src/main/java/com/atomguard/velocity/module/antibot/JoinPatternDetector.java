package com.atomguard.velocity.module.antibot;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Katılma/ayrılma örüntüsü analizi — hızlı join/quit döngüsü tespiti.
 *
 * <p>Düzeltmeler (false positive önleme):
 * <ul>
 *   <li>{@code maxJoinsInWindow} minimum 8 (önceden 5)</li>
 *   <li>{@code maxQuitsBeforeSuspect} minimum 15 (önceden 10)</li>
 *   <li>Quit count decay: 10 dakika sonra 3 puan düşürülür</li>
 *   <li>Skor yumuşatma: rapid=30 (önceden 40), quitter=15 (önceden 20)</li>
 * </ul>
 */
public class JoinPatternDetector {

    private final Map<String, Deque<Long>> joinTimes = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> quitCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> lastQuitTimes = new ConcurrentHashMap<>();

    private final int joinWindowSeconds;
    private final int maxJoinsInWindow;
    private final int maxQuitsBeforeSuspect;

    /** Quit decay süresi: 10 dakika */
    private static final long QUIT_DECAY_INTERVAL_MS = 600_000L;
    /** Quit decay miktarı */
    private static final int QUIT_DECAY_AMOUNT = 3;

    public JoinPatternDetector(int joinWindowSeconds, int maxJoinsInWindow, int maxQuitsBeforeSuspect) {
        this.joinWindowSeconds = joinWindowSeconds;
        this.maxJoinsInWindow = Math.max(8, maxJoinsInWindow);
        this.maxQuitsBeforeSuspect = Math.max(15, maxQuitsBeforeSuspect);
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
        long now = System.currentTimeMillis();
        quitCounts.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
        lastQuitTimes.put(ip, now);
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
        applyQuitDecay(ip);
        AtomicInteger count = quitCounts.get(ip);
        return count != null && count.get() >= maxQuitsBeforeSuspect;
    }

    /**
     * Quit sayacına zaman bazlı decay uygular: 10dk sonra 3 düşürür.
     */
    private void applyQuitDecay(String ip) {
        Long lastQuit = lastQuitTimes.get(ip);
        if (lastQuit == null) return;
        long now = System.currentTimeMillis();
        if (now - lastQuit >= QUIT_DECAY_INTERVAL_MS) {
            AtomicInteger count = quitCounts.get(ip);
            if (count != null) {
                int newVal = count.addAndGet(-QUIT_DECAY_AMOUNT);
                if (newVal <= 0) {
                    quitCounts.remove(ip);
                    lastQuitTimes.remove(ip);
                } else {
                    lastQuitTimes.put(ip, now);
                }
            }
        }
    }

    /**
     * Join pattern skoru — yumuşatılmış değerler.
     * rapid=30 (önceden 40), quitter=15 (önceden 20), ikisi birden=50.
     */
    public int getJoinScore(String ip) {
        boolean rapid = isRapidJoiner(ip);
        boolean quitter = isFrequentQuitter(ip);
        if (rapid && quitter) return 50;
        if (rapid) return 30;
        if (quitter) return 15;
        return 0;
    }

    private void pruneOld(Deque<Long> times, long now) {
        long cutoff = now - (joinWindowSeconds * 1000L);
        while (!times.isEmpty() && times.peekFirst() < cutoff) times.pollFirst();
    }

    public void cleanup() {
        joinTimes.entrySet().removeIf(e -> {
            synchronized (e.getValue()) { return e.getValue().isEmpty(); }
        });
        // Decay uygula ve temizle
        for (String ip : quitCounts.keySet()) {
            applyQuitDecay(ip);
        }
    }
}
