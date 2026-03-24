package com.atomguard.heuristic;

import com.atomguard.AtomGuard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
     * Removes profiles of players who are no longer online to prevent memory leaks.
     */
    public void cleanupOfflinePlayers() {
        Set<UUID> online = Bukkit.getOnlinePlayers().stream()
                .map(Player::getUniqueId)
                .collect(Collectors.toSet());
        profiles.keySet().removeIf(uuid -> !online.contains(uuid));
    }

    /**
     * Analyzes player rotation for bot-like behavior.
     * Checks for instant snaps or impossible speeds.
     */
    public void analyzeRotation(Player player, float yaw, float pitch) {
        if (player == null) return;
        if (player.hasPermission("atomguard.bypass")) return;
        HeuristicProfile profile = getProfile(player.getUniqueId());

        long now = System.currentTimeMillis();

        // Session grace period — yeni giriş sonrası 5 saniye rotation analizi atlanır
        if (profile.getSessionStartTime() > 0 && now - profile.getSessionStartTime() < 5000) {
            profile.setLastYaw(yaw);
            profile.setLastPitch(pitch);
            profile.setLastRotationTime(now);
            return;
        }

        long timeDiff = now - profile.getLastRotationTime();

        // Skip if too frequent (avoid division by zero or micro-checks)
        if (timeDiff < 1) return;

        float yawDiff = Math.abs(yaw - profile.getLastYaw());
        float pitchDiff = Math.abs(pitch - profile.getLastPitch());
        
        // Normalize Yaw (0-360 wrapping)
        if (yawDiff > 180) yawDiff = 360 - yawDiff;

        // Check 1: Impossible Rotation Speed (Snapping)
        // FP-11: Eşik yükseltildi (3.5 -> 5.0 -> 8.0) ve ardışık spike aralık kontrolü eklendi
        double speed = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff) / timeDiff; // degrees per ms

        double maxSpeed = plugin.getConfig().getDouble("heuristic.max-rotation-speed", 12.0);  // 8.0 → 12.0: yüksek DPI fare kullanıcıları
        int maxSpikes = plugin.getConfig().getInt("heuristic.max-rotation-spikes", 8);          // 5 → 8: PvP'de hızlı rotation normal
        long minSpikeIntervalMs = plugin.getConfig().getLong("heuristic.min-spike-interval-ms", 100); // yeni: spike'lar en az 100ms aralıklı olmalı

        if (speed > maxSpeed) {
            // Spike aralık kontrolü: 100ms içindeki birden fazla spike'ı tek sayma
            // Yüksek FPS'de paket bombardımanı false positive'i önler
            if (now - profile.getLastSpikeTime() >= minSpikeIntervalMs) {
                profile.incrementRotationSpikes();
                profile.setLastSpikeTime(now);
            }
            if (profile.getRotationSpikes() >= maxSpikes) {
                double oldScore = profile.getSuspicionLevel();
                profile.addSuspicion(3.0);
                double newScore = profile.getSuspicionLevel();
                
                // Trigger Event (async-only event)
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                    Bukkit.getPluginManager().callEvent(new com.atomguard.api.event.ThreatScoreChangedEvent(player, oldScore, newScore, "Rotation Spike")));
                
                plugin.getLogManager().debug("High rotation speed (Burst): " + speed + " (User: " + player.getName() + ")");
                checkSuspicionLevel(player, profile);
                profile.resetRotationSpikes();
            }
        } else {
            profile.decrementRotationSpikes();
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
        if (player.hasPermission("atomguard.bypass")) return;
        HeuristicProfile profile = getProfile(player.getUniqueId());

        long now = System.currentTimeMillis();
        long lastClick = profile.getLastClickTime();

        if (lastClick == 0) {
            profile.setLastClickTime(now);
            return;
        }

        long interval = now - lastClick;
        profile.setLastClickTime(now);

        // Lag kaynaklı paket birleşmesi — 10ms altı aralıkları filtrele
        if (interval < 10) return;

        profile.addClickSample(interval);

        Queue<Long> samples = profile.getClickIntervals();
        int minSamples = plugin.getConfig().getInt("heuristic.min-click-samples", 15);
        double minVariance = plugin.getConfig().getDouble("heuristic.min-click-variance", 10.0);
        double maxAvgInterval = plugin.getConfig().getDouble("heuristic.max-avg-click-interval", 100.0);

        if (samples.size() >= minSamples) {
            double variance = calculateVariance(samples);
            
            // Check 2: Zero Variance (Macro/Bot)
            // If variance is extremely low (e.g. < 5.0), it's likely a macro
            if (variance < minVariance && getAverage(samples) < maxAvgInterval) { // Fast clicking with no variance
                double oldScore = profile.getSuspicionLevel();
                profile.addSuspicion(8.0);
                double newScore = profile.getSuspicionLevel();
                
                // Trigger Event (async-only event)
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                    Bukkit.getPluginManager().callEvent(new com.atomguard.api.event.ThreatScoreChangedEvent(player, oldScore, newScore, "Low Click Variance")));
                
                plugin.getLogManager().debug("Low click variance: " + variance + " (User: " + player.getName() + ")");
                checkSuspicionLevel(player, profile);
            }
        }
    }

    private void checkSuspicionLevel(Player player, HeuristicProfile profile) {
        double level = profile.getSuspicionLevel();

        double kickThreshold = plugin.getConfig().getDouble("heuristic.kick-threshold", 150.0);

        if (level >= kickThreshold) {
            // Seviye 3: Kick
            String action = plugin.getConfig().getString("heuristic.action", "KICK");

            plugin.getLogManager().logExploit(player.getName(), "HeuristicEngine", "Suspicion level reached " + kickThreshold + " - Action: " + action);

            if ("KICK".equalsIgnoreCase(action)) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.kick(net.kyori.adventure.text.Component.text("§cŞüpheli davranış tespit edildi. (Heuristic)"));
                    }
                });
            }

            // Kick sonrası suspicion'ı tamamen sıfırla
            profile.reduceSuspicion(level);
        } else if (level >= 60.0) {
            // Seviye 2: Log/Warning
            if (System.currentTimeMillis() % 10000 < 50) { // Throttle debug logs
                plugin.getLogManager().warning("[Heuristic] " + player.getName() + " yüksek şüphe seviyesinde: " + String.format("%.1f", level));
            }
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
