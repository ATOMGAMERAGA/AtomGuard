package com.atomguard.velocity.module.latency;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;

public class LatencyCheckModule extends VelocityModule {

    private final ConnectionLatencyTracker tracker;

    public LatencyCheckModule(AtomGuardVelocity plugin) {
        super(plugin, "gecikme-kontrol");
        this.tracker = new ConnectionLatencyTracker();
    }

    @Override
    public void onEnable() {
        plugin.getProxyServer().getEventManager().register(plugin, this);
        logger.info("Latency Check module enabled.");
    }

    @Override
    public void onDisable() {
        plugin.getProxyServer().getEventManager().unregisterListener(plugin, this);
        logger.info("Latency Check module disabled.");
    }

    // Note: To capture Handshake time accurately, we ideally need a PacketListener/ChannelHandler.
    // Events might be too late or inconsistent for precise network latency.
    // But PreLoginEvent is the earliest high-level event.
    // We can use PreLogin as "Handshake processed" time approx.
    
    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        if (!isEnabled()) return;
        String ip = event.getConnection().getRemoteAddress().getAddress().getHostAddress();
        tracker.recordHandshake(ip);
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        if (!isEnabled()) return;
        String ip = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();
        long duration = tracker.recordLogin(ip);

        if (duration != -1) {
            long min = getConfigLong("handshake-login-suresi.minimum-ms", 15);
            long max = getConfigLong("handshake-login-suresi.maksimum-ms", 30000);
            
            if (duration < min) {
                logger.info("Suspiciously fast login from {}: {}ms", ip, duration);
                // Flag or kick logic
                String action = getConfigString("handshake-login-suresi.aksiyon-hizli", "flag");
                if ("kick".equalsIgnoreCase(action)) {
                    event.getPlayer().disconnect(plugin.getMessageManager().parse("<red>Login too fast."));
                }
            } else if (duration > max) {
                // Slowloris check
                logger.info("Suspiciously slow login from {}: {}ms", ip, duration);
                String action = getConfigString("handshake-login-suresi.aksiyon-yavas", "kick");
                 if ("kick".equalsIgnoreCase(action)) {
                    event.getPlayer().disconnect(plugin.getMessageManager().parse("<red>Login timeout."));
                }
            }
        }
    }
}