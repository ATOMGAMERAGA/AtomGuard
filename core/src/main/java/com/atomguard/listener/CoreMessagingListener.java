package com.atomguard.listener;

import com.atomguard.AtomGuard;
import com.atomguard.data.VerifiedPlayerCache;
import com.atomguard.manager.AttackModeManager;
import com.atomguard.reputation.IPReputationManager;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.entity.Player;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Core tarafında Velocity proxy'den gelen Plugin Messaging Channel mesajlarını işler.
 * Kanal: atomguard:main
 */
public class CoreMessagingListener implements PluginMessageListener {
    private static final String CHANNEL = "atomguard:main";
    private static final byte PROTOCOL_VERSION = 1;

    private final AtomGuard plugin;

    public CoreMessagingListener(AtomGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            byte version = in.readByte();
            int typeOrdinal = in.readShort();
            int payloadLength = in.readInt();
            byte[] payloadBytes = new byte[payloadLength];
            in.readFully(payloadBytes);
            String payload = new String(payloadBytes, java.nio.charset.StandardCharsets.UTF_8);

            // Process based on type ordinal (matches Velocity MessageType ID):
            // 1=PLAYER_VERIFIED, 2=ATTACK_MODE_SYNC, 3=IP_BLOCK_SYNC, 4=IP_UNBLOCK_SYNC
            // 5=THREAT_SCORE, 6=PLAYER_DATA_REQUEST, 7=STATS_SYNC, 8=CONFIG_RELOAD
            switch (typeOrdinal) {
                case 1 -> handlePlayerVerified(payload);
                case 2 -> handleAttackModeSync(payload);
                case 3 -> handleIPBlockSync(payload);
                case 4 -> handleIPUnblockSync(payload);
                case 5 -> handleThreatScore(payload);
                case 6 -> handlePlayerDataRequest(payload, player);
                case 7 -> handleStatsSync(payload);
                case 8 -> handleConfigReload(payload);
                default -> plugin.getLogger().warning("CoreMessagingListener: Bilinmeyen mesaj tipi: " + typeOrdinal);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "CoreMessagingListener: Mesaj işlenirken hata oluştu", e);
        }
    }

    /**
     * Oyuncu Velocity'de doğrulandı - VerifiedPlayerCache'e ekle.
     * Payload: playerName:ip
     */
    private void handlePlayerVerified(String payload) {
        try {
            String[] parts = payload.split(":", 2);
            if (parts.length < 2) return;

            String playerName = parts[0];
            String ip = parts[1];

            VerifiedPlayerCache cache = plugin.getVerifiedPlayerCache();
            if (cache != null) {
                cache.addVerified(playerName, ip);
                
                // Trigger API Event
                Player player = plugin.getServer().getPlayer(playerName);
                if (player != null) {
                    plugin.getServer().getPluginManager().callEvent(new com.atomguard.api.event.PlayerVerifiedEvent(player, ip));
                }
                
                plugin.getLogger().fine("CoreMessagingListener: Oyuncu doğrulandı - " + playerName + " [" + ip + "]");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "PLAYER_VERIFIED işleme hatası: " + e.getMessage());
        }
    }

    /**
     * Saldırı modu senkronizasyonu.
     * Payload: true/false:peakRate:timestamp
     */
    private void handleAttackModeSync(String payload) {
        try {
            String[] parts = payload.split(":", 3);
            boolean active = Boolean.parseBoolean(parts[0]);

            AttackModeManager attackManager = plugin.getAttackModeManager();
            if (attackManager != null) {
                if (active) {
                    attackManager.forceEnable();
                    plugin.getLogger().info("CoreMessagingListener: Velocity'den saldırı modu aktifleştirildi.");
                } else {
                    attackManager.forceDisable();
                    plugin.getLogger().info("CoreMessagingListener: Velocity'den saldırı modu devre dışı bırakıldı.");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "ATTACK_MODE_SYNC işleme hatası: " + e.getMessage());
        }
    }

    /**
     * IP engelleme senkronizasyonu.
     * Payload: ip:reason:duration
     */
    private void handleIPBlockSync(String payload) {
        try {
            String[] parts = payload.split(":", 3);
            if (parts.length < 1) return;

            String ip = parts[0];
            String reason = parts.length > 1 ? parts[1] : "Velocity proxy engeli";

            IPReputationManager repManager = plugin.getReputationManager();
            if (repManager != null) {
                repManager.blockIP(ip);
                plugin.getLogger().info("CoreMessagingListener: IP engellendi (Velocity sync): " + ip + " - " + reason);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "IP_BLOCK_SYNC işleme hatası: " + e.getMessage());
        }
    }

    /**
     * IP engel kaldırma senkronizasyonu.
     * Payload: ip
     */
    private void handleIPUnblockSync(String payload) {
        try {
            String ip = payload.trim();
            IPReputationManager repManager = plugin.getReputationManager();
            if (repManager != null) {
                repManager.unblockIP(ip);
                plugin.getLogger().info("CoreMessagingListener: IP engeli kaldırıldı (Velocity sync): " + ip);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "IP_UNBLOCK_SYNC işleme hatası: " + e.getMessage());
        }
    }

    /**
     * Tehdit skoru bildirimi.
     * Payload: uuid:ip:score:details
     */
    private void handleThreatScore(String payload) {
        try {
            String[] parts = payload.split(":", 4);
            if (parts.length < 3) return;

            UUID uuid = UUID.fromString(parts[0]);
            String ip = parts[1];
            int score = Integer.parseInt(parts[2]);

            plugin.getLogger().fine("CoreMessagingListener: Tehdit skoru alındı - " + uuid + " [" + ip + "] Skor: " + score);

            // Yüksek tehdit skoru - itibar sistemine ekle
            if (score >= 75 && plugin.getReputationManager() != null) {
                plugin.getReputationManager().blockIP(ip);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "THREAT_SCORE işleme hatası: " + e.getMessage());
        }
    }

    /**
     * Oyuncu verisi isteği.
     * Payload: uuid
     */
    private void handlePlayerDataRequest(String payload, Player requestPlayer) {
        try {
            UUID uuid = UUID.fromString(payload.trim());
            // Mevcut oyuncu verileriyle yanıt oluştur
            Player target = plugin.getServer().getPlayer(uuid);
            String responsePayload;
            if (target != null) {
                responsePayload = uuid + ":{\"online\":true,\"world\":\"" + target.getWorld().getName() + "\"}";
            } else {
                responsePayload = uuid + ":{\"online\":false}";
            }

            // Plugin messaging aracılığıyla yanıtı gönder (type 0 = PLAYER_DATA_RESPONSE)
            sendResponseToVelocity(requestPlayer, 0, responsePayload);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "PLAYER_DATA_REQUEST işleme hatası: " + e.getMessage());
        }
    }

    /**
     * İstatistik senkronizasyonu.
     * Payload: json
     */
    private void handleStatsSync(String payload) {
        plugin.getLogger().fine("CoreMessagingListener: İstatistik senkronizasyonu alındı: " + payload.length() + " karakter");
    }

    /**
     * Config yenileme komutu.
     * Payload: module_name (or "all")
     */
    private void handleConfigReload(String payload) {
        try {
            String moduleName = payload.trim();
            plugin.getLogger().info("CoreMessagingListener: Config yenileme isteği alındı: " + moduleName);

            // Config'i ana thread'de yenile
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getConfigManager().reload();
                plugin.getLogger().info("CoreMessagingListener: Config yenilendi.");
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "CONFIG_RELOAD işleme hatası: " + e.getMessage());
        }
    }

    /**
     * Velocity'ye yanıt mesajı gönderir.
     */
    private void sendResponseToVelocity(Player player, int typeOrdinal, String payload) {
        try {
            byte[] payloadBytes = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(baos);
            out.writeByte(PROTOCOL_VERSION);
            out.writeShort(typeOrdinal);
            out.writeInt(payloadBytes.length);
            out.write(payloadBytes);
            player.sendPluginMessage(plugin, CHANNEL, baos.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Velocity'ye yanıt gönderilemedi: " + e.getMessage());
        }
    }
}
