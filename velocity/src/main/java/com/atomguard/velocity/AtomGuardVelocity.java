package com.atomguard.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

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

    private CommentedConfigurationNode config;
    private YamlConfigurationLoader loader;

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
            
            this.loader = YamlConfigurationLoader.builder().path(configPath).build();
            this.config = loader.load();
            
        } catch (Exception e) {
            logger.error("Config yüklenirken hata oluştu", e);
        }
    }

    public CommentedConfigurationNode getConfig() {
        return config;
    }
}
