package com.atomguard.velocity.communication;

import com.atomguard.velocity.AtomGuardVelocity;
import org.slf4j.Logger;

/**
 * Backend iletişim koordinatörü - Plugin Messaging ve Redis'i birleştirir.
 */
public class BackendCommunicator {

    private final AtomGuardVelocity plugin;
    private final Logger logger;
    private final MessagingBridge messagingBridge;
    private final RedisBridge redisBridge;
    private boolean redisEnabled;

    public BackendCommunicator(AtomGuardVelocity plugin) {
        this.plugin = plugin;
        this.logger = plugin.getSlf4jLogger();
        this.messagingBridge = new MessagingBridge(plugin);
        this.redisBridge = new RedisBridge(plugin);
    }

    public void initialize() {
        messagingBridge.initialize();

        redisEnabled = plugin.getConfigManager().getBoolean("redis.aktif", false);
        if (redisEnabled) {
            String host = plugin.getConfigManager().getString("redis.host", "localhost");
            int port = plugin.getConfigManager().getInt("redis.port", 6379);
            String password = plugin.getConfigManager().getString("redis.sifre", "");
            int timeout = plugin.getConfigManager().getInt("redis.zaman-asimi", 3000);

            redisEnabled = redisBridge.initialize(host, port, password, timeout);
            if (redisEnabled) {
                redisBridge.startSubscriber(this::handleRedisMessage);
            }
        }

        logger.info("Backend iletişim başlatıldı. Redis: {}", redisEnabled ? "aktif" : "devre dışı");
    }

    private void handleRedisMessage(String channel, String message) {
        // Format: TYPE:PAYLOAD
        int sep = message.indexOf(':');
        if (sep < 0) return;
        String type = message.substring(0, sep);
        String payload = message.substring(sep + 1);

        switch (type) {
            case "ATTACK_MODE" -> plugin.setAttackMode(Boolean.parseBoolean(payload));
            case "IP_BLOCK" -> {
                String[] parts = payload.split(":", 2);
                if (parts.length == 2 && plugin.getFirewallModule() != null)
                    plugin.getFirewallModule().banIP(parts[0], 3_600_000L, parts[1]);
            }
            case "CONFIG_RELOAD" -> plugin.getConfigManager().reload();
            default -> logger.debug("Bilinmeyen Redis mesajı: {}", type);
        }
    }

    public void broadcastPlayerVerified(String playerName, String ip) {
        messagingBridge.broadcastPlayerVerified(playerName, ip);
        if (redisEnabled) redisBridge.publish("PLAYER_VERIFIED:" + playerName + ":" + ip);
    }

    public void broadcastAttackMode(boolean enabled) {
        messagingBridge.broadcastAttackMode(enabled);
        if (redisEnabled) redisBridge.publish("ATTACK_MODE:" + enabled);
    }

    public void broadcastIPBlock(String ip, String reason) {
        messagingBridge.broadcastIPBlock(ip, reason);
        if (redisEnabled) redisBridge.publish("IP_BLOCK:" + ip + ":" + reason);
    }

    public MessagingBridge getMessagingBridge() { return messagingBridge; }
    public RedisBridge getRedisBridge() { return redisBridge; }
    public boolean isRedisEnabled() { return redisEnabled; }

    public void shutdown() {
        messagingBridge.shutdown();
        redisBridge.shutdown();
    }
}
