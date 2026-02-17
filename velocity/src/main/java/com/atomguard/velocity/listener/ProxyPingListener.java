package com.atomguard.velocity.listener;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.antiddos.DDoSProtectionModule;
import com.atomguard.velocity.module.ratelimit.GlobalRateLimitModule;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Proxy ping/MOTD olayı dinleyicisi.
 */
public class ProxyPingListener {

    private final AtomGuardVelocity plugin;

    public ProxyPingListener(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    @Subscribe(order = PostOrder.EARLY)
    public void onPing(ProxyPingEvent event) {
        String ip = event.getConnection().getRemoteAddress().getAddress().getHostAddress();

        GlobalRateLimitModule rateLimiter = plugin.getRateLimitModule();
        if (rateLimiter != null && !rateLimiter.allowPing(ip)) {
            // Sahte yanıt döndür
            event.setPing(event.getPing().asBuilder()
                .onlinePlayers(0)
                .maximumPlayers(0)
                .description(Component.text("Sunucu bakımda.", NamedTextColor.RED))
                .build());
            plugin.getStatisticsManager().increment("ping_blocked");
            return;
        }

        DDoSProtectionModule ddos = plugin.getDdosModule();
        if (ddos != null && !ddos.checkPing(ip)) {
            event.setPing(event.getPing().asBuilder()
                .onlinePlayers(0)
                .maximumPlayers(0)
                .description(Component.text("Lütfen bekleyin.", NamedTextColor.YELLOW))
                .build());
            plugin.getStatisticsManager().increment("ping_blocked");
        }
    }
}
