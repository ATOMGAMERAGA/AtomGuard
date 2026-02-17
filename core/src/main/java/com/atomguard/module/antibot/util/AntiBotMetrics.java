package com.atomguard.module.antibot.util;

import java.util.concurrent.atomic.AtomicInteger;

public class AntiBotMetrics {
    private final AtomicInteger totalDetections = new AtomicInteger(0);
    private final AtomicInteger totalBlacklisted = new AtomicInteger(0);

    public void recordDetection() {
        totalDetections.incrementAndGet();
    }

    public void recordBlacklist() {
        totalBlacklisted.incrementAndGet();
    }

    public int getTotalDetections() {
        return totalDetections.get();
    }

    public int getTotalBlacklisted() {
        return totalBlacklisted.get();
    }
}
