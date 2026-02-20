package com.atomguard.velocity.communication;

import com.atomguard.velocity.AtomGuardVelocity;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;

/**
 * Backend iletişim koordinatörü - Plugin Messaging ve Redis'i birleştirir.
 */
public class BackendCommunicator {

    private final AtomGuardVelocity plugin;
    private final Logger logger;
    private final MessagingBridge messagingBridge;
    private final RedisBridge redisBridge;
    private boolean redisEnabled;
    private final String proxyId;

    public BackendCommunicator(AtomGuardVelocity plugin) {
        this.plugin = plugin;
        this.logger = plugin.getSlf4jLogger();
        this.messagingBridge = new MessagingBridge(plugin);
        this.redisBridge = new RedisBridge(plugin);
        this.proxyId = UUID.randomUUID().toString().substring(0, 8); // Kendi gönderdiği mesajları yoksaymak için
    }

    public void initialize() {
        if (messagingBridge != null) messagingBridge.initialize();
        setupRedis();
    }

    private void setupRedis() {
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
        logger.info("Redis senkronizasyonu: {} (ProxyID: {})", redisEnabled ? "AKTIF" : "KAPALI", proxyId);
    }

    public void reload() {
        logger.info("Backend iletişimi yeniden yapılandırılıyor...");
        redisBridge.shutdown();
        setupRedis();
    }

    private void handleRedisMessage(String channel, String jsonMessage) {
        try {
            RedisMessage msg = RedisMessage.deserialize(jsonMessage);
            
            // Kendi gönderdiğimiz mesajı işleme (sonsuz döngüyü engeller)
            if (proxyId.equals(msg.getSourceProxyId())) return;

            Map<String, String> data = msg.getData();
            
            switch (msg.getType()) {
                case "ATTACK_MODE" -> {
                    if (data.containsKey("enabled")) {
                        plugin.setAttackMode(Boolean.parseBoolean(data.get("enabled")));
                    }
                }
                case "IP_BLOCK" -> {
                    if (data.containsKey("ip") && plugin.getFirewallModule() != null) {
                        String ip = data.get("ip");
                        String reason = data.getOrDefault("reason", "Cross-server ban");
                        long duration = data.containsKey("duration") ? Long.parseLong(data.get("duration")) : 3_600_000L;
                        
                        if (duration == 0) {
                            plugin.getFirewallModule().blacklistIPLocal(ip);
                        } else {
                            plugin.getFirewallModule().banIPLocal(ip, duration, reason);
                        }
                    }
                }
                case "IP_UNBLOCK" -> {
                    if (data.containsKey("ip") && plugin.getFirewallModule() != null) {
                        plugin.getFirewallModule().unbanIPLocal(data.get("ip"));
                    }
                }
                case "WHITELIST_SYNC" -> {
                    if (data.containsKey("ip") && plugin.getFirewallModule() != null) {
                        plugin.getFirewallModule().whitelistIPLocal(data.get("ip"));
                    }
                }
                case "VERIFIED_PLAYER_SYNC" -> {
                    if (data.containsKey("ip") && plugin.getAntiBotModule() != null) {
                        plugin.getAntiBotModule().markVerifiedLocal(data.get("ip"));
                        if (plugin.getVpnModule() != null) {
                            plugin.getVpnModule().markAsVerifiedClean(data.get("ip"));
                        }
                    }
                }
                case "CONFIG_RELOAD" -> {
                    plugin.getConfigManager().reload();
                    plugin.getMessageManager().load(plugin.getConfigManager().getString("dil", "tr"));
                    plugin.getLogManager().log("Uzaktan yapılandırma yenilendi.");
                }
                default -> logger.debug("Bilinmeyen Redis mesaj türü: {}", msg.getType());
            }
        } catch (Exception e) {
            logger.debug("Redis mesajı okunamadı (eski format olabilir veya bozuk JSON): {}", e.getMessage());
        }
    }

    public void broadcastPlayerVerified(String playerName, String ip) {
        messagingBridge.broadcastPlayerVerified(playerName, ip);
        if (redisEnabled) {
            RedisMessage msg = new RedisMessage("VERIFIED_PLAYER_SYNC", proxyId, Map.of("username", playerName, "ip", ip));
            redisBridge.publish(msg.serialize());
        }
    }

    public void broadcastAttackMode(boolean enabled) {
        messagingBridge.broadcastAttackMode(enabled);
        if (redisEnabled) {
            RedisMessage msg = new RedisMessage("ATTACK_MODE", proxyId, Map.of("enabled", String.valueOf(enabled)));
            redisBridge.publish(msg.serialize());
        }
    }

    public void broadcastIPBlock(String ip, long duration, String reason) {
        messagingBridge.broadcastIPBlock(ip, reason);
        if (redisEnabled) {
            RedisMessage msg = new RedisMessage("IP_BLOCK", proxyId, Map.of(
                "ip", ip, 
                "reason", reason,
                "duration", String.valueOf(duration)
            ));
            redisBridge.publish(msg.serialize());
        }
    }

    public void broadcastBlacklist(String ip) {
        if (redisEnabled) {
            RedisMessage msg = new RedisMessage("IP_BLOCK", proxyId, Map.of(
                "ip", ip, 
                "reason", "Blacklisted",
                "duration", "0" // 0 = Kalıcı
            ));
            redisBridge.publish(msg.serialize());
        }
    }

    public void broadcastIPUnblock(String ip) {
        if (redisEnabled) {
            RedisMessage msg = new RedisMessage("IP_UNBLOCK", proxyId, Map.of("ip", ip));
            redisBridge.publish(msg.serialize());
        }
    }

    public void broadcastWhitelistSync(String ip) {
        if (redisEnabled) {
            RedisMessage msg = new RedisMessage("WHITELIST_SYNC", proxyId, Map.of("ip", ip));
            redisBridge.publish(msg.serialize());
        }
    }

    public void broadcastConfigReload() {
        if (redisEnabled) {
            RedisMessage msg = new RedisMessage("CONFIG_RELOAD", proxyId, Map.of());
            redisBridge.publish(msg.serialize());
        }
    }

    public MessagingBridge getMessagingBridge() { return messagingBridge; }
    public RedisBridge getRedisBridge() { return redisBridge; }
    public boolean isRedisEnabled() { return redisEnabled; }

    public void shutdown() {
        messagingBridge.shutdown();
        redisBridge.shutdown();
    }
}
