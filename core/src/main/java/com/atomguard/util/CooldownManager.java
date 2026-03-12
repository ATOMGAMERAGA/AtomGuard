package com.atomguard.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Generic cooldown yönetim sistemi
 * Thread-safe ve yüksek performanslı
 * System.nanoTime() kullanarak hassas zaman ölçümü yapar
 */
public class CooldownManager {

    // UUID -> (Cooldown Tipi -> Son İşlem Zamanı (nanoseconds))
    private final Cache<UUID, ConcurrentHashMap<String, Long>> cooldowns;

    public CooldownManager() {
        this.cooldowns = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

    /**
     * Cooldown kontrolü yapar
     *
     * @param uuid Oyuncu UUID
     * @param type Cooldown tipi (örn: "shear", "click", "drop")
     * @param cooldownMs Cooldown süresi (milisaniye)
     * @return Cooldown aktif mi?
     */
    public boolean isOnCooldown(@NotNull UUID uuid, @NotNull String type, long cooldownMs) {
        ConcurrentHashMap<String, Long> playerCooldowns = cooldowns.getIfPresent(uuid);
        if (playerCooldowns == null) {
            return false;
        }

        Long lastTime = playerCooldowns.get(type);
        if (lastTime == null) {
            return false;
        }

        long cooldownNanos = cooldownMs * 1_000_000L; // ms -> ns
        long currentTime = System.nanoTime();

        return (currentTime - lastTime) < cooldownNanos;
    }

    /**
     * Cooldown ayarlar
     *
     * @param uuid Oyuncu UUID
     * @param type Cooldown tipi
     */
    public void setCooldown(@NotNull UUID uuid, @NotNull String type) {
        cooldowns.get(uuid, k -> new ConcurrentHashMap<>())
                 .put(type, System.nanoTime());
    }

    /**
     * Kalan cooldown süresini döndürür (milisaniye)
     *
     * @param uuid Oyuncu UUID
     * @param type Cooldown tipi
     * @param cooldownMs Cooldown süresi
     * @return Kalan süre (ms), cooldown yoksa 0
     */
    public long getRemainingTime(@NotNull UUID uuid, @NotNull String type, long cooldownMs) {
        ConcurrentHashMap<String, Long> playerCooldowns = cooldowns.getIfPresent(uuid);
        if (playerCooldowns == null) {
            return 0;
        }

        Long lastTime = playerCooldowns.get(type);
        if (lastTime == null) {
            return 0;
        }

        long cooldownNanos = cooldownMs * 1_000_000L;
        long currentTime = System.nanoTime();
        long elapsed = currentTime - lastTime;

        if (elapsed >= cooldownNanos) {
            return 0;
        }

        return (cooldownNanos - elapsed) / 1_000_000L; // ns -> ms
    }

    /**
     * Belirli bir oyuncunun tüm cooldown'larını temizler
     *
     * @param uuid Oyuncu UUID
     */
    public void clearCooldowns(@NotNull UUID uuid) {
        cooldowns.invalidate(uuid);
    }

    /**
     * Belirli bir oyuncunun belirli tipindeki cooldown'unu temizler
     *
     * @param uuid Oyuncu UUID
     * @param type Cooldown tipi
     */
    public void clearCooldown(@NotNull UUID uuid, @NotNull String type) {
        ConcurrentHashMap<String, Long> playerCooldowns = cooldowns.getIfPresent(uuid);
        if (playerCooldowns != null) {
            playerCooldowns.remove(type);
        }
    }

    /**
     * Tüm cooldown'ları temizler
     */
    public void clearAll() {
        cooldowns.invalidateAll();
    }

    /**
     * Süresi dolmuş cooldown'ları temizler (memory optimization)
     * Bu metot periyodik olarak çağrılmalıdır
     */
    public void cleanup() {
        cooldowns.cleanUp();
    }

    /**
     * Aktif cooldown sayısını döndürür
     *
     * @return Toplam cooldown sayısı
     */
    public int getActiveCooldownCount() {
        return cooldowns.asMap().values().stream()
                .mapToInt(ConcurrentHashMap::size)
                .sum();
    }
}
