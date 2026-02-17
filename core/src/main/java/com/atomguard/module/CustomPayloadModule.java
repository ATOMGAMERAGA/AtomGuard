package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Özel Payload Modülü
 *
 * Custom payload paketlerini kontrol eder ve zararlı kanalları engeller.
 * Plugin messaging exploit'lerini önlemek için tasarlanmıştır.
 *
 * Özellikler:
 * - İzinli kanal kontrolü
 * - Maksimum payload boyutu kontrolü
 * - Bilinmeyen kanal engelleme
 * - Kanal whitelist/blacklist sistemi
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class CustomPayloadModule extends AbstractModule {

    private PacketListenerAbstract listener;

    // İzinli kanallar
    private Set<String> allowedChannels;

    // Config cache
    private int maxPayloadSize;
    private boolean blockUnknownChannels;

    /**
     * CustomPayloadModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public CustomPayloadModule(@NotNull AtomGuard plugin) {
        super(plugin, "ozel-payload", "Custom payload paket kontrolü");
        this.allowedChannels = new HashSet<>();
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

        debug("Modül aktifleştirildi. İzinli kanal sayısı: " + allowedChannels.size() +
              ", Max boyut: " + maxPayloadSize + " bytes");
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

        allowedChannels.clear();

        debug("Modül devre dışı bırakıldı.");
    }

    /**
     * Config değerlerini yükler
     */
    private void loadConfig() {
        // İzinli kanalları yükle
        List<String> channelList = plugin.getConfigManager()
            .getConfig()
            .getStringList("moduller." + name + ".izinli-kanallar");

        if (channelList == null || channelList.isEmpty()) {
            // Varsayılan izinli kanallar
            channelList = getDefaultAllowedChannels();
        }

        this.allowedChannels = new HashSet<>(channelList);
        this.maxPayloadSize = getConfigInt("max-payload-boyutu", 32767); // 32KB
        this.blockUnknownChannels = getConfigBoolean("bilinmeyen-kanallari-engelle", true);

        debug("Config yüklendi: maxSize=" + maxPayloadSize +
              ", blockUnknown=" + blockUnknownChannels +
              ", channels=" + allowedChannels.size());
    }

    /**
     * Varsayılan izinli kanalları döndürür
     */
    @NotNull
    private List<String> getDefaultAllowedChannels() {
        List<String> defaults = new ArrayList<>();
        defaults.add("minecraft:brand");
        defaults.add("minecraft:register");
        defaults.add("minecraft:unregister");
        defaults.add("bungeecord:main");
        return defaults;
    }

    /**
     * Paket alındığında çağrılır
     */
    private void handlePacketReceive(PacketReceiveEvent event) {
        if (!isEnabled()) {
            return;
        }

        // CustomPayload paketini kontrol et
        if (event.getPacketType() != PacketType.Play.Client.PLUGIN_MESSAGE) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        try {
            WrapperPlayClientPluginMessage packet = new WrapperPlayClientPluginMessage(event);
            String channelName = packet.getChannelName();
            byte[] data = packet.getData();

            debug(player.getName() + " custom payload gönderdi: " + channelName +
                  " (Boyut: " + data.length + " bytes)");

            // Kanal kontrolü
            if (blockUnknownChannels && !isChannelAllowed(channelName)) {
                incrementBlockedCount();

                logExploit(player.getName(),
                    String.format("Bilinmeyen custom payload kanalı: %s", channelName));

                event.setCancelled(true);
                debug(player.getName() + " için payload engellendi (bilinmeyen kanal)");
                return;
            }

            // Register/Unregister kanal sayısı kontrolü
            if (channelName.equals("minecraft:register") || channelName.equals("minecraft:unregister")) {
                String channels = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                String[] parts = channels.split("\0");
                if (parts.length > 100) { // Limit: 100 channels per packet
                    incrementBlockedCount();
                    logExploit(player.getName(), "Çok fazla kanal kaydı/silme isteği: " + parts.length);
                    event.setCancelled(true);
                    return;
                }
                
                // Kaydedilen kanalların da whitelist kontrolünden geçmesi gerekebilir
                for (String c : parts) {
                    if (c.length() > 256) {
                        incrementBlockedCount();
                        logExploit(player.getName(), "Çok uzun kanal ismi: " + c.length());
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            // Boyut kontrolü
            if (data.length > maxPayloadSize) {
                incrementBlockedCount();

                logExploit(player.getName(),
                    String.format("Çok büyük payload: %d bytes (Limit: %d, Kanal: %s)",
                        data.length, maxPayloadSize, channelName));

                event.setCancelled(true);
                debug(player.getName() + " için payload engellendi (boyut aşımı)");
                return;
            }

            // Zararlı içerik kontrolü
            if (containsMaliciousContent(data)) {
                incrementBlockedCount();

                logExploit(player.getName(),
                    String.format("Zararlı payload içeriği tespit edildi! Kanal: %s", channelName));

                event.setCancelled(true);
                debug(player.getName() + " için payload engellendi (zararlı içerik)");
            }

        } catch (Exception e) {
            error("CustomPayload paketi işlenirken hata: " + e.getMessage());
        }
    }

    /**
     * Kanalın izinli olup olmadığını kontrol eder
     */
    private boolean isChannelAllowed(@NotNull String channel) {
        // Tam eşleşme
        if (allowedChannels.contains(channel)) {
            return true;
        }

        // Varsayılan güvenli prefix'ler — minecraft ve bungeecord kanalları her zaman izinli
        if (channel.startsWith("minecraft:") || channel.startsWith("bungeecord:")) {
            return true;
        }

        // Wildcard kontrolü (örn: "myplugin:*")
        for (String allowed : allowedChannels) {
            if (allowed.endsWith(":*")) {
                String prefix = allowed.substring(0, allowed.length() - 1);
                if (channel.startsWith(prefix)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Zararlı içerik kontrolü yapar
     */
    private boolean containsMaliciousContent(byte[] data) {
        // Null byte kontrolü
        for (byte b : data) {
            if (b == 0x00 && data.length > 1) {
                // Çok fazla null byte var
                int nullCount = 0;
                for (byte check : data) {
                    if (check == 0x00) {
                        nullCount++;
                    }
                }
                if (nullCount > data.length * 0.8) { // %80'den fazlası null
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * İzinli kanallara yeni kanal ekler
     */
    public void addAllowedChannel(@NotNull String channel) {
        allowedChannels.add(channel);
        debug("İzinli kanal eklendi: " + channel);
    }

    /**
     * İzinli kanallardan kanal kaldırır
     */
    public void removeAllowedChannel(@NotNull String channel) {
        allowedChannels.remove(channel);
        debug("İzinli kanal kaldırıldı: " + channel);
    }

    /**
     * Tüm izinli kanalları döndürür
     */
    @NotNull
    public Set<String> getAllowedChannels() {
        return new HashSet<>(allowedChannels);
    }

    /**
     * Modül istatistiklerini döndürür
     */
    public String getStatistics() {
        return String.format("İzinli kanal: %d, Engellenen payload: %d",
            allowedChannels.size(),
            getBlockedCount());
    }
}
