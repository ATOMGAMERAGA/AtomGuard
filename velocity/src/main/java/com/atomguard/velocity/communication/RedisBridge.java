package com.atomguard.velocity.communication;

import com.atomguard.velocity.AtomGuardVelocity;
import org.slf4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

/**
 * Redis Pub/Sub ile sunucular arası iletişim.
 */
public class RedisBridge {

    private static final String CHANNEL = "atomguard:sync";

    private final AtomGuardVelocity plugin;
    private final Logger logger;
    private JedisPool pool;
    private ExecutorService subscriber;
    private volatile boolean running = false;
    private BiConsumer<String, String> messageHandler;

    public RedisBridge(AtomGuardVelocity plugin) {
        this.plugin = plugin;
        this.logger = plugin.getSlf4jLogger();
    }

    public boolean initialize(String host, int port, String password, int timeout) {
        try {
            JedisPoolConfig config = new JedisPoolConfig();
            config.setMaxTotal(8);
            config.setMaxIdle(2);
            config.setTestOnBorrow(true);

            if (password != null && !password.isBlank()) {
                pool = new JedisPool(config, host, port, timeout, password);
            } else {
                pool = new JedisPool(config, host, port, timeout);
            }

            // Bağlantıyı test et
            try (Jedis jedis = pool.getResource()) {
                jedis.ping();
            }

            logger.info("Redis bağlantısı kuruldu: {}:{}", host, port);
            return true;
        } catch (Exception e) {
            logger.warn("Redis bağlantısı kurulamadı: {}. Redis özellikleri devre dışı.", e.getMessage());
            pool = null;
            return false;
        }
    }

    public void startSubscriber(BiConsumer<String, String> handler) {
        if (pool == null || !isConnected()) return;
        this.messageHandler = handler;
        running = true;
        subscriber = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "atomguard-redis-sub");
            t.setDaemon(true);
            return t;
        });
        subscriber.submit(() -> {
            int attempt = 0;
            while (running) {
                try (Jedis jedis = pool.getResource()) {
                    attempt = 0; // Başarılı bağlantıda deneme sayısını sıfırla
                    jedis.subscribe(new JedisPubSub() {
                        @Override
                        public void onMessage(String channel, String message) {
                            if (messageHandler != null) messageHandler.accept(channel, message);
                        }
                    }, CHANNEL);
                } catch (Exception e) {
                    if (running) {
                        long delay = Math.min(5000L * (1L << attempt), 60_000L); // Max 60sn
                        logger.warn("Redis subscriber hatası, {} ms sonra yeniden bağlanıyor (deneme {}): {}", delay, attempt + 1, e.getMessage());
                        try { Thread.sleep(delay); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                        attempt++;
                    }
                }
            }
        });
    }

    public boolean publish(String message) {
        if (!isConnected()) return false;
        try (Jedis jedis = pool.getResource()) {
            jedis.publish(CHANNEL, message);
            return true;
        } catch (Exception e) {
            logger.warn("Redis yayın hatası: {}", e.getMessage());
            return false;
        }
    }

    public boolean isConnected() { return pool != null && !pool.isClosed(); }

    public void shutdown() {
        running = false;
        if (subscriber != null) subscriber.shutdownNow();
        if (pool != null) pool.close();
    }
}
