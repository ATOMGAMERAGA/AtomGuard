package com.atomguard.velocity.listener;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.antiddos.DDoSProtectionModule;
import com.atomguard.velocity.module.antibot.VelocityAntiBotModule;
import com.atomguard.velocity.module.antivpn.VPNDetectionModule;
import com.atomguard.velocity.module.firewall.FirewallModule;
import com.atomguard.velocity.module.ratelimit.GlobalRateLimitModule;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.PlayerClientBrandEvent;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Bağlantı olayları dinleyicisi - pre-login, login, disconnect.
 */
public class ConnectionListener {

    private final AtomGuardVelocity plugin;

    public ConnectionListener(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    @Subscribe(order = PostOrder.EARLY)
    public void onPreLogin(PreLoginEvent event) {
        String ip = event.getConnection().getRemoteAddress().getAddress().getHostAddress();
        String username = event.getUsername();

        // 1. Güvenlik duvarı kontrolü
        FirewallModule firewall = plugin.getFirewallModule();
        if (firewall != null) {
            FirewallModule.FirewallCheckResult fwResult = firewall.check(ip);
            if (fwResult.verdict() == FirewallModule.FirewallVerdict.DENY) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    plugin.getMessageManager().buildKickMessage("kick.yasakli", Map.of("ip", ip))));
                return;
            }
        }

        // 2. Hız sınırlaması
        GlobalRateLimitModule rateLimiter = plugin.getRateLimitModule();
        if (rateLimiter != null && !rateLimiter.allowConnection(ip)) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                plugin.getMessageManager().buildKickMessage("kick.rate-limit", Map.of())));
            return;
        }

        // 3. DDoS koruma
        DDoSProtectionModule ddos = plugin.getDdosModule();
        if (ddos != null) {
            DDoSProtectionModule.ConnectionCheckResult ddosResult = ddos.checkConnection(ip, false);
            if (!ddosResult.allowed()) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    plugin.getMessageManager().buildKickMessage("kick.ddos", Map.of())));
                return;
            }
        }

        // 4. Bot tespiti
        VelocityAntiBotModule antiBot = plugin.getAntiBotModule();
        if (antiBot != null) {
            antiBot.analyzePreLogin(ip, username, null, 0, 0);
            if (antiBot.isHighRisk(ip)) {
                plugin.getFirewallModule().recordViolation(ip, 20, "bot-tespiti");
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    plugin.getMessageManager().buildKickMessage("kick.bot", Map.of())));
                return;
            }
        }

        // 5. VPN kontrolü (asenkron, zaman aşımı ile)
        VPNDetectionModule vpn = plugin.getVpnModule();
        if (vpn != null) {
            try {
                VPNDetectionModule.DetectionResult vpnResult = vpn.check(ip, false).get(3, TimeUnit.SECONDS);
                if (vpnResult.isVPN()) {
                    event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                        plugin.getMessageManager().buildKickMessage("kick.vpn", Map.of())));
                    return;
                }
            } catch (Exception ignored) {
                // VPN kontrolü zaman aşımına uğradı, devam et
            }
        }

        plugin.getStatisticsManager().increment("pre_login_checks");
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        String ip = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();
        if (plugin.getAntiBotModule() != null) plugin.getAntiBotModule().recordJoin(ip);
        plugin.getStatisticsManager().increment("total_connections");
        plugin.getLogManager().log("Giriş: " + event.getPlayer().getUsername() + " (" + ip + ")");
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        String ip = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();
        if (plugin.getAntiBotModule() != null) plugin.getAntiBotModule().recordQuit(ip);
        if (plugin.getExploitModule() != null) plugin.getExploitModule().onPlayerLeave(event.getPlayer().getUniqueId());
        if (plugin.getAntiBotModule() != null && plugin.getAntiBotModule().isCaptchaEnabled())
            plugin.getAntiBotModule().getCaptcha().onPlayerLeave(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onBrand(PlayerClientBrandEvent event) {
        String ip = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();
        if (plugin.getAntiBotModule() != null) {
            plugin.getAntiBotModule().recordBrand(ip, event.getBrand());
            if (plugin.getAntiBotModule().isHighRisk(ip)) {
                event.getPlayer().disconnect(
                    plugin.getMessageManager().buildKickMessage("kick.bot", Map.of()));
            }
        }
    }
}
