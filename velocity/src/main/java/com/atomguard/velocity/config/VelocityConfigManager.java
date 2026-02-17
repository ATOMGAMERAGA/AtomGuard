package com.atomguard.velocity.config;

import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class VelocityConfigManager {

    private final Path dataDirectory;
    private final Logger logger;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private YamlConfigurationLoader loader;
    private CommentedConfigurationNode root;

    public VelocityConfigManager(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    public void load() throws IOException {
        if (!Files.exists(dataDirectory)) Files.createDirectories(dataDirectory);
        Path configPath = dataDirectory.resolve("config.yml");
        if (!Files.exists(configPath)) {
            try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                if (in != null) Files.copy(in, configPath);
            }
        }
        loader = YamlConfigurationLoader.builder().path(configPath).build();
        lock.writeLock().lock();
        try { root = loader.load(); }
        finally { lock.writeLock().unlock(); }
        logger.info("Yapılandırma yüklendi.");
    }

    public void reload() {
        lock.writeLock().lock();
        try {
            root = loader.load();
            logger.info("Yapılandırma yeniden yüklendi.");
        } catch (ConfigurateException e) {
            logger.error("Yeniden yükleme hatası: {}", e.getMessage());
        } finally { lock.writeLock().unlock(); }
    }

    public boolean getBoolean(String path, boolean def) {
        lock.readLock().lock();
        try { return node(path).getBoolean(def); }
        finally { lock.readLock().unlock(); }
    }

    public int getInt(String path, int def) {
        lock.readLock().lock();
        try { return node(path).getInt(def); }
        finally { lock.readLock().unlock(); }
    }

    public long getLong(String path, long def) {
        lock.readLock().lock();
        try { return node(path).getLong(def); }
        finally { lock.readLock().unlock(); }
    }

    public String getString(String path, String def) {
        lock.readLock().lock();
        try {
            String val = node(path).getString();
            return val != null ? val : def;
        } finally { lock.readLock().unlock(); }
    }

    public List<String> getStringList(String path) {
        lock.readLock().lock();
        try {
            return node(path).childrenList().stream()
                    .map(n -> n.getString(""))
                    .filter(s -> !s.isEmpty())
                    .toList();
        } finally { lock.readLock().unlock(); }
    }

    private CommentedConfigurationNode node(String path) {
        return root.node((Object[]) path.split("\\."));
    }

    public CommentedConfigurationNode getRootNode() {
        lock.readLock().lock();
        try { return root; }
        finally { lock.readLock().unlock(); }
    }
}
