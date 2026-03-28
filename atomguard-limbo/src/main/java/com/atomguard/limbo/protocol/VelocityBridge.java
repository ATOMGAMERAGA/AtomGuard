package com.atomguard.limbo.protocol;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Velocity ↔ Limbo iletişim köprüsü.
 *
 * <p>Doğrulama sonucunu {@code "atomguard:verify"} plugin messaging kanalı
 * üzerinden Velocity'ye bildirir.
 *
 * <p>Format:
 * <ul>
 *   <li>Başarı: {@code "PASS:<uuid>"}
 *   <li>Başarısız: {@code "FAIL:<uuid>:<reason>"}
 * </ul>
 */
public class VelocityBridge {

    public static final String CHANNEL = "atomguard:verify";

    private final Plugin plugin;

    public VelocityBridge(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Velocity'ye doğrulama geçti mesajı gönder.
     */
    public void sendPass(Player player) {
        String message = "PASS:" + player.getUniqueId();
        sendMessage(player, message);
        plugin.getLogger().info("[Limbo] PASS gönderildi: " + player.getName());
    }

    /**
     * Velocity'ye doğrulama başarısız mesajı gönder.
     *
     * @param reason başarısızlık sebebi (gravity, packet-order, timeout vb.)
     */
    public void sendFail(Player player, String reason) {
        String message = "FAIL:" + player.getUniqueId() + ":" + reason;
        sendMessage(player, message);
        plugin.getLogger().info("[Limbo] FAIL gönderildi: " + player.getName() + " — " + reason);
    }

    private void sendMessage(Player player, String message) {
        try {
            player.sendPluginMessage(plugin, CHANNEL, message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().warning("[Limbo] Plugin mesajı gönderilemedi: " + e.getMessage());
        }
    }
}
