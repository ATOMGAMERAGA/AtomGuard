package com.atomguard.velocity.listener;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.antiddos.DDoSProtectionModule;
import com.atomguard.velocity.module.antibot.VelocityAntiBotModule;
import com.atomguard.velocity.module.antivpn.VPNDetectionModule;
import com.atomguard.velocity.module.firewall.AccountFirewallModule;
import com.atomguard.velocity.module.firewall.AccountFirewallResult;
import com.atomguard.velocity.module.firewall.FirewallModule;
import com.atomguard.velocity.module.geo.CountryFilterModule;
import com.atomguard.velocity.module.geo.CountryFilterResult;
import com.atomguard.velocity.module.ratelimit.GlobalRateLimitModule;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.PlayerClientBrandEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Bağlantı olayları dinleyicisi — pre-login, login, disconnect.
 *
 * <p>Güvenlik kontrolü sırası:
 * <ol>
 *   <li>Güvenlik duvarı (blacklist/tempban)</li>
 *   <li>Hız sınırlaması</li>
 *   <li>Ülke filtreleme</li>
 *   <li>Hesap güvenlik duvarı</li>
 *   <li>DDoS koruma</li>
 *   <li>Bot tespiti — DOĞRULANMIŞ OYUNCU BYPASS</li>
 *   <li>VPN kontrolü — KONSENSÜS TABANLI + VERIFIED BYPASS</li>
 * </ol>
 *
 * <p>Login başarısında tüm bypass cache'leri güncellenir.
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
        UUID uuid = event.getUniqueId();

        // 1. Güvenlik duvarı kontrolü
        FirewallModule firewall = plugin.getFirewallModule();
        if (firewall != null) {
            FirewallModule.FirewallCheckResult fwResult = firewall.check(ip);
            if (fwResult.verdict() == FirewallModule.FirewallVerdict.DENY) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    plugin.getMessageManager().buildKickMessage("kick.yasakli",
                        Map.of("ip", ip, "sebep", fwResult.reason()))));
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

        // 3. Ülke Filtreleme
        CountryFilterModule countryFilter = plugin.getCountryFilterModule();
        if (countryFilter != null && countryFilter.isEnabled()) {
            CountryFilterResult geoResult = countryFilter.check(ip);
            if (!geoResult.isAllowed()) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    plugin.getMessageManager().parse(geoResult.getReason())));
                return;
            }
        }

        // 4. Hesap Güvenlik Duvarı
        AccountFirewallModule accountFirewall = plugin.getAccountFirewallModule();
        if (accountFirewall != null && accountFirewall.isEnabled()) {
            try {
                boolean isPremium = true;
                AccountFirewallResult accResult = accountFirewall.checkAsync(username, uuid, isPremium)
                        .get(2, TimeUnit.SECONDS);
                if (!accResult.isAllowed()) {
                    event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                        plugin.getMessageManager().parse(accResult.getReason())));
                    return;
                }
            } catch (Exception ignored) {
                // Timeout veya hata → fail-open
            }
        }

        // 5. DDoS koruma
        DDoSProtectionModule ddos = plugin.getDdosModule();
        if (ddos != null) {
            DDoSProtectionModule.ConnectionCheckResult ddosResult = ddos.checkConnection(ip, false);
            if (!ddosResult.allowed()) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    plugin.getMessageManager().buildKickMessage("kick.ddos", Map.of())));
                return;
            }
        }

        // 6. Bot tespiti — DOĞRULANMIŞ OYUNCU BYPASS
        VelocityAntiBotModule antiBot = plugin.getAntiBotModule();
        if (antiBot != null && antiBot.isEnabled()) {
            if (!antiBot.isVerified(ip)) {
                // Gerçek handshake bilgilerini PreLoginEvent'ten çıkar
                String hostname = event.getConnection().getVirtualHost()
                        .map(addr -> addr.getHostString()).orElse(null);
                int port = event.getConnection().getVirtualHost()
                        .map(addr -> addr.getPort()).orElse(0);
                int protocol = event.getConnection().getProtocolVersion().getProtocol();
                antiBot.analyzePreLogin(ip, username, hostname, port, protocol);
                if (antiBot.isHighRisk(ip)) {
                    if (firewall != null) {
                        // 20 → 10 puan, bağlamsal tür "bot-tespiti" (0.7x çarpan)
                        firewall.recordViolation(ip, 10, "bot-tespiti");
                    }
                    event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                        plugin.getMessageManager().buildKickMessage("kick.bot", Map.of())));
                    return;
                }
            }
        }

        // 7. VPN kontrolü — KONSENSÜS TABANLI + VERIFIED BYPASS
        VPNDetectionModule vpn = plugin.getVpnModule();
        if (vpn != null && vpn.isEnabled()) {
            if (!vpn.isVerifiedClean(ip)) {
                try {
                    // 3 saniyelik timeout (önceden 2 sn)
                    VPNDetectionModule.DetectionResult vpnResult = vpn.check(ip, false)
                            .get(3, TimeUnit.SECONDS);
                    if (vpnResult.isVPN()) {
                        event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                            plugin.getMessageManager().buildKickMessage("kick.vpn", Map.of())));
                        return;
                    }
                } catch (Exception ignored) {
                    // VPN kontrolü zaman aşımına uğradı → fail-open (erişilebilirlik öncelikli)
                }
            }
        }

        plugin.getStatisticsManager().increment("pre_login_checks");
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        String ip = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();

        // Başarılı giriş = otomatik doğrulama (tüm sistemlerde)
        VelocityAntiBotModule antiBot = plugin.getAntiBotModule();
        if (antiBot != null) {
            antiBot.markVerified(ip);
            antiBot.recordJoin(ip);
        }

        FirewallModule firewall = plugin.getFirewallModule();
        if (firewall != null) {
            firewall.getReputationEngine().rewardSuccessfulLogin(ip);
        }

        VPNDetectionModule vpn = plugin.getVpnModule();
        if (vpn != null) {
            vpn.markAsVerifiedClean(ip);
        }

        plugin.getStatisticsManager().increment("total_connections");
        plugin.getLogManager().log("Giriş: " + event.getPlayer().getUsername() + " (" + ip + ")");
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (event.getPlayer().getRemoteAddress() == null) return;
        String ip = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();
        if (plugin.getAntiBotModule() != null) plugin.getAntiBotModule().recordQuit(ip);
        if (plugin.getExploitModule() != null) plugin.getExploitModule().onPlayerLeave(event.getPlayer().getUniqueId());
        if (plugin.getAntiBotModule() != null && plugin.getAntiBotModule().isCaptchaEnabled())
            plugin.getAntiBotModule().getCaptcha().onPlayerLeave(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onBrand(PlayerClientBrandEvent event) {
        String ip = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();
        VelocityAntiBotModule antiBot = plugin.getAntiBotModule();
        if (antiBot != null) {
            // Doğrulanmış oyuncuları atla
            if (antiBot.isVerified(ip)) return;

            antiBot.recordBrand(ip, event.getBrand());
            if (antiBot.isHighRisk(ip)) {
                event.getPlayer().disconnect(
                    plugin.getMessageManager().buildKickMessage("kick.bot", Map.of()));
            }
        }
    }
}
