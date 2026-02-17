package com.atomguard.velocity.module.antiddos;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.util.TimeUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global bağlantı hızı izleme ve SYN flood tespiti.
 */
public class SynFloodDetector {

    private final AtomGuardVelocity plugin;
    private final int threshold;
    private final AtomicInteger connectionsThisSecond = new AtomicInteger(0);
    private final AtomicInteger peakRate = new AtomicInteger(0);
    private final Deque<Integer> rateHistory = new ArrayDeque<>();
    private final ScheduledExecutorService scheduler;

    public SynFloodDetector(AtomGuardVelocity plugin, int threshold) {
        this.plugin = plugin;
        this.threshold = threshold;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "atomguard-syn-detector");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
    }

    private void tick() {
        int rate = connectionsThisSecond.getAndSet(0);
        peakRate.updateAndGet(p -> Math.max(p, rate));
        synchronized (rateHistory) {
            rateHistory.addLast(rate);
            if (rateHistory.size() > 60) rateHistory.pollFirst();
        }
        checkAndTrigger(rate);
    }

    public void recordConnection() {
        connectionsThisSecond.incrementAndGet();
    }

    private void checkAndTrigger(int rate) {
        if (rate >= threshold && !plugin.isAttackMode()) {
            plugin.setAttackMode(true);
            plugin.getAlertManager().alertAttackStarted(String.valueOf(rate));
            plugin.getLogManager().warn("SYN flood tespit edildi! Hız: " + rate + "/s - Saldırı modu aktif.");
        } else if (rate < threshold / 2 && plugin.isAttackMode()) {
            long duration = System.currentTimeMillis() - plugin.getAttackModeStartTime();
            plugin.setAttackMode(false);
            plugin.getAlertManager().alertAttackEnded(
                TimeUtils.formatDurationShort(duration),
                plugin.getStatisticsManager().get("ddos_blocked"));
        }
    }

    public int getCurrentRate() { return connectionsThisSecond.get(); }
    public int getPeakRate() { return peakRate.get(); }

    public void shutdown() { scheduler.shutdownNow(); }
}
