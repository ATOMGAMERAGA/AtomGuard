package com.atomguard.module.antibot.action;

import com.atomguard.module.antibot.AntiBotModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BlacklistManager {
    private final AntiBotModule module;
    private final Map<String, BlacklistEntry> blacklistedIPs = new ConcurrentHashMap<>();
    private final File dataFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public BlacklistManager(AntiBotModule module) {
        this.module = module;
        this.dataFile = new File(module.getPlugin().getDataFolder(), module.getConfigString("kara-liste.dosya", "blacklist.json"));
        load();
    }

    public void blacklist(String ip, long durationMs, String reason) {
        blacklistedIPs.put(ip, new BlacklistEntry(System.currentTimeMillis(), durationMs, reason));
    }

    public boolean isBlacklisted(String ip) {
        BlacklistEntry entry = blacklistedIPs.get(ip);
        if (entry == null) return false;

        if (entry.durationMs > 0) {
            long elapsed = System.currentTimeMillis() - entry.timestamp;
            if (elapsed > entry.durationMs) {
                blacklistedIPs.remove(ip);
                return false;
            }
        }
        return true;
    }

    private void load() {
        if (!dataFile.exists()) return;
        try (Reader reader = new InputStreamReader(new FileInputStream(dataFile), StandardCharsets.UTF_8)) {
            Map<String, BlacklistEntry> loaded = gson.fromJson(reader, new TypeToken<Map<String, BlacklistEntry>>(){}.getType());
            if (loaded != null) blacklistedIPs.putAll(loaded);
        } catch (IOException e) {
            module.getPlugin().getLogger().warning("Blacklist load error: " + e.getMessage());
        }
    }

    public void saveAsync() {
        Map<String, BlacklistEntry> copy = new ConcurrentHashMap<>(blacklistedIPs);
        module.getPlugin().getServer().getScheduler().runTaskAsynchronously(module.getPlugin(), () -> {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(dataFile), StandardCharsets.UTF_8)) {
                gson.toJson(copy, writer);
            } catch (IOException e) {
                module.getPlugin().getLogger().warning("Blacklist save error: " + e.getMessage());
            }
        });
    }

    public static class BlacklistEntry {
        long timestamp;
        long durationMs;
        String reason;

        public BlacklistEntry(long timestamp, long durationMs, String reason) {
            this.timestamp = timestamp;
            this.durationMs = durationMs;
            this.reason = reason;
        }
    }
}
