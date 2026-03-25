package com.atomguard.velocity.module.antiddos;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.util.TimeUtils;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 5 kademeli saldırı seviye yöneticisi.
 * <p>
 * Seviyeler (CPS — bağlantı/saniye eşiğine göre):
 * <ul>
 *   <li>NONE      — normal trafik</li>
 *   <li>ELEVATED  — eşik × 1.5 → limitler %30 düşer</li>
 *   <li>HIGH      — eşik × 2   → limitler %60 düşer, yeni hesaplar engellenir</li>
 *   <li>CRITICAL  — eşik × 3   → sadece verified + captcha</li>
 *   <li>LOCKDOWN  — eşik × 5   → tüm yeni bağlantılar reddedilir</li>
 * </ul>
 *
 * <p>Hysteresis: seviye yükseltme için {@code hysteresisUpMs} kadar tutarlı
 * yüksek hız, düşürme için {@code hysteresisDownMs} kadar tutarlı düşük hız gerekir.
 *
 * <p>Geriye uyumluluk: ELEVATED ve üstü → {@code plugin.setAttackMode(true)},
 * NONE → {@code plugin.setAttackMode(false)}.
 */
public class AttackLevelManager {

    // ────────────────────────────────────────────────────────
    // Seviye tanımları
    // ────────────────────────────────────────────────────────

    public enum AttackLevel {
        NONE("Normal", 0, 1.0),
        ELEVATED("Yükseltilmiş", 1, 2.0),
        HIGH("Yüksek", 2, 3.0),
        CRITICAL("Kritik", 3, 5.0),
        LOCKDOWN("Kilitleme", 4, 8.0);

        private final String displayName;
        private final int ordinal;
        private final double cpsMultiplier;

        AttackLevel(String displayName, int ordinal, double cpsMultiplier) {
            this.displayName   = displayName;
            this.ordinal       = ordinal;
            this.cpsMultiplier = cpsMultiplier;
        }

        public String getDisplayName()  { return displayName; }
        public int    getOrdinal()      { return ordinal; }
        public double getCpsMultiplier(){ return cpsMultiplier; }

        public boolean isAtLeast(AttackLevel other) {
            return this.ordinal >= other.ordinal;
        }

        public boolean isAbove(AttackLevel other) {
            return this.ordinal > other.ordinal;
        }
    }

    // ────────────────────────────────────────────────────────
    // State
    // ────────────────────────────────────────────────────────

    private final AtomGuardVelocity plugin;
    private final int               baseCpsThreshold;
    private final long              hysteresisUpMs;
    private final long              hysteresisDownMs;

    private final AtomicReference<AttackLevel> currentLevel   = new AtomicReference<>(AttackLevel.NONE);
    private final AtomicReference<AttackLevel> pendingLevel   = new AtomicReference<>(AttackLevel.NONE);
    private final AtomicLong                   pendingLevelSince = new AtomicLong(0);
    private final AtomicLong                   levelChangedAt    = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong                   blockedCount      = new AtomicLong(0);

    /**
     * @param plugin           AtomGuard plugin instance
     * @param baseCpsThreshold NONE → ELEVATED geçiş CPS eşiği
     * @param hysteresisUpMs   Seviye yükseltme için gereken tutarlı süre (ms)
     * @param hysteresisDownMs Seviye düşürme için gereken tutarlı süre (ms)
     */
    public AttackLevelManager(AtomGuardVelocity plugin, int baseCpsThreshold,
                              long hysteresisUpMs, long hysteresisDownMs) {
        this.plugin            = plugin;
        this.baseCpsThreshold  = baseCpsThreshold;
        this.hysteresisUpMs    = hysteresisUpMs;
        this.hysteresisDownMs  = hysteresisDownMs;
    }

    // ────────────────────────────────────────────────────────
    // Güncelleme
    // ────────────────────────────────────────────────────────

