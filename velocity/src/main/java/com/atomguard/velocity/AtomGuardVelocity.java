package com.atomguard.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "atomguard-velocity",
        name = "Atom Guard Velocity",
        version = "1.0.0",
        description = "Velocity proxy module for Atom Guard",
        authors = {"AtomGuard Team"}
)
public class AtomGuardVelocity {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private YamlConfiguration config;

    @Inject
    public AtomGuardVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("AtomGuard Velocity modülü başlatılıyor...");
        
        loadConfig();
        
        server.getEventManager().register(this, new VelocityListener(this, server, logger));
        
        logger.info("AtomGuard Velocity modülü başarıyla yüklendi.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("AtomGuard Velocity modülü kapatılıyor...");
    }

    private void loadConfig() {
        try {
            if (!java.nio.file.Files.exists(dataDirectory)) {
                java.nio.file.Files.createDirectories(dataDirectory);
            }
            
            Path configPath = dataDirectory.resolve("config.yml");
            if (!java.nio.file.Files.exists(configPath)) {
                try (java.io.InputStream in = getClass().getResourceAsStream("/config.yml")) {
                    if (in != null) {
                        java.nio.file.Files.copy(in, configPath);
                    } else {
                        // Create default if resource not found
                        java.nio.file.Files.writeString(configPath, "debug: true\nlog-connections: true\n");
                    }
                }
            }
            
            // Basic YAML loading (using SnakeYAML via Velocity or just simple parsing if dependency issue)
            // Since Velocity doesn't shade SnakeYAML by default for plugins, we might need it.
            // But usually Velocity plugins use Configurate.
            // For simplicity and avoiding deps, I'll use a very simple properties loader or assume Configurate is available.
            // Actually, let's just use a simple Properties-like approach for now since adding Configurate dependency requires pom changes.
            // Wait, I can add Configurate or just use simple logic.
            
            // To be safe and dependency-free for now:
            config = new YamlConfiguration(configPath, logger);
            config.load();
            
        } catch (Exception e) {
            logger.error("Config yüklenirken hata oluştu", e);
        }
    }

    public YamlConfiguration getConfig() {
        return config;
    }

    // Simple Configuration helper class to avoid external dependencies for this basic prototype
    public static class YamlConfiguration {
        private final Path path;
        private final Logger logger;
        private final java.util.Map<String, Object> values = new java.util.HashMap<>();

        public YamlConfiguration(Path path, Logger logger) {
            this.path = path;
            this.logger = logger;
        }

        public void load() {
            try {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(path);
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String value = parts[1].trim();
                        if (value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                        }
                        values.put(key, value);
                    }
                }
            } catch (Exception e) {
                logger.error("YamlConfiguration load error", e);
            }
        }

        public String getString(String key) {
            return (String) values.get(key);
        }

        public boolean getBoolean(String key, boolean def) {
            Object val = values.get(key);
            if (val == null) return def;
            return Boolean.parseBoolean(val.toString());
        }
    }
}
