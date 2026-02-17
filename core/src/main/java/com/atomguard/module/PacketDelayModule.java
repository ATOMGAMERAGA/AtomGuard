package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.atomguard.data.PlayerData;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paket Gecikme Modülü
 *
 * Paket spam kontrolü yapar ve rate limiting uygular.
 * Oyuncuların aşırı paket göndermesini engelleyerek sunucu performansını korur.
 *
 * Özellikler:
 * - Saniyede maksimum paket sayısı kontrolü
 * - Gecikme eşiği kontrolü
 * - Rate limiting ile spam önleme
 * - PlayerData kullanarak oyuncu bazlı tracking
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class PacketDelayModule extends AbstractModule {

    private PacketListenerAbstract listener;

    // Oyuncu verilerini saklayan map
    private final Map<UUID, PlayerData> playerDataMap;

    // Config cache
    private int maxPacketsPerSecond;
    private long delayThresholdMs;

    /**
     * PacketDelayModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public PacketDelayModule(@NotNull AtomGuard plugin) {
        super(plugin, "paket-gecikme", "Paket spam kontrolü ve rate limiting");
        this.playerDataMap = new ConcurrentHashMap<>();
    }

    @Override

    public void onEnable() {
        super.onEnable();

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

        debug("Modül aktifleştirildi. Max paket/saniye: " + maxPacketsPerSecond);
    }

    @Override

    public void onDisable() {
        super.onDisable();

        // PacketEvents listener'ı kaldır
        if (listener != null) {
            com.github.retrooper.packetevents.PacketEvents.getAPI()
                .getEventManager()
                .unregisterListener(listener);
        }

        // Player data'yı temizle
        playerDataMap.clear();

        debug("Modül devre dışı bırakıldı.");
    }

    /**
     * Config değerlerini yükler
     */
    private void loadConfig() {
        this.maxPacketsPerSecond = getConfigInt("saniyede-max-paket", 500);
        this.delayThresholdMs = getConfigLong("gecikme-esigi-ms", 20L);

        debug("Config yüklendi: maxPackets=" + maxPacketsPerSecond +
              ", delayThreshold=" + delayThresholdMs + "ms");
    }

    /**
     * Paket alındığında çağrılır
     */
    private void handlePacketReceive(PacketReceiveEvent event) {
        if (!isEnabled()) {
            return;
        }

        // Login/Handshake aşamasında player henüz Bukkit Player değil, ClassCastException önleme
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        UUID uuid = player.getUniqueId();

        // PlayerData al veya oluştur
        PlayerData playerData = getOrCreatePlayerData(uuid, player.getName());

        // Paket sayısını artır
        playerData.incrementReceivedPackets();

        // ClickWindow paketlerini özellikle kontrol et (inventory exploit prevention)
        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            handleClickWindowPacket(event, player, playerData);
        }

        // Rate limiting kontrolü
        String packetTypeName = event.getPacketType().getName();
        if (playerData.isRateLimited(packetTypeName, maxPacketsPerSecond)) {
            // Rate limit aşıldı
            incrementBlockedCount();
            playerData.incrementBlockedPackets();

            logExploit(player.getName(),
                String.format("Paket spam tespit edildi! Paket: %s, Rate: %d/s (Limit: %d/s)",
                    packetTypeName,
                    playerData.getOrCreateRateTracker(packetTypeName).getCurrentRate(),
                    maxPacketsPerSecond));

            event.setCancelled(true);
            debug(player.getName() + " için paket engellendi (rate limit)");
        }
    }

    /**
     * ClickWindow paketlerini özel olarak işler
     */
    private void handleClickWindowPacket(@NotNull PacketReceiveEvent event,
                                          @NotNull Player player,
                                          @NotNull PlayerData playerData) {
        try {
            WrapperPlayClientClickWindow packet = new WrapperPlayClientClickWindow(event);

            // Slot kontrolü
            int slot = packet.getSlot();
            if (slot < -999 || slot > 999) {
                incrementBlockedCount();
                playerData.incrementBlockedPackets();

                logExploit(player.getName(),
                    "Geçersiz slot numarası tespit edildi! Slot: " + slot);

                event.setCancelled(true);
                debug(player.getName() + " için ClickWindow paketi engellendi (geçersiz slot)");
            }

        } catch (Exception e) {
            error("ClickWindow paketi işlenirken hata: " + e.getMessage());
        }
    }

    /**
     * PlayerData alır veya oluşturur
     */
    @NotNull
    private PlayerData getOrCreatePlayerData(@NotNull UUID uuid, @NotNull String name) {
        return playerDataMap.computeIfAbsent(uuid, k -> new PlayerData(uuid, name));
    }

    /**
     * Oyuncu verisini kaldırır
     */
    public void removePlayerData(@NotNull UUID uuid) {
        playerDataMap.remove(uuid);
    }

    /**
     * Tüm oyuncu verilerini temizler
     */
    public void clearAllPlayerData() {
        playerDataMap.clear();
    }

    /**
     * Modül istatistiklerini döndürür
     */
    public String getStatistics() {
        long totalPackets = playerDataMap.values().stream()
            .mapToLong(PlayerData::getTotalPacketsReceived)
            .sum();

        int totalBlocked = playerDataMap.values().stream()
            .mapToInt(PlayerData::getBlockedPackets)
            .sum();

        return String.format("Takip edilen oyuncu: %d, Toplam paket: %d, Engellenen: %d",
            playerDataMap.size(),
            totalPackets,
            totalBlocked);
    }

    /**
     * Belirli bir oyuncunun istatistiklerini döndürür
     */
    public String getPlayerStatistics(@NotNull UUID uuid) {
        PlayerData data = playerDataMap.get(uuid);
        if (data == null) {
            return "Oyuncu verisi bulunamadı";
        }

        return String.format("Alınan paket: %d, Engellenen: %d",
            data.getTotalPacketsReceived(),
            data.getBlockedPackets());
    }
}
