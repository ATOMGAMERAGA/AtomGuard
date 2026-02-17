package com.atomguard.velocity.module.firewall;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Geçici IP yasaklama yönetimi - kalıcı olmayan, süreli yasaklar.
 */
public class TempBanManager {

    private final ConcurrentHashMap<String, BanEntry> bans = new ConcurrentHashMap<>();
    private final Path banFile;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().create();

    public TempBanManager(Path dataDirectory, Logger logger) {
        this.banFile = dataDirectory.resolve("temp-bans.json");
        this.logger = logger;
    }

    public void load() {
        if (!Files.exists(banFile)) return;
        try {
            String json = Files.readString(banFile, StandardCharsets.UTF_8);
            Type type = new TypeToken<Map<String, BanEntry>>(){}.getType();
            Map<String, BanEntry> loaded = gson.fromJson(json, type);
            if (loaded != null) bans.putAll(loaded);
            cleanup();
        } catch (IOException e) {
            logger.warn("Geçici yasak listesi yüklenemedi: {}", e.getMessage());
        }
    }

    public void save() {
        try {
            cleanup();
            Files.writeString(banFile, gson.toJson(new HashMap<>(bans)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("Geçici yasak listesi kaydedilemedi: {}", e.getMessage());
        }
    }

    public void ban(String ip, long durationMs, String reason) {
        bans.put(ip, new BanEntry(System.currentTimeMillis() + durationMs, reason));
    }

    public boolean isBanned(String ip) {
        BanEntry entry = bans.get(ip);
        if (entry == null) return false;
        if (System.currentTimeMillis() > entry.expiry()) {
            bans.remove(ip);
            return false;
        }
        return true;
    }

    public void unban(String ip) { bans.remove(ip); }

    public long getRemainingMs(String ip) {
        BanEntry entry = bans.get(ip);
        if (entry == null) return 0;
        long remaining = entry.expiry() - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    public String getBanReason(String ip) {
        BanEntry entry = bans.get(ip);
        return entry != null ? entry.reason() : null;
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        bans.entrySet().removeIf(e -> e.getValue().expiry() < now);
    }

    public int size() { return bans.size(); }

    record BanEntry(long expiry, String reason) {}
}
