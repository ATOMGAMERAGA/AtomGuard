package com.atomguard.data;

import com.atomguard.AtomGuard;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Basariyla giris yapan oyuncularin IP'lerini JSON'a cache'ler.
 * Configurable TTL ile otomatik temizleme yapar.
 */
public class VerifiedPlayerCache {

    private final AtomGuard plugin;
    private final Gson gson;
    private final File cacheFile;
    private final ScheduledExecutorService scheduler;

    // Config
    private final boolean enabled;
    private final long ttlMillis;
    private final boolean skipBotCheck;
    private final boolean skipIpCheck;

    // player_name:ip -> timestamp
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public VerifiedPlayerCache(AtomGuard plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.cacheFile = new File(plugin.getDataFolder(), "verified-players.json");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AtomGuard-VerifiedCache");
            t.setDaemon(true);
            return t;
        });

        this.enabled = plugin.getConfig().getBoolean("dogrulanmis-onbellek.aktif", true);
        long hours = plugin.getConfig().getLong("dogrulanmis-onbellek.sure-saat", 48);
        this.ttlMillis = hours * 3600_000L;
        this.skipBotCheck = plugin.getConfig().getBoolean("dogrulanmis-onbellek.bot-kontrolu-atla", true);
        this.skipIpCheck = plugin.getConfig().getBoolean("dogrulanmis-onbellek.ip-kontrolu-atla", false);
    }

    public void start() {
        if (!enabled) return;

        load();

        // Cleanup expired entries every 10 minutes
        scheduler.scheduleAtFixedRate(this::cleanupExpired, 10, 10, TimeUnit.MINUTES);

        // Auto-save every 15 minutes
        scheduler.scheduleAtFixedRate(this::save, 15, 15, TimeUnit.MINUTES);

        plugin.getLogger().info("VerifiedPlayerCache baslatildi. Cache boyutu: " + cache.size());
    }

    public void stop() {
        save();
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Adds a player+IP to the verified cache.
     */
    public void addVerified(String playerName, String ip) {
        if (!enabled) return;
        String key = playerName.toLowerCase() + ":" + ip;
        cache.put(key, new CacheEntry(ip, playerName, System.currentTimeMillis()));
    }

    /**
     * Checks if a player+IP combination is in the verified cache and not expired.
     */
    public boolean isVerified(String playerName, String ip) {
        if (!enabled) return false;
        String key = playerName.toLowerCase() + ":" + ip;
        CacheEntry entry = cache.get(key);
        if (entry == null) return false;
        if (System.currentTimeMillis() - entry.timestamp > ttlMillis) {
            cache.remove(key);
            return false;
        }
        return true;
    }

    /**
     * Checks if an IP is in the verified cache (any player).
     */
    public boolean isIpVerified(String ip) {
        if (!enabled) return false;
        long now = System.currentTimeMillis();
        for (CacheEntry entry : cache.values()) {
            if (entry.ip.equals(ip) && (now - entry.timestamp) <= ttlMillis) {
                return true;
            }
        }
        return false;
    }

    public boolean shouldSkipBotCheck() {
        return enabled && skipBotCheck;
    }

    public boolean shouldSkipIpCheck() {
        return enabled && skipIpCheck;
    }

    public int getCacheSize() {
        return cache.size();
    }

    // ═══════════════════════════════════════
    // Persistence
    // ═══════════════════════════════════════

    private void load() {
        if (!cacheFile.exists()) return;

        try (Reader reader = new InputStreamReader(new FileInputStream(cacheFile), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, CacheEntry>>(){}.getType();
            Map<String, CacheEntry> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                cache.clear();
                cache.putAll(loaded);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Verified player cache okunamadi: " + e.getMessage());
        }
    }

    public synchronized void save() {
        if (!enabled) return;
        try {
            if (!cacheFile.getParentFile().exists()) {
                cacheFile.getParentFile().mkdirs();
            }
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(cacheFile), StandardCharsets.UTF_8)) {
                gson.toJson(cache, writer);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Verified player cache kaydedilemedi: " + e.getMessage());
        }
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> (now - entry.getValue().timestamp) > ttlMillis);
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ═══════════════════════════════════════
    // Data class
    // ═══════════════════════════════════════

    public static class CacheEntry {
        public String ip;
        public String playerName;
        public long timestamp;

        public CacheEntry() {} // For Gson

        public CacheEntry(String ip, String playerName, long timestamp) {
            this.ip = ip;
            this.playerName = playerName;
            this.timestamp = timestamp;
        }
    }
}
