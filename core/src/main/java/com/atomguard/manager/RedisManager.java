package com.atomguard.manager;

import com.atomguard.AtomGuard;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.concurrent.CompletableFuture;

public class RedisManager {

    private final AtomGuard plugin;
    private JedisPool jedisPool;
    private boolean enabled;
    private Thread pubSubThread;

    public RedisManager(AtomGuard plugin) {
        this.plugin = plugin;
    }

    public void start() {
        this.enabled = plugin.getConfig().getBoolean("redis.enabled", false);
        if (!enabled) return;

        String host = plugin.getConfig().getString("redis.host", "localhost");
        int port = plugin.getConfig().getInt("redis.port", 6379);
        String password = plugin.getConfig().getString("redis.password", "");
        int timeout = plugin.getConfig().getInt("redis.timeout", 2000);

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(8);
        
        if (password.isEmpty()) {
            this.jedisPool = new JedisPool(poolConfig, host, port, timeout);
        } else {
            this.jedisPool = new JedisPool(poolConfig, host, port, timeout, password);
        }

        plugin.getLogger().info("Redis bağlantısı kuruluyor: " + host + ":" + port);
        
        // Start Pub/Sub listener
        startPubSub();
    }

    public void stop() {
        if (pubSubThread != null) {
            pubSubThread.interrupt();
        }
        if (jedisPool != null) {
            jedisPool.close();
        }
    }

    private void startPubSub() {
        pubSubThread = new Thread(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        handlePubSubMessage(channel, message);
                    }
                }, "atomguard:sync");
            } catch (Exception e) {
                if (enabled) {
                    plugin.getLogger().warning("Redis Pub/Sub bağlantısı koptu: " + e.getMessage());
                }
            }
        }, "AtomGuard-Redis-PubSub");
        pubSubThread.start();
    }

    private void handlePubSubMessage(String channel, String message) {
        String[] parts = message.split(":", 2);
        if (parts.length < 2) return;

        String action = parts[0];
        String data = parts[1];

        switch (action) {
            case "IP_BLOCK":
                plugin.getReputationManager().addToManualBlocklist(data);
                break;
            case "IP_UNBLOCK":
                plugin.getReputationManager().removeFromManualBlocklist(data);
                break;
            case "ATTACK_MODE":
                boolean active = Boolean.parseBoolean(data);
                if (active) {
                    plugin.getAttackModeManager().forceEnable();
                } else {
                    plugin.getAttackModeManager().forceDisable();
                }
                break;
        }
    }

    public void publish(String action, String data) {
        if (!enabled || jedisPool == null) return;
        CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.publish("atomguard:sync", action + ":" + data);
            } catch (Exception e) {
                plugin.getLogger().warning("Redis publish hatası: " + e.getMessage());
            }
        });
    }

    public boolean isEnabled() {
        return enabled;
    }
}
