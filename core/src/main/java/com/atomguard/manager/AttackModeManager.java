package com.atomguard.manager;

import com.atomguard.AtomGuard;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Monitors connection rates and triggers "Attack Mode" (Lockdown).
 * v2.3: Verified IP tracking, automatic actions during attack.
 */
public class AttackModeManager {

    private final AtomGuard plugin;

    private final AtomicInteger connectionCounter = new AtomicInteger(0);
    private long lastReset = System.currentTimeMillis();

    private volatile boolean attackMode = false;
    private long attackModeStartTime = 0;
    private volatile int peakRate = 0;
    private volatile long blockedDuringAttack = 0;

    private final int threshold;
    private final int durationSeconds;

    // Verified IPs (players that successfully joined before)
    private final Set<String> verifiedIps = ConcurrentHashMap.newKeySet();

    // Action config
    private boolean actionBlockUnverified;
    private boolean actionTightLimits;
    private double tightLimitMultiplier;
    private boolean actionAutoEnableModules;
    private List<String> autoModules;
    private boolean actionWhitelistOnly;
    private boolean actionDiscordNotify;

    public AttackModeManager(AtomGuard plugin) {
        this.plugin = plugin;
        this.threshold = plugin.getConfig().getInt("attack-mode.threshold", 10);
        this.durationSeconds = plugin.getConfig().getInt("attack-mode.duration-seconds", 60);
        loadActionConfig();
    }

    private void loadActionConfig() {
        this.actionBlockUnverified = plugin.getConfig().getBoolean("attack-mode.aksiyonlar.dogrulanmamis-ip-engelle", true);
        this.actionTightLimits = plugin.getConfig().getBoolean("attack-mode.aksiyonlar.siki-limitler", true);
        this.tightLimitMultiplier = plugin.getConfig().getDouble("attack-mode.aksiyonlar.siki-limit-carpani", 0.5);
        this.actionAutoEnableModules = plugin.getConfig().getBoolean("attack-mode.aksiyonlar.otomatik-modul-etkinlestir", true);
        this.autoModules = plugin.getConfig().getStringList("attack-mode.aksiyonlar.otomatik-moduller");
        this.actionWhitelistOnly = plugin.getConfig().getBoolean("attack-mode.aksiyonlar.sadece-beyaz-liste", false);
        this.actionDiscordNotify = plugin.getConfig().getBoolean("attack-mode.aksiyonlar.discord-bildirim", true);
    }

    /**
     * Records a connection attempt and checks for attack.
     */
    public void recordConnection() {
        long now = System.currentTimeMillis();

        // Reset counter every second
        if (now - lastReset >= 1000) {
            connectionCounter.set(0);
            lastReset = now;
        }

        int currentRate = connectionCounter.incrementAndGet();

        // Track peak during attack
        if (attackMode && currentRate > peakRate) {
            peakRate = currentRate;
        }

        if (currentRate >= threshold) {
            // Trigger DDoS Event (every time threshold reached, even if already in attack mode)
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.getServer().getPluginManager().callEvent(new com.atomguard.api.event.DDoSDetectedEvent(currentRate, threshold, attackMode));
            });
            
