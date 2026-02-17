package com.atomguard.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.PlayerClientBrandEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class VelocityListener {

    private final AtomGuardVelocity plugin;
    private final ProxyServer server;
    private final Logger logger;

    private final Map<String, AtomicInteger> connectionRate = new ConcurrentHashMap<>();
    private final Map<String, Long> lastConnection = new ConcurrentHashMap<>();

    public VelocityListener(AtomGuardVelocity plugin, ProxyServer server, Logger logger) {
        this.plugin = plugin;
        this.server = server;
        this.logger = logger;
        
        // Reset connection rates every minute
        server.getScheduler().buildTask(plugin, connectionRate::clear)
                .repeat(java.time.Duration.ofMinutes(1))
                .schedule();
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        String ip = event.getConnection().getRemoteAddress().getAddress().getHostAddress();
        
        // 1. Connection Rate Limiting
        int rate = connectionRate.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
        if (rate > 10) { // Max 10 connections per minute per IP
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    Component.text("Çok fazla bağlantı isteği! Lütfen bekleyin.", NamedTextColor.RED)
            ));
            return;
        }

        // 2. Handshake Validation (Basic)
        if (event.getUsername().length() > 16 || event.getUsername().isEmpty()) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    Component.text("Geçersiz kullanıcı adı!", NamedTextColor.RED)
            ));
        }
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        if (plugin.getConfig().node("log-connections").getBoolean(true)) {
            logger.info("Bağlantı başarılı: " + event.getPlayer().getUsername() + " (" + event.getPlayer().getRemoteAddress().getAddress().getHostAddress() + ")");
        }
    }

    @Subscribe
    public void onBrand(PlayerClientBrandEvent event) {
        String brand = event.getBrand();
        if (brand.toLowerCase().contains("crasher") || brand.toLowerCase().contains("exploit")) {
            event.getPlayer().disconnect(Component.text("Zararlı client tespiti!", NamedTextColor.RED));
            logger.warn("Zararlı brand tespiti: " + event.getPlayer().getUsername() + " (" + brand + ")");
        }
    }
}
