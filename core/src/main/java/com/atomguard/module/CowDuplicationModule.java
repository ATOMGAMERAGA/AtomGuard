package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.atomguard.util.CooldownManager;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * İnek Duplikasyon Modülü
 *
 * Mooshroom kırkma exploit'ini önler.
 * CooldownManager kullanarak spam kırkma engellenir.
 *
 * Özellikler:
 * - Kırkma cooldown kontrolü
 * - Mooshroom spam kırkma önleme
 * - Oyuncu bazlı rate limiting
 * - Duplikasyon exploit önleme
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class CowDuplicationModule extends AbstractModule implements Listener {

    private CooldownManager cooldownManager;

    // Config cache
    private long shearCooldownMs;

    /**
     * CowDuplicationModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public CowDuplicationModule(@NotNull AtomGuard plugin) {
        super(plugin, "inek-duplikasyon", "Mooshroom kırkma duplikasyonu önleme");
    }

    @Override

    public void onEnable() {
        super.onEnable();

        // Cooldown manager başlat
        this.cooldownManager = new CooldownManager();

        // Config değerlerini yükle
        loadConfig();

        debug("Modül aktifleştirildi. Kırkma cooldown: " + shearCooldownMs + "ms");
    }

    @Override

    public void onDisable() {
        super.onDisable();

        // Cooldown manager'ı temizle
        if (cooldownManager != null) {
            cooldownManager.clearAll();
        }

        // Event listener'ı kaldır
        PlayerShearEntityEvent.getHandlerList().unregister(this);

        debug("Modül devre dışı bırakıldı.");
    }

    /**
     * Config değerlerini yükler
     */
    private void loadConfig() {
        this.shearCooldownMs = getConfigLong("kirkma-cooldown-ms", 500L);

        debug("Config yüklendi: cooldown=" + shearCooldownMs + "ms");
    }

    /**
     * Entity kırkma olayını dinler
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerShearEntity(PlayerShearEntityEvent event) {
        if (!isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Sadece Mooshroom için kontrol et
        if (event.getEntity().getType() != EntityType.MOOSHROOM) {
            return;
        }

        debug(player.getName() + " mooshroom kırkıyor");

        // Cooldown kontrolü
        if (cooldownManager.isOnCooldown(uuid, "shear", shearCooldownMs)) {
            incrementBlockedCount();

            long remaining = cooldownManager.getRemainingTime(uuid, "shear", shearCooldownMs);

            logExploit(player.getName(),
                String.format("Mooshroom spam kırkma tespit edildi! Kalan cooldown: %dms", remaining));

            event.setCancelled(true);

            // Oyuncuya mesaj gönder
            java.util.Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("time", String.valueOf(remaining));
            player.sendMessage(plugin.getMessageManager()
                .getMessage("cooldown-aktif", placeholders));

            debug(player.getName() + " için kırkma engellendi (cooldown)");
            return;
        }

        // Cooldown ayarla
        cooldownManager.setCooldown(uuid, "shear");
        debug(player.getName() + " için cooldown ayarlandı");
    }

    /**
     * Oyuncu cooldown'unu temizler
     */
    public void clearPlayerCooldown(@NotNull UUID uuid) {
        cooldownManager.clearCooldown(uuid, "shear");
        debug("Cooldown temizlendi: " + uuid);
    }

    /**
     * Tüm cooldown'ları temizler
     */
    public void clearAllCooldowns() {
        cooldownManager.clearAll();
        debug("Tüm cooldown'lar temizlendi");
    }

    /**
     * Cooldown durumunu kontrol eder
     */
    public boolean isOnCooldown(@NotNull UUID uuid) {
        return cooldownManager.isOnCooldown(uuid, "shear", shearCooldownMs);
    }

    /**
     * Kalan cooldown süresini döndürür
     */
    public long getRemainingCooldown(@NotNull UUID uuid) {
        return cooldownManager.getRemainingTime(uuid, "shear", shearCooldownMs);
    }

    /**
     * Modül istatistiklerini döndürür
     */
    public String getStatistics() {
        return String.format("Engellenen duplikasyon: %d, Aktif cooldown: %d",
            getBlockedCount(),
            cooldownManager.getActiveCooldownCount());
    }
}
