package com.atomguard.velocity.listener;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.antibot.VelocityAntiBotModule;
import com.atomguard.velocity.module.antivpn.VPNDetectionModule;
import com.atomguard.velocity.module.firewall.FirewallModule;
import com.atomguard.velocity.module.verification.VerificationModule;
import com.atomguard.velocity.pipeline.CheckResult;
import com.atomguard.velocity.pipeline.ConnectionContext;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.PlayerClientBrandEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Bağlantı olayları dinleyicisi — pre-login, login, disconnect.
 * 
 * Pipeline mimarisine geçilmiştir.
 */
public class ConnectionListener {

    private final AtomGuardVelocity plugin;

    public ConnectionListener(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    @Subscribe(order = PostOrder.EARLY)
    public EventTask onPreLogin(PreLoginEvent event) {
        String ip = event.getConnection().getRemoteAddress().getAddress().getHostAddress();
        String username = event.getUsername();
        UUID uuid = event.getUniqueId();
        if (uuid == null) {
            uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        String hostname = event.getConnection().getVirtualHost()
                .map(addr -> addr.getHostString()).orElse(null);
        int port = event.getConnection().getVirtualHost()
                .map(addr -> addr.getPort()).orElse(0);
        int protocol = event.getConnection().getProtocolVersion().getProtocol();

        // Pipeline'a verified flag'i ekle — VerificationModule öncelikli, fallback antibot
        boolean verified = false;
        VerificationModule verificationModule = plugin.getVerificationModule();
        if (verificationModule != null && verificationModule.isEnabled()) {
            verified = verificationModule.isVerified(ip);
        } else {
            VelocityAntiBotModule antiBotForCtx = plugin.getAntiBotModule();
            if (antiBotForCtx != null) verified = antiBotForCtx.isVerified(ip);
        }

        ConnectionContext ctx = new ConnectionContext(ip, username, uuid, hostname, port, protocol, verified);

        CompletableFuture<Void> future = plugin.getConnectionPipeline().processAsync(ctx)
                .thenAccept(result -> {
                    if (result.denied()) {
                        event.setResult(PreLoginEvent.PreLoginComponentResult.denied(result.kickMessage()));

                        if (plugin.getAuditLogger() != null) {
                            plugin.getAuditLogger().connectionBlocked(ip, result.module(), result.reason());
                        }

                        // Detaylı deny logu
                        plugin.getLogManager().log(String.format(
                            "[DENY] IP=%s User=%s Module=%s Reason=%s Verified=%s Severity=%s",
                            ip, username, result.module(), result.reason(), ctx.verified(), result.severity()
                        ));

                        // Violation kaydı: SADECE ciddi ihlallerde (antibot, firewall, vpn).
                        // Rate limit ve throttle gibi geçici SOFT deny'lar trust score düşürmemeli.
                        if (plugin.getBehaviorManager() != null
                                && result.severity() != CheckResult.Severity.SOFT
                                && isHardViolationModule(result.module())) {
                            plugin.getBehaviorManager().recordViolation(ip);
                        }

                        plugin.getStatisticsManager().increment("pre_login_blocked");
                        return;
                    }
                    plugin.getStatisticsManager().increment("pre_login_checks");
                })
                .exceptionally(e -> {
                    plugin.getLogManager().log("PreLogin pipeline hatası (" + ip + "): " + e.getMessage());
                    return null;
                });

        return EventTask.resumeWhenComplete(future);
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        String ip = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();
        String username = event.getPlayer().getUsername();
        UUID uuid = event.getPlayer().getUniqueId();

        VerificationModule verificationModule = plugin.getVerificationModule();

        // Doğrulama modülü aktifse ve bu oyuncu henüz verified değilse → limbo
        if (verificationModule != null && verificationModule.isEnabled()
                && !verificationModule.isVerified(ip)) {

            // Kuyruğa al
            verificationModule.getConnectionQueue().enqueue(ip).thenAccept(slotAcquired -> {
                if (!slotAcquired) {
                    event.getPlayer().disconnect(
                        plugin.getMessageManager().buildKickMessage("kick.queue-full", Map.of()));
                    return;
                }
                // Limbo'ya yönlendir
                verificationModule.getLimbo().sendToLimbo(event.getPlayer());
            });

            // Behavior / history kaydı yönlendirme sonrası yapılır; burada sadece
            // connection counter'ı artır
            plugin.getStatisticsManager().increment("total_connections");
            plugin.getLogManager().log("Limbo yönlendirme: " + username + " (" + ip + ")");
            return;
        }

        // Embedded limbo doğrulaması (MEDIUM_RISK oyuncular için)
        if (plugin.getLimboModule() != null && plugin.getLimboModule().isEnabled()
                && plugin.getLimboModule().needsVerification(ip)) {
            plugin.getLimboModule().onLogin(event.getPlayer());
            plugin.getStatisticsManager().increment("total_connections");
            return;
        }

        // Doğrulama modülü kapalı veya oyuncu zaten verified → normal giriş akışı
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

        // Behavior & History
        if (plugin.getBehaviorManager() != null) plugin.getBehaviorManager().recordLogin(ip, username);
        if (plugin.getConnectionHistory() != null) plugin.getConnectionHistory().recordLogin(uuid, ip, username);

        plugin.getStatisticsManager().increment("total_connections");
        plugin.getLogManager().log("Giriş: " + username + " (" + ip + ")");
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (event.getPlayer().getRemoteAddress() == null) return;
        java.net.InetAddress addr = event.getPlayer().getRemoteAddress().getAddress();
        if (addr == null) return;
        String ip = addr.getHostAddress();
        UUID uuid = event.getPlayer().getUniqueId();

        if (plugin.getAntiBotModule() != null) plugin.getAntiBotModule().recordQuit(ip);
        if (plugin.getExploitModule() != null) plugin.getExploitModule().onPlayerLeave(uuid);
        if (plugin.getAntiBotModule() != null && plugin.getAntiBotModule().isCaptchaEnabled())
            plugin.getAntiBotModule().getCaptcha().onPlayerLeave(uuid);
        
        if (plugin.getConnectionHistory() != null) plugin.getConnectionHistory().recordLogout(uuid);
    }

    @Subscribe
    public void onServerSwitch(com.velocitypowered.api.event.player.ServerConnectedEvent event) {
        if (plugin.getConnectionHistory() != null) {
            plugin.getConnectionHistory().recordServerSwitch(
                event.getPlayer().getUniqueId(), 
                event.getServer().getServerInfo().getName()
            );
        }
    }

    @Subscribe
    public void onBrand(PlayerClientBrandEvent event) {
        String ip = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();
        VelocityAntiBotModule antiBot = plugin.getAntiBotModule();
        if (antiBot == null) return;

        // Doğrulanmış oyuncuları atla
        if (antiBot.isVerified(ip)) return;

        // Race condition düzeltmesi: brand event bazen login event'ten ÖNCE gelir.
        // Oyuncu zaten bir backend sunucusuna bağlıysa login tamamlanmış demektir
        // → verified olarak işaretle, kick yapma.
        if (event.getPlayer().getCurrentServer().isPresent()) {
            antiBot.markVerified(ip);
            return;
        }

        // Oyuncu login sürecini tamamlamışsa (isActive) verified olarak işaretle
        if (event.getPlayer().isActive()) {
            antiBot.markVerified(ip);
            return;
        }

        // Brand kaydı — sadece analiz için, KICK YAPMA.
        // Kick kararı yalnızca PreLogin pipeline'ı verir.
        // Brand event'inde kick yapmak race condition ve çift kontrol sorununa yol açar.
        antiBot.recordBrand(ip, event.getBrand());
    }

    /**
     * Bu modül ciddi ihlal mi? (violation kaydı için kullanılır)
     * Rate limit, throttle gibi geçici durumlar violation sayılmaz.
     */
    private static boolean isHardViolationModule(String module) {
        if (module == null) return false;
        return switch (module) {
            case "antibot", "firewall", "vpn", "account-firewall" -> true;
            default -> false;
        };
    }
}
