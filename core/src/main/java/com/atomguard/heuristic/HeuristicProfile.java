package com.atomguard.heuristic;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stores heuristic data for a single player.
 */
public class HeuristicProfile {

    private final UUID uuid;
    
    // Rotation Analysis
    private float lastYaw;
    private float lastPitch;
    private long lastRotationTime;
    
    // Click Consistency (CPS & Intervals)
    private final Queue<Long> clickIntervals;
    private long lastClickTime;
    private static final int MAX_CLICK_SAMPLES = 20;

    // Suspicion System
    private double suspicionLevel; // 0.0 to 100.0
    private final AtomicInteger violationCount;
    private int rotationSpikes = 0;

    public HeuristicProfile(UUID uuid) {
        this.uuid = uuid;
        this.clickIntervals = new LinkedList<>();
        this.lastRotationTime = System.currentTimeMillis();
        this.violationCount = new AtomicInteger(0);
        this.suspicionLevel = 0.0;
    }

    public void incrementRotationSpikes() {
        this.rotationSpikes++;
    }

    public void resetRotationSpikes() {
        this.rotationSpikes = 0;
    }

    public int getRotationSpikes() {
        return rotationSpikes;
    }

    public UUID getUuid() {
        return uuid;
    }

    public float getLastYaw() {
        return lastYaw;
    }

    public void setLastYaw(float lastYaw) {
        this.lastYaw = lastYaw;
    }

    public float getLastPitch() {
        return lastPitch;
    }

    public void setLastPitch(float lastPitch) {
        this.lastPitch = lastPitch;
    }

    public long getLastRotationTime() {
        return lastRotationTime;
    }

    public void setLastRotationTime(long lastRotationTime) {
        this.lastRotationTime = lastRotationTime;
    }

    public void addClickSample(long interval) {
        if (clickIntervals.size() >= MAX_CLICK_SAMPLES) {
            clickIntervals.poll();
        }
        clickIntervals.add(interval);
    }

    public Queue<Long> getClickIntervals() {
        return clickIntervals;
    }

    public long getLastClickTime() {
        return lastClickTime;
    }

    public void setLastClickTime(long lastClickTime) {
        this.lastClickTime = lastClickTime;
    }

    public double getSuspicionLevel() {
        return suspicionLevel;
    }

    public void addSuspicion(double amount) {
        this.suspicionLevel = Math.min(100.0, this.suspicionLevel + amount);
    }
    
    public void reduceSuspicion(double amount) {
        this.suspicionLevel = Math.max(0.0, this.suspicionLevel - amount);
    }

    public int getViolationCount() {
        return violationCount.get();
    }
    
    public void incrementViolation() {
        violationCount.incrementAndGet();
    }
}
