package com.atomguard.velocity.module.antiddos;

import com.atomguard.velocity.AtomGuardVelocity;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

/**
 * Adaptif throttling motoru.
 * <p>
 * AttackLevelManager ile entegre çalışır; saldırı seviyesine göre
 * dinamik bağlantı limitleri uygular.
 * <p>
 * Memory-leak koruması: Caffeine cache (5 dakika TTL) ile sağlanır.
 * Eski ConcurrentHashMap+AtomicInteger tabanlı yapı değiştirildi.
 */
public class SmartThrottleEngine {

    // ThrottleMode geriye uyumluluk için korunuyor
    public enum ThrottleMode { NORMAL, CAREFUL, AGGRESSIVE, LOCKDOWN }

    private final AtomGuardVelocity  plugin;
    private final int normalLimit;
    private final int carefulLimit;
    private final int aggressiveLimit;
    private final int lockdownLimit;

    /** Sliding window süresi: 60 saniye */
    private static final long WINDOW_MS = 60_000L;

    /** IP → bağlantı zaman damgaları (sliding window, 5 dakika erişim-sonrası TTL) */
    private final Cache<String, ConcurrentLinkedDeque<Long>> connectionWindows = Caffeine.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(500_000)
            .build();

    /** AttackLevelManager bağlantısı (opsiyonel — null ise eski davranış) */
    private AttackLevelManager levelManager;

    public SmartThrottleEngine(AtomGuardVelocity plugin, int normalLimit,
                                int carefulLimit, int aggressiveLimit, int lockdownLimit) {
        this.plugin           = plugin;
        this.normalLimit      = normalLimit;
        this.carefulLimit     = carefulLimit;
        this.aggressiveLimit  = aggressiveLimit;
        this.lockdownLimit    = lockdownLimit;
    }

    /**
     * AttackLevelManager'ı bağla.
     * Bu metod çağrılırsa level-aware throttling aktif olur.
     */
    public void setLevelManager(AttackLevelManager levelManager) {
        this.levelManager = levelManager;
    }

    /**
     * Bu IP'den yeni bağlantıya izin ver ya da reddet.
     *
     * @param ip         Kaynak IP
     * @param isVerified Oyuncu doğrulanmış mı?
     * @return true ise bağlantıya izin ver
     */
    public boolean shouldAllow(String ip, boolean isVerified) {
        int limit;
        if (levelManager != null) {
            limit = getLimitForLevel(levelManager.getCurrentLevel(), isVerified);
        } else {
            ThrottleMode mode = getCurrentThrottleMode();
            limit = switch (mode) {
                case NORMAL     -> normalLimit;
                case CAREFUL    -> carefulLimit;
                case AGGRESSIVE -> isVerified ? aggressiveLimit : aggressiveLimit / 2;
                case LOCKDOWN   -> isVerified ? lockdownLimit : 1; // 0→1: saldırı sırasında bile 1 deneme hakkı
            };
        }
        if (limit <= 0) return isVerified;

        long now = System.currentTimeMillis();
        ConcurrentLinkedDeque<Long> window =
                connectionWindows.get(ip, k -> new ConcurrentLinkedDeque<>());

        // Pencere dışındaki eski zaman damgalarını temizle, ardından kontrol et
        synchronized (window) {
            while (!window.isEmpty() && (now - window.peekFirst()) > WINDOW_MS) {
                window.pollFirst();
            }
            if (window.size() >= limit) return false;
            window.addLast(now);
        }
        return true;
    }

    /**
     * AttackLevel'e göre limit belirle.
     */
    private int getLimitForLevel(AttackLevelManager.AttackLevel level, boolean isVerified) {
        return switch (level) {
            case NONE     -> normalLimit;
            case ELEVATED -> carefulLimit;
            case HIGH     -> isVerified ? carefulLimit : aggressiveLimit;
            case CRITICAL -> isVerified ? aggressiveLimit : Math.max(1, aggressiveLimit / 2);
            case LOCKDOWN -> isVerified ? lockdownLimit : 0;
        };
    }

    /**
     * Güncel ThrottleMode'u döndür (geriye uyumluluk için).
     */
    public ThrottleMode getCurrentMode() {
        return getCurrentThrottleMode();
    }

    private ThrottleMode getCurrentThrottleMode() {
        if (levelManager == null) return ThrottleMode.NORMAL;
        return switch (levelManager.getCurrentLevel()) {
            case NONE     -> ThrottleMode.NORMAL;
            case ELEVATED -> ThrottleMode.CAREFUL;
            case HIGH     -> ThrottleMode.AGGRESSIVE;
            case CRITICAL, LOCKDOWN -> ThrottleMode.LOCKDOWN;
        };
    }

    /**
     * Eski scheduler uyumluluğu için korunuyor.
     * AttackLevelManager varsa bu metod gereksiz; hysteresis orada yönetiliyor.
     */
    public void relax() {
        // AttackLevelManager varsa relax işlemi orada yapılıyor
        if (levelManager != null) return;
        // Eski basit davranış
    }

    /**
     * Periyodik temizlik (Caffeine otomatik temizler; compatibility için).
     */
    public void cleanup() {
        connectionWindows.cleanUp();
    }

    public String getModeDisplayName() {
        return switch (getCurrentThrottleMode()) {
            case NORMAL     -> "Normal";
            case CAREFUL    -> "Dikkatli";
            case AGGRESSIVE -> "Agresif";
            case LOCKDOWN   -> "Kilitleme";
        };
    }
}
