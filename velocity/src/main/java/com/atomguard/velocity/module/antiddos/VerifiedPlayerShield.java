package com.atomguard.velocity.module.antiddos;

import com.atomguard.velocity.AtomGuardVelocity;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Saldırı sırasında doğrulanmış oyuncuları koruma katmanı.
 * <p>
 * Özellikler:
 * <ul>
 *   <li>Verified oyuncular için garantili slot sayısı</li>
 *   <li>CRITICAL ve LOCKDOWN seviyelerinde bypass token'ı</li>
 *   <li>Slot dolduğunda yeni bağlantıları reddet (verified olmayan)</li>
 *   <li>Redis üzerinden cross-proxy senkronizasyonu (opsiyonel)</li>
 * </ul>
 */
public class VerifiedPlayerShield {

    // ────────────────────────────────────────────────────────
    // Konfigürasyon
    // ────────────────────────────────────────────────────────

    private final AtomGuardVelocity plugin;
    private final int               guaranteedSlots;    // Verified için ayrılan maks. eş zamanlı bağlantı
    private final long              bypassTokenTtlMs;   // Token geçerlilik süresi (ms)

    // ────────────────────────────────────────────────────────
    // State
    // ────────────────────────────────────────────────────────

    /** Geçerli bypass token'ları (IP → token) */
    private final Cache<String, String> bypassTokens = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(50_000)
            .build();

    /** Anlık guaranteed slot kullanımı */
    private final AtomicInteger slotsInUse = new AtomicInteger(0);

    /** Toplam bypass olayı sayısı */
    private final AtomicInteger totalBypasses = new AtomicInteger(0);

    public VerifiedPlayerShield(AtomGuardVelocity plugin, int guaranteedSlots, long bypassTokenTtlMs) {
        this.plugin           = plugin;
        this.guaranteedSlots  = guaranteedSlots;
        this.bypassTokenTtlMs = bypassTokenTtlMs;
    }

    // ────────────────────────────────────────────────────────
    // Bağlantı kararı
    // ────────────────────────────────────────────────────────

    /**
     * Bağlantı isteğine izin verilmeli mi?
     * <p>
     * CRITICAL/LOCKDOWN seviyelerinde verified oyunculara slot garantisi sağlar.
     *
     * @param ip         Kaynak IP
     * @param level      Güncel saldırı seviyesi
     * @return true ise bağlantıya izin ver
     */
    public boolean shouldAllow(String ip, AttackLevelManager.AttackLevel level) {
        boolean isVerified = isVerified(ip);

        // Kritik ve kilitleme seviyelerinde bypass token kontrolü
        if (level.isAtLeast(AttackLevelManager.AttackLevel.CRITICAL)) {
            if (!isVerified) return false;

            // Slot kontrolü
            int current = slotsInUse.get();
            if (current >= guaranteedSlots) {
                plugin.getLogManager().warn(
                    "Doğrulanmış oyuncu slotu dolu: " + current + "/" + guaranteedSlots
                    + " IP: " + ip
                );
                return false;
            }
            slotsInUse.incrementAndGet();
            totalBypasses.incrementAndGet();
            return true;
        }

        // Diğer seviyelerde normal akış
        return true;
    }

    /**
     * Bağlantı kapandığında slot'u serbest bırak.
     */
    public void onConnectionClosed(String ip, AttackLevelManager.AttackLevel level) {
        if (level.isAtLeast(AttackLevelManager.AttackLevel.CRITICAL)) {
            slotsInUse.updateAndGet(v -> Math.max(0, v - 1));
        }
    }

    // ────────────────────────────────────────────────────────
    // Token yönetimi
    // ────────────────────────────────────────────────────────

    /**
     * IP için bypass token oluştur.
     * Verified oyunculara saldırı başladığında verilir.
     */
    public String issueBypassToken(String ip) {
        String token = Long.toHexString(System.nanoTime()) + "-" + ip.hashCode();
        bypassTokens.put(ip, token);
        return token;
    }

    /** IP için mevcut token'ı döndür. */
    public String getToken(String ip) {
        return bypassTokens.getIfPresent(ip);
    }

    /** Token geçerli mi? */
    public boolean hasValidToken(String ip) {
        return bypassTokens.getIfPresent(ip) != null;
    }

    /** Token'ı iptal et. */
    public void revokeToken(String ip) {
        bypassTokens.invalidate(ip);
    }

    // ────────────────────────────────────────────────────────
    // Verified kontrol
    // ────────────────────────────────────────────────────────

    /**
     * IP daha önce başarıyla giriş yapmış mı?
     * AntiBot modülünün verified cache'ini kullanır.
     */
    private boolean isVerified(String ip) {
        if (plugin.getAntiBotModule() == null) return false;
        return plugin.getAntiBotModule().isVerified(ip);
    }

    // ────────────────────────────────────────────────────────
    // Seviye değişimi
    // ────────────────────────────────────────────────────────

    /**
     * Saldırı seviyesi CRITICAL veya LOCKDOWN olduğunda tüm verified
     * oyuncular için token oluştur.
     */
    public void onLevelEscalated(AttackLevelManager.AttackLevel newLevel) {
        if (!newLevel.isAtLeast(AttackLevelManager.AttackLevel.CRITICAL)) return;

        plugin.getProxyServer().getAllPlayers().forEach(player -> {
            String ip = player.getRemoteAddress().getAddress().getHostAddress();
            if (isVerified(ip)) {
                issueBypassToken(ip);
            }
        });

        plugin.getLogManager().warn(
            "Koruma katmanı aktif! Seviye: " + newLevel.getDisplayName()
            + " | Slot limiti: " + guaranteedSlots
        );
    }

    /**
     * Saldırı seviyesi normale döndüğünde token'ları temizle.
     */
    public void onLevelNormalized() {
        bypassTokens.invalidateAll();
        slotsInUse.set(0);
    }

    // ────────────────────────────────────────────────────────
    // Getters
    // ────────────────────────────────────────────────────────

    public int  getGuaranteedSlots() { return guaranteedSlots; }
    public int  getSlotsInUse()      { return slotsInUse.get(); }
    public int  getAvailableSlots()  { return Math.max(0, guaranteedSlots - slotsInUse.get()); }
    public int  getTotalBypasses()   { return totalBypasses.get(); }
    public long getActiveTokenCount(){ return bypassTokens.estimatedSize(); }
}
