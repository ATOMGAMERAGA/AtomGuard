package com.atomguard.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

public class VelocityListener {

    private final AtomGuardVelocity plugin;
    private final ProxyServer server;
    private final Logger logger;

    public VelocityListener(AtomGuardVelocity plugin, ProxyServer server, Logger logger) {
        this.plugin = plugin;
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        if (plugin.getConfig().getBoolean("log-connections", true)) {
            logger.info("Bağlantı isteği: " + event.getPlayer().getUsername() + " (" + event.getPlayer().getRemoteAddress().getAddress().getHostAddress() + ")");
        }
        
        // Buraya ileride Velocity tabanlı exploit kontrolleri eklenecek.
        // Örneğin: Çok hızlı bağlantı, geçersiz paketler vb.
    }
}
