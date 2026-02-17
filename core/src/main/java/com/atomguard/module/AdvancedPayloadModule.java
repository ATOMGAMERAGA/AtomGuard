package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.atomguard.data.PlayerData;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gelişmiş CustomPayload Filtresi Modülü
 *
 * Mevcut CustomPayloadModule'ün ÜZERİNE ek katman olarak çalışır.
 * Ek özellikler:
 * 1. Yapılandırılabilir kanal whitelist
 * 2. Sert boyut limiti (varsayılan 64 byte)
 * 3. Kara liste desenleri (MC|BSign, MC|BEdit, null byte içeren kanallar)
 * 4. Brand analizi ve crash client imzası tespiti
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class AdvancedPayloadModule extends AbstractModule {

    /** Kara listedeki kanal desenleri */
    private static final Set<String> BLACKLISTED_CHANNELS = Set.of(
            "MC|BSign", "MC|BEdit", "MC|TrSel", "MC|PickItem"
    );

    /** Bilinen crash client imzaları */
    private static final Set<String> CRASH_CLIENT_SIGNATURES = Set.of(
            "crasher", "exploit", "grief", "nuke", "destroyer",
            "wurst", "impact+crash", "liquidbounce+crash"
    );

    private PacketListenerAbstract listener;

    /** Oyuncu brand bilgilerini saklar */
    private final Map<UUID, String> playerBrands = new ConcurrentHashMap<>();

    // Config cache
    private int maxPayloadBytes;
    private int brandMaxLength;
    private Set<String> allowedChannels;

    /**
     * AdvancedPayloadModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public AdvancedPayloadModule(@NotNull AtomGuard plugin) {
        super(plugin, "gelismis-payload", "Gelişmiş CustomPayload filtresi");
    }

    @Override

    public void onEnable() {
        super.onEnable();
        loadConfig();

        // PERF-01: Merkezi yönlendiriciye kaydol
        plugin.getPacketListener().registerReceiveHandler(PacketType.Play.Client.PLUGIN_MESSAGE, this::handlePacketReceive);

        debug("Gelişmiş payload filtresi başlatıldı. Max boyut: " + maxPayloadBytes +
                " byte, İzinli kanal: " + allowedChannels.size());
    }

    @Override

    public void onDisable() {
        super.onDisable();
        playerBrands.clear();
        debug("Gelişmiş payload filtresi durduruldu.");
    }

    /**
     * Config değerlerini yükler
     */
    private void loadConfig() {
        this.maxPayloadBytes = getConfigInt("max-payload-bayt", 32768);
        this.brandMaxLength = getConfigInt("brand-max-uzunluk", 256);

        // İzinli kanalları yükle
        List<String> configChannels = plugin.getConfigManager()
                .getStringList("moduller." + getName() + ".izinli-kanallar");
        if (configChannels != null && !configChannels.isEmpty()) {
            this.allowedChannels = new HashSet<>(configChannels);
        } else {
            // Varsayılan: tüm bilinen güvenli kanallar
            this.allowedChannels = new HashSet<>(Set.of(
                    "minecraft:brand", "minecraft:register", "minecraft:unregister"
            ));
        }
    }

    /**
     * Gelen payload paketini kontrol eder
     */
    private void handlePacketReceive(@NotNull PacketReceiveEvent event) {
        if (!isEnabled()) return;

        if (event.getPacketType() != PacketType.Play.Client.PLUGIN_MESSAGE) return;

        if (!(event.getPlayer() instanceof Player player)) return;

        try {
            WrapperPlayClientPluginMessage packet = new WrapperPlayClientPluginMessage(event);
            String channel = packet.getChannelName();
            byte[] data = packet.getData();

            // 1. Kara liste kanal kontrolü
            if (isBlacklistedChannel(channel)) {
                blockPacket(event, player, "Kara listeli kanal: " + channel);
                return;
            }

            // 2. Null byte kontrolü — kanal adında null byte olmamalı
            if (channel.contains("\0")) {
                blockPacket(event, player, "Null byte içeren kanal: " + channel);
                return;
            }

            // 3. Whitelist kontrolü — izinli kanallar listesinde olmalı
            //    minecraft: ve bungeecord: prefix'li kanallar varsayılan olarak izinli
            if (!allowedChannels.isEmpty() && !isChannelAllowed(channel)) {
                blockPacket(event, player, "İzin verilmeyen kanal: " + channel);
                return;
            }

            // 4. Boyut limiti kontrolü
            if (data != null && data.length > maxPayloadBytes) {
                blockPacket(event, player,
                        String.format("Aşırı payload boyutu: %d byte (limit: %d)", data.length, maxPayloadBytes));
                return;
            }

            // 5. Brand analizi
            if ("minecraft:brand".equals(channel) && data != null) {
                analyzeBrand(player, data);
            }
            
            // 6. Zararlı içerik kontrolü (CustomPayloadModule'den taşındı)
            if (data != null && containsMaliciousContent(data)) {
                blockPacket(event, player, "Zararlı payload içeriği tespit edildi! (Anormal null byte oranı)");
                return;
            }
            
            // 7. CR-07: Register/Unregister kanal sayısı ve uzunluk kontrolü
            if (channel.equals("minecraft:register") || channel.equals("minecraft:unregister")) {
                if (data == null) return;
                String channels = new String(data, StandardCharsets.UTF_8);
                String[] parts = channels.split("\0");
                if (parts.length > 100) { // Limit: 100 channels per packet
                    blockPacket(event, player, "Çok fazla kanal kaydı isteği: " + parts.length);
                    return;
                }
                for (String c : parts) {
                    if (c.length() > 256) {
                        blockPacket(event, player, "Çok uzun kanal ismi: " + c.length());
                        return;
                    }
                }
            }

        } catch (Exception e) {
            error("Payload paketi işlenirken hata: " + e.getMessage());
        }
    }

    /**
     * Zararlı içerik kontrolü yapar
     */
    private boolean containsMaliciousContent(byte[] data) {
        if (data.length < 10) return false;
        
        // Null byte kontrolü - %80'den fazlası null ise zararlı sayılır
        int nullCount = 0;
        for (byte b : data) {
            if (b == 0x00) {
                nullCount++;
            }
        }
        return nullCount > data.length * 0.8;
    }

    /**
     * Kanalın izinli olup olmadığını kontrol eder.
     * Tam eşleşme veya prefix bazlı eşleşme destekler.
     * minecraft: ve bungeecord: prefix'li kanallar varsayılan olarak izinlidir.
     */
    private boolean isChannelAllowed(@NotNull String channel) {
        // Tam eşleşme
        if (allowedChannels.contains(channel)) return true;

        // Varsayılan güvenli prefix'ler — modded client'lar ve plugin'ler için
        if (channel.startsWith("minecraft:") || channel.startsWith("bungeecord:")) return true;

        // Wildcard kontrolü (örn: "myplugin:*")
        for (String allowed : allowedChannels) {
            if (allowed.endsWith(":*")) {
                String prefix = allowed.substring(0, allowed.length() - 1);
                if (channel.startsWith(prefix)) return true;
            }
        }

        return false;
    }

    /**
     * Kanal adının kara listede olup olmadığını kontrol eder
     */
    private boolean isBlacklistedChannel(@NotNull String channel) {
        for (String blacklisted : BLACKLISTED_CHANNELS) {
            if (channel.equalsIgnoreCase(blacklisted)) return true;
        }
        return false;
    }

    /**
     * Client brand bilgisini analiz eder
     */
    private void analyzeBrand(@NotNull Player player, @NotNull byte[] data) {
        try {
            String brand = new String(data, StandardCharsets.UTF_8).trim();
            UUID uuid = player.getUniqueId();

            // Brand'i kaydet
            playerBrands.put(uuid, brand);
            debug(player.getName() + " brand: " + brand);

            // Uzunluk kontrolü — aşırı uzun brand şüpheli
            if (brand.length() > brandMaxLength) {
                warning(String.format("Şüpheli brand uzunluğu! Oyuncu: %s, Uzunluk: %d (limit: %d)",
                        player.getName(), brand.length(), brandMaxLength));
            }

            // Crash client imzası kontrolü
            String lowerBrand = brand.toLowerCase();
            for (String signature : CRASH_CLIENT_SIGNATURES) {
                if (lowerBrand.contains(signature)) {
                    logExploit(player.getName(),
                            "Şüpheli client brand tespit edildi: " + brand);
                    break;
                }
            }

        } catch (Exception e) {
            debug("Brand analizi sırasında hata: " + e.getMessage());
        }
    }

    /**
     * Paketi engeller ve loglar
     */
    private void blockPacket(@NotNull PacketReceiveEvent event, @NotNull Player player, @NotNull String reason) {
        event.setCancelled(true);
        incrementBlockedCount();
        logExploit(player.getName(), "Payload engellendi: " + reason);
    }

    /**
     * Oyuncunun brand bilgisini döndürür
     *
     * @param uuid Oyuncu UUID'si
     * @return Brand string'i veya null
     */
    public String getPlayerBrand(@NotNull UUID uuid) {
        return playerBrands.get(uuid);
    }

    /**
     * Oyuncu verisini temizler
     */
    public void removePlayerData(@NotNull UUID uuid) {
        playerBrands.remove(uuid);
    }
}
