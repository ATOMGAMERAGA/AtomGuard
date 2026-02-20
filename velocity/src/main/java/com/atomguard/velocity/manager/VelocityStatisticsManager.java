package com.atomguard.velocity.manager;

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
import java.util.concurrent.atomic.AtomicLong;

public class VelocityStatisticsManager {

    private final ConcurrentHashMap<String, AtomicLong> stats = new ConcurrentHashMap<>();
    private final Path statsFile;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public VelocityStatisticsManager(Path dataDirectory, Logger logger) {
        this.statsFile = dataDirectory.resolve("velocity-stats.json");
        this.logger = logger;
    }

    public void load() {
        if (!Files.exists(statsFile)) return;
        try {
            String json = Files.readString(statsFile, StandardCharsets.UTF_8);
            Type type = new TypeToken<Map<String, Long>>(){}.getType();
            Map<String, Long> loaded = gson.fromJson(json, type);
            if (loaded != null) {
                loaded.forEach((k, v) -> stats.put(k, new AtomicLong(v)));
            }
        } catch (IOException e) {
            logger.warn("İstatistik yüklenemedi: {}", e.getMessage());
        }
    }

    public void save() {
        try {
            Map<String, Long> snapshot = new HashMap<>();
            stats.forEach((k, v) -> snapshot.put(k, v.get()));
            Files.writeString(statsFile, gson.toJson(snapshot), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("İstatistik kaydedilemedi: {}", e.getMessage());
        }
    }

    public void increment(String key) {
        stats.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void increment(String key, long amount) {
        stats.computeIfAbsent(key, k -> new AtomicLong(0)).addAndGet(amount);
    }

    public long get(String key) {
        AtomicLong v = stats.get(key);
        return v != null ? v.get() : 0L;
    }

    public long getLong(String key, long def) {
        AtomicLong v = stats.get(key);
        return v != null ? v.get() : def;
    }

    public int getInt(String key, int def) {
        AtomicLong v = stats.get(key);
        return v != null ? (int) v.get() : def;
    }

    public void reset(String key) {
        stats.computeIfAbsent(key, k -> new AtomicLong(0)).set(0);
    }

    public Map<String, Long> getAll() {
        Map<String, Long> snapshot = new HashMap<>();
        stats.forEach((k, v) -> snapshot.put(k, v.get()));
        return snapshot;
    }

    public Map<String, Long> getAllModuleStats() {
        Map<String, Long> moduleStats = new HashMap<>();
        stats.forEach((k, v) -> {
            if (k.startsWith("module-") && k.endsWith("-total")) {
                String moduleName = k.substring(7, k.length() - 6);
                moduleStats.put(moduleName, v.get());
            }
        });
        return moduleStats;
    }
}