            if (!attackMode) {
                activateAttackMode(currentRate);
            }
        }
    }

    private void activateAttackMode(int triggerRate) {
        this.attackMode = true;
        this.attackModeStartTime = System.currentTimeMillis();
        this.peakRate = triggerRate;
        this.blockedDuringAttack = 0;

        plugin.getLogger().warning("!!! ATTACK MODE ACTIVATED !!! Connection rate: " + triggerRate + "/sec");
        plugin.getLogManager().warning("System entered ATTACK MODE due to high connection rate.");

        // Trigger API Event
        plugin.getServer().getPluginManager().callEvent(new com.atomguard.api.event.AttackModeToggleEvent(true, triggerRate));

        // Execute actions
        executeAttackActions();

        // Discord notification
        if (actionDiscordNotify && plugin.getDiscordWebhookManager() != null) {
            plugin.getDiscordWebhookManager().notifyAttackMode(true, triggerRate);
        }

        // Redis sync
        if (plugin.getRedisManager() != null) {
            plugin.getRedisManager().publish("ATTACK_MODE", "true");
        }
    }

    /**
     * Forcing attack mode from external source (e.g. Redis)
     */
    public void forceEnable() {
        if (!attackMode) {
            this.attackMode = true;
            this.attackModeStartTime = System.currentTimeMillis();
            this.peakRate = threshold;
            this.blockedDuringAttack = 0;
            executeAttackActions();
            // Trigger API Event
            plugin.getServer().getPluginManager().callEvent(new com.atomguard.api.event.AttackModeToggleEvent(true, threshold));
            plugin.getLogger().warning("!!! ATTACK MODE ACTIVATED (Synced via Redis) !!!");
        }
    }

    /**
     * Forcing attack mode deactivation from external source (e.g. Redis)
     */
    public void forceDisable() {
        if (attackMode) {
            this.attackMode = false;
            // Trigger API Event
            plugin.getServer().getPluginManager().callEvent(new com.atomguard.api.event.AttackModeToggleEvent(false, peakRate));
            plugin.getLogger().info("Attack mode deactivated (Synced via Redis).");
        }
    }

    private void executeAttackActions() {
        // Auto-enable modules
        if (actionAutoEnableModules && autoModules != null) {
            for (String moduleName : autoModules) {
                if (!plugin.getModuleManager().isModuleEnabled(moduleName)) {
                    plugin.getModuleManager().enableModule(moduleName);
                    plugin.getLogger().info("[AttackMode] Modul etkinlestirildi: " + moduleName);
                }
            }
        }
    }

    /**
     * Checks if attack mode should be deactivated.
     */
    public void update() {
        if (attackMode) {
            long now = System.currentTimeMillis();
            if (now - attackModeStartTime >= (durationSeconds * 1000L)) {
                deactivateAttackMode();
            }
        }
    }

    private void deactivateAttackMode() {
        long endTime = System.currentTimeMillis();

        // Record attack in statistics
        if (plugin.getStatisticsManager() != null) {
            plugin.getStatisticsManager().recordAttack(
                    attackModeStartTime, endTime, peakRate, blockedDuringAttack);
        }

        this.attackMode = false;

        // Trigger API Event
        plugin.getServer().getPluginManager().callEvent(new com.atomguard.api.event.AttackModeToggleEvent(false, peakRate));

        plugin.getLogger().info("Attack mode deactivated. Peak rate: " + peakRate
                + "/sec, Blocked: " + blockedDuringAttack);
        plugin.getLogManager().info("Attack mode deactivated. Duration: "
                + ((endTime - attackModeStartTime) / 1000) + "s");

        // Discord notification
        if (actionDiscordNotify && plugin.getDiscordWebhookManager() != null) {
            plugin.getDiscordWebhookManager().notifyAttackMode(false, 0);
        }

        // Redis sync
        if (plugin.getRedisManager() != null) {
            plugin.getRedisManager().publish("ATTACK_MODE", "false");
        }
    }

    // ═══════════════════════════════════════
    // Verified IP Management
    // ═══════════════════════════════════════

    /**
     * Records an IP as verified (player successfully joined).
     */
    public void recordVerifiedIp(String ip) {
        if (ip != null) {
            verifiedIps.add(ip);
        }
    }

    /**
     * Checks if a connection should be blocked during attack mode.
     * Returns true if the connection should be BLOCKED.
     */
    public boolean shouldBlockConnection(String ip) {
        if (!attackMode) return false;

        // Check persistent cache first
        if (plugin.getVerifiedPlayerCache() != null && plugin.getVerifiedPlayerCache().isIpVerified(ip)) {
            return false;
        }

        // Whitelist-only mode: block ALL non-verified
        if (actionWhitelistOnly) {
            if (!verifiedIps.contains(ip)) {
                blockedDuringAttack++;
                return true;
            }
        }

        // Block unverified IPs
        if (actionBlockUnverified && !verifiedIps.contains(ip)) {
            blockedDuringAttack++;
            return true;
        }

        return false;
    }

    /**
     * Returns the tight limit multiplier (used by modules during attack).
     * Returns 1.0 when not in attack mode or tight limits disabled.
     */
    public double getLimitMultiplier() {
        if (attackMode && actionTightLimits) {
            return tightLimitMultiplier;
        }
        return 1.0;
    }

    public boolean isVerifiedIp(String ip) {
        return verifiedIps.contains(ip);
    }

    public int getVerifiedIpCount() {
        return verifiedIps.size();
    }

    public boolean isAttackMode() {
        return attackMode;
    }

    public int getCurrentRate() {
        return connectionCounter.get();
    }

    public int getPeakRate() {
        return peakRate;
    }

    public long getBlockedDuringAttack() {
        return blockedDuringAttack;
    }

    public long getAttackModeStartTime() {
        return attackModeStartTime;
    }
}
