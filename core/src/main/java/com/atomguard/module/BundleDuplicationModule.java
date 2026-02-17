package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.atomguard.util.CooldownManager;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Bundle Duplikasyon Modülü
 *
 * Bundle item ile duplikasyon exploit'ini önler.
 * WrapperPlayClientClickWindow ile bundle tıklama/bırakma cooldown kontrolü yapar.
 *
 * Özellikler:
 * - Tıklama cooldown kontrolü
 * - Bırakma cooldown kontrolü
 * - Bundle item detection
 * - CooldownManager kullanımı
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class BundleDuplicationModule extends AbstractModule {

    private PacketListenerAbstract listener;
    private CooldownManager cooldownManager;

    // Config cache
    private long clickCooldownMs;
    private long dropCooldownMs;

    /**
     * BundleDuplicationModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public BundleDuplicationModule(@NotNull AtomGuard plugin) {
        super(plugin, "bundle-duplikasyon", "Bundle duplikasyonu önleme");
    }

    @Override

    public void onEnable() {
        super.onEnable();

        // Cooldown manager başlat
        this.cooldownManager = new CooldownManager();

        // Config değerlerini yükle
        loadConfig();

        // PacketEvents listener'ı oluştur ve kaydet
        listener = new PacketListenerAbstract(PacketListenerPriority.NORMAL) {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                handlePacketReceive(event);
            }
        };

        com.github.retrooper.packetevents.PacketEvents.getAPI()
            .getEventManager()
            .registerListener(listener);

        debug("Modül aktifleştirildi. Click cooldown: " + clickCooldownMs +
              "ms, Drop cooldown: " + dropCooldownMs + "ms");
    }

    @Override

    public void onDisable() {
        super.onDisable();

        // Cooldown manager'ı temizle
        if (cooldownManager != null) {
            cooldownManager.clearAll();
        }

        // PacketEvents listener'ı kaldır
        if (listener != null) {
            com.github.retrooper.packetevents.PacketEvents.getAPI()
                .getEventManager()
                .unregisterListener(listener);
        }

        debug("Modül devre dışı bırakıldı.");
    }

    /**
     * Config değerlerini yükler
     */
    private void loadConfig() {
        this.clickCooldownMs = getConfigLong("tiklama-cooldown-ms", 50L);
        this.dropCooldownMs = getConfigLong("birakma-cooldown-ms", 50L);

        debug("Config yüklendi: clickCooldown=" + clickCooldownMs +
              "ms, dropCooldown=" + dropCooldownMs + "ms");
    }

    /**
     * Paket alındığında çağrılır
     */
    private void handlePacketReceive(PacketReceiveEvent event) {
        if (!isEnabled()) {
            return;
        }

        // ClickWindow paketini kontrol et
        if (event.getPacketType() != PacketType.Play.Client.CLICK_WINDOW) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        try {
            WrapperPlayClientClickWindow packet = new WrapperPlayClientClickWindow(event);
            UUID uuid = player.getUniqueId();

            // Önce bundle ile ilgili bir etkileşim mi kontrol et
            // Oyuncunun elinde veya cursor'ında bundle olup olmadığını kontrol et
            org.bukkit.inventory.ItemStack mainHand = player.getInventory().getItemInMainHand();
            org.bukkit.inventory.ItemStack offHand = player.getInventory().getItemInOffHand();
            org.bukkit.inventory.ItemStack cursorItem = player.getItemOnCursor();
            boolean bundleInvolved = isBundleItem(mainHand) || isBundleItem(offHand) || isBundleItem(cursorItem);

            // Bundle yoksa bu modülün işi değil — atla
            if (!bundleInvolved) {
                return;
            }

            String cooldownType;
            long cooldownTime;

            // Drop action mı?
            if (packet.getWindowClickType().name().contains("DROP")) {
                cooldownType = "bundle_drop";
                cooldownTime = dropCooldownMs;
            } else {
                cooldownType = "bundle_click";
                cooldownTime = clickCooldownMs;
            }

            debug(player.getName() + " Bundle ClickWindow: " + cooldownType);

            // Cooldown kontrolü
            if (cooldownManager.isOnCooldown(uuid, cooldownType, cooldownTime)) {
                incrementBlockedCount();

                long remaining = cooldownManager.getRemainingTime(uuid, cooldownType, cooldownTime);

                logExploit(player.getName(),
                    String.format("Bundle spam tespit edildi! Tip: %s, Kalan: %dms",
                        cooldownType, remaining));

                event.setCancelled(true);
                debug(player.getName() + " için paket engellendi (cooldown)");
                return;
            }

            // Cooldown ayarla
            cooldownManager.setCooldown(uuid, cooldownType);

        } catch (Exception e) {
            error("ClickWindow paketi işlenirken hata: " + e.getMessage());
        }
    }

    /**
     * Oyuncu cooldown'unu temizler
     */
    public void clearPlayerCooldown(@NotNull UUID uuid) {
        cooldownManager.clearCooldown(uuid, "bundle_click");
        cooldownManager.clearCooldown(uuid, "bundle_drop");
        debug("Bundle cooldown temizlendi: " + uuid);
    }

    /**
     * Tüm cooldown'ları temizler
     */
    public void clearAllCooldowns() {
        cooldownManager.clearAll();
        debug("Tüm bundle cooldown'ları temizlendi");
    }

    /**
     * Click cooldown durumunu kontrol eder
     */
    public boolean isClickOnCooldown(@NotNull UUID uuid) {
        return cooldownManager.isOnCooldown(uuid, "bundle_click", clickCooldownMs);
    }

    /**
     * Drop cooldown durumunu kontrol eder
     */
    public boolean isDropOnCooldown(@NotNull UUID uuid) {
        return cooldownManager.isOnCooldown(uuid, "bundle_drop", dropCooldownMs);
    }

    /**
     * Item'ın bundle olup olmadığını kontrol eder
     */
    private boolean isBundleItem(org.bukkit.inventory.ItemStack item) {
        return item != null && item.getType() == org.bukkit.Material.BUNDLE;
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
