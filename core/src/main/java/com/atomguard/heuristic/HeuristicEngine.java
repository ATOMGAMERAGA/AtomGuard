package com.atomguard.heuristic;

import com.atomguard.AtomGuard;
import com.atomguard.data.PlayerData;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core engine for heuristic analysis and machine learning-assisted behavior tracking.
 */
public class HeuristicEngine {

    private final AtomGuard plugin;
    private final ConcurrentHashMap<UUID, HeuristicProfile> profiles;

    public HeuristicEngine(AtomGuard plugin) {
        this.plugin = plugin;
        this.profiles = new ConcurrentHashMap<>();
    }

    public HeuristicProfile getProfile(UUID uuid) {
        return profiles.computeIfAbsent(uuid, k -> new HeuristicProfile(k));
    }

    public void removeProfile(UUID uuid) {
        profiles.remove(uuid);
    }

    /**
     * Analyzes player rotation for bot-like behavior.
     * Checks for instant snaps or impossible speeds.
     */
    public void analyzeRotation(Player player, float yaw, float pitch) {
        if (player == null) return;
        HeuristicProfile profile = getProfile(player.getUniqueId());

        long now = System.currentTimeMillis();
        long timeDiff = now - profile.getLastRotationTime();
        
        // Skip if too frequent (avoid division by zero or micro-checks)
        if (timeDiff < 1) return;

        float yawDiff = Math.abs(yaw - profile.getLastYaw());
        float pitchDiff = Math.abs(pitch - profile.getLastPitch());
        
        // Normalize Yaw (0-360 wrapping)
        if (yawDiff > 180) yawDiff = 360 - yawDiff;

        // Check 1: Impossible Rotation Speed (Snapping)
        // FP-11: Eşik yükseltildi (3.5 -> 5.0) ve ardışık spike kontrolü eklendi
        double speed = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff) / timeDiff; // degrees per ms
        
        if (speed > 5.0) {
            profile.incrementRotationSpikes();
            if (profile.getRotationSpikes() >= 3) {
                profile.addSuspicion(5.0); // 10.0'dan 5.0'a düşürüldü
                plugin.getLogManager().debug("High rotation speed (Burst): " + speed + " (User: " + player.getName() + ")");
                checkSuspicionLevel(player, profile);
                profile.resetRotationSpikes();
            }
        } else {
            profile.resetRotationSpikes();
        }

        profile.setLastYaw(yaw);
        profile.setLastPitch(pitch);
        profile.setLastRotationTime(now);
    }

    /**
     * Analyzes click consistency.
     * Bots often have perfect 50ms delays or perfectly randomized patterns that fail statistical tests.
     */
    public void analyzeClick(Player player) {
        if (player == null) return;
        HeuristicProfile profile = getProfile(player.getUniqueId());

        long now = System.currentTimeMillis();
        long lastClick = profile.getLastClickTime();
        
        if (lastClick == 0) {
            profile.setLastClickTime(now);
            return;
        }

        long interval = now - lastClick;
        profile.setLastClickTime(now);
        profile.addClickSample(interval);

        Queue<Long> samples = profile.getClickIntervals();
        if (samples.size() >= 10) {
            double variance = calculateVariance(samples);
            
            // Check 2: Zero Variance (Macro/Bot)
            // If variance is extremely low (e.g. < 5.0), it's likely a macro
            if (variance < 10.0 && getAverage(samples) < 100) { // Fast clicking with no variance
                profile.addSuspicion(15.0);
                plugin.getLogManager().debug("Low click variance: " + variance + " (User: " + player.getName() + ")");
                checkSuspicionLevel(player, profile);
            }
        }
    }

    private void checkSuspicionLevel(Player player, HeuristicProfile profile) {
        if (profile.getSuspicionLevel() >= 100.0) {
            // Trigger Lockdown or Kick
            plugin.getLogManager().logExploit(player.getName(), "HeuristicEngine", "Suspicion level reached 100%");
            // Reset slightly to avoid spam
             profile.reduceSuspicion(50.0);
             
             // Action can be configured here (Kick, Captcha, etc.)
             // For now, we log.
        }
    }

    private double calculateVariance(Queue<Long> samples) {
        if (samples.isEmpty()) return 0;
        double mean = getAverage(samples);
        double temp = 0;
        for (double a : samples) {
            temp += (a - mean) * (a - mean);
        }
        return temp / samples.size();
    }
    
    private double getAverage(Queue<Long> samples) {
        if (samples.isEmpty()) return 0;
        double sum = 0;
        for (long val : samples) {
            sum += val;
        }
        return sum / samples.size();
    }
}
