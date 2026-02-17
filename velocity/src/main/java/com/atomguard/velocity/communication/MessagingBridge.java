package com.atomguard.velocity.communication;

import com.atomguard.velocity.AtomGuardVelocity;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Plugin Messaging kanalı üzerinden Core ↔ Velocity iletişimi.
 */
public class MessagingBridge {

    private final AtomGuardVelocity plugin;
    private final Logger logger;
    public static final MinecraftChannelIdentifier CHANNEL_ID =
        MinecraftChannelIdentifier.from(SyncProtocol.CHANNEL);

    public MessagingBridge(AtomGuardVelocity plugin) {
        this.plugin = plugin;
        this.logger = plugin.getSlf4jLogger();
    }

    public void initialize() {
        plugin.getProxyServer().getChannelRegistrar().register(CHANNEL_ID);
        logger.info("Plugin Messaging kanalı kayıt edildi: {}", SyncProtocol.CHANNEL);
    }

    public void shutdown() {
        plugin.getProxyServer().getChannelRegistrar().unregister(CHANNEL_ID);
    }

    public boolean sendToBackend(Player player, SyncProtocol.MessageType type, String payload) {
        Optional<ServerConnection> server = player.getCurrentServer();
        if (server.isEmpty()) return false;
        try {
            byte[] data = SyncProtocol.encode(type, payload);
            return server.get().sendPluginMessage(CHANNEL_ID, data);
        } catch (Exception e) {
            logger.warn("Plugin mesajı gönderilemedi: {}", e.getMessage());
            return false;
        }
    }

    public boolean sendToAnyBackend(SyncProtocol.MessageType type, String payload) {
        for (Player player : plugin.getProxyServer().getAllPlayers()) {
            if (sendToBackend(player, type, payload)) return true;
        }
        return false;
    }

    public void broadcastPlayerVerified(String playerName, String ip) {
        sendToAnyBackend(SyncProtocol.MessageType.PLAYER_VERIFIED, playerName + ":" + ip);
    }

    public void broadcastAttackMode(boolean enabled) {
        sendToAnyBackend(SyncProtocol.MessageType.ATTACK_MODE_SYNC, String.valueOf(enabled));
    }

    public void broadcastIPBlock(String ip, String reason) {
        sendToAnyBackend(SyncProtocol.MessageType.IP_BLOCK_SYNC, ip + ":" + reason);
    }

    public void broadcastIPUnblock(String ip) {
        sendToAnyBackend(SyncProtocol.MessageType.IP_UNBLOCK_SYNC, ip);
    }
}