    /**
     * Güncel CPS değerine göre seviyeyi güncelle.
     * Her tick'te (genellikle 1 saniyede bir) çağrılır.
     */
    public void update(int currentCps) {
        AttackLevel target  = calculateTargetLevel(currentCps);
        AttackLevel current = currentLevel.get();
        AttackLevel pending = pendingLevel.get();
        long now = System.currentTimeMillis();

        if (target != pending) {
            pendingLevel.set(target);
            pendingLevelSince.set(now);
            return;
        }

        long elapsed = now - pendingLevelSince.get();
        boolean isUpward   = target.ordinal > current.ordinal;
        boolean isDownward  = target.ordinal < current.ordinal;

        boolean shouldChange =
                (isUpward   && elapsed >= hysteresisUpMs)   ||
                (isDownward && elapsed >= hysteresisDownMs);

        if (shouldChange && target != current) {
            applyLevelChange(current, target);
        }
    }

    private AttackLevel calculateTargetLevel(int cps) {
        if (cps >= baseCpsThreshold * 8)   return AttackLevel.LOCKDOWN;
        if (cps >= baseCpsThreshold * 5)   return AttackLevel.CRITICAL;
        if (cps >= baseCpsThreshold * 3)   return AttackLevel.HIGH;
        if (cps >= baseCpsThreshold * 2)   return AttackLevel.ELEVATED;
        return AttackLevel.NONE;
    }

    private void applyLevelChange(AttackLevel from, AttackLevel to) {
        currentLevel.set(to);
        levelChangedAt.set(System.currentTimeMillis());

        plugin.getLogManager().warn(
            "Saldırı seviyesi değişti: " + from.getDisplayName() + " → " + to.getDisplayName()
        );

        // Attack mode geriye uyumluluğu
        boolean shouldBeAttackMode = to.isAtLeast(AttackLevel.ELEVATED);
        if (shouldBeAttackMode != plugin.isAttackMode()) {
            plugin.setAttackMode(shouldBeAttackMode);
        }

        if (to.ordinal > from.ordinal) {
            // Seviye yükseldi
            plugin.getAlertManager().sendAlert(
                "<red>[AtomGuard] ⚠ Saldırı seviyesi: <bold>" + to.getDisplayName()
                + "</bold> (CPS eşik ×" + to.getCpsMultiplier() + ")</red>"
            );
            plugin.getAlertManager().sendDiscordAlert(
                "🚨 Saldırı seviyesi: **" + to.getDisplayName()
                + "** (CPS eşik ×" + to.getCpsMultiplier() + ")"
            );
        } else if (to == AttackLevel.NONE) {
            // Saldırı tamamen sona erdi
            long duration = System.currentTimeMillis() - plugin.getAttackModeStartTime();
            plugin.getAlertManager().alertAttackEnded(
                TimeUtils.formatDurationShort(duration),
                plugin.getStatisticsManager().get("ddos_blocked"),
                0,
                null
            );
        }

        plugin.getStatisticsManager().increment("ddos_level_changes");
    }

    // ────────────────────────────────────────────────────────
    // Kontrol API
    // ────────────────────────────────────────────────────────

    /**
     * Belirtilen IP'nin bağlantısına izin ver ya da reddet.
     * Seviyeye özgü kısıtlamalar uygulanır.
     */
    public boolean shouldAllowConnection(String ip, boolean isVerified) {
        return switch (currentLevel.get()) {
            case NONE, ELEVATED -> true;
            case HIGH      -> isVerified || deterministicAllow(ip, 40);
            case CRITICAL  -> isVerified;
            case LOCKDOWN  -> false; // VerifiedPlayerShield ayrı slot yönetir
        };
    }

    /**
     * Hash-tabanlı deterministik izin: IP'lerin {@code percentAllowed}%'ini geçirir.
     * Aynı IP her zaman aynı kararı alır (tutarlı davranış).
     */
    private boolean deterministicAllow(String ip, int percentAllowed) {
        return Math.abs(ip.hashCode()) % 100 < percentAllowed;
    }

    // ────────────────────────────────────────────────────────
    // Getters
    // ────────────────────────────────────────────────────────

    public AttackLevel getCurrentLevel()      { return currentLevel.get(); }
    public boolean isAtLeast(AttackLevel l)   { return currentLevel.get().isAtLeast(l); }
    public long getLevelAge()                  { return System.currentTimeMillis() - levelChangedAt.get(); }
    public void incrementBlocked()             { blockedCount.incrementAndGet(); }
    public long getBlockedCount()              { return blockedCount.get(); }
}
