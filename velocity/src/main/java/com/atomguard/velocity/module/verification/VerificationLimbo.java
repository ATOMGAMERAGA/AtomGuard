package com.atomguard.velocity.module.verification;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.verification.storage.VerifiedPlayerStore;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Gelişmiş Limbo Doğrulama Yöneticisi — v2 (IP+Username çifti + Kick-After-Verify).
 *
 * <h2>Akış</h2>
 * <pre>
 * Oyuncu bağlanır → IP+Username doğrulanmış? → EVET → Direkt giriş
 *                                             → HAYIR → Limbo'ya yönlendir
 *                                                        → Fizik testleri
 *                                                        → PASS → IP+Username kaydet → Kick
 *                                                           → "Doğrulandı, tekrar giriş yapın"
 *                                                        → FAIL → Kick + Firewall violation
 * Tekrar giriş (aynı IP+Username) → Bypass → Direkt giriş
 * Farklı IP ile giriş → Tekrar Limbo doğrulaması
 * </pre>
 */
public class VerificationLimbo {

    public static final String VERIFY_CHANNEL = "atomguard:verify";

    private final AtomGuardVelocity plugin;
    private final VerifiedPlayerStore verifiedStore;
    private final ConnectionQueue queue;
    private final int timeoutSeconds;
    private final String mainServerName;
    private final boolean kickAfterVerify;

    private final ConcurrentHashMap<UUID, VerificationSession> activeSessions = new ConcurrentHashMap<>();

    public VerificationLimbo(AtomGuardVelocity plugin,
                              VerifiedPlayerStore verifiedStore,
                              ConnectionQueue queue,
                              int timeoutSeconds,
                              String mainServerName,
                              boolean kickAfterVerify) {
        this.plugin = plugin;
        this.verifiedStore = verifiedStore;
        this.queue = queue;
        this.timeoutSeconds = timeoutSeconds;
        this.mainServerName = mainServerName;
        this.kickAfterVerify = kickAfterVerify;
    }

    // ─────────────────────────── Yönlendirme ───────────────────────────

    public void sendToLimbo(Player player) {
        String ip = player.getRemoteAddress().getAddress().getHostAddress();
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();

        RegisteredServer limboServer = plugin.getProxyServer()
                .getServer("limbo").orElse(null);

        if (limboServer == null) {
            plugin.getLogManager().log("[Doğrulama] 'limbo' sunucusu bulunamadı — bypass: " + username);
            verifiedStore.markVerified(ip, uuid, username);
            syncVerifiedMarks(ip, uuid, username);
            return;
        }

        VerificationSession session = new VerificationSession(uuid, ip, username);
        activeSessions.put(uuid, session);

        player.createConnectionRequest(limboServer)
              .connectWithIndication()
              .thenAccept(success -> {
                  if (!success) {
                      activeSessions.remove(uuid);
                      queue.release(ip);
                      plugin.getLogManager().log("[Doğrulama] Limbo yönlendirme başarısız: " + username);
                  }
              })
              .exceptionally(e -> {
                  activeSessions.remove(uuid);
                  queue.release(ip);
                  plugin.getLogManager().log("[Doğrulama] Limbo hatası: " + username + " — " + e.getMessage());
                  return null;
              });

        scheduleTimeout(uuid, username);
    }

    // ─────────────────────────── Sonuç İşleme ───────────────────────────

    public void onVerificationResult(UUID uuid, boolean passed, String reason) {
        VerificationSession session = activeSessions.remove(uuid);
        if (session == null) return;

        session.complete(passed);
        Player player = plugin.getProxyServer().getPlayer(uuid).orElse(null);
        String ip = session.getIp();
        String username = session.getUsername();

        queue.release(ip);

        if (passed) {
            // ─── DOĞRULAMA BAŞARILI ───
            verifiedStore.markVerified(ip, uuid, username);
            syncVerifiedMarks(ip, uuid, username);

            plugin.getStatisticsManager().increment("limbo_passed");
            plugin.getLogManager().log(String.format(
                    "[Doğrulama] ✓ DOĞRULANDI: %s (%s) — %dms", username, ip, session.getElapsedMs()));

            if (player != null && player.isActive()) {
                if (kickAfterVerify) {
                    // Kick-After-Verify: Oyuncuyu kick et, tekrar bağlanmasını iste
                    player.disconnect(buildSuccessKickMessage(username, ip));
                    plugin.getLogManager().log("[Doğrulama] Kick-after-verify: " + username);
                } else {
                    transferToMainServer(player);
                }
            }
        } else {
            // ─── DOĞRULAMA BAŞARISIZ ───
            plugin.getStatisticsManager().increment("limbo_failed");
            plugin.getLogManager().log(String.format(
                    "[Doğrulama] ✗ BAŞARISIZ: %s (%s) — %s", username, ip, reason));

            if (player != null && player.isActive()) {
                player.disconnect(buildFailKickMessage());
            }

            if (plugin.getFirewallModule() != null) {
                plugin.getFirewallModule().recordViolation(ip, 25, "bot-tespiti-limbo");
            }
            if (plugin.getAuditLogger() != null) {
                plugin.getAuditLogger().log(
                        com.atomguard.velocity.audit.AuditLogger.EventType.BOT_DETECTED,
                        ip, username, "limbo-verification",
                        "Doğrulama başarısız: " + reason,
                        com.atomguard.velocity.audit.AuditLogger.Severity.WARN);
            }
        }
    }

    // ─────────────────────────── Kick Mesajları ───────────────────────────

    private Component buildSuccessKickMessage(String username, String ip) {
        try {
            Component msg = plugin.getMessageManager().buildKickMessage(
                    "kick.verification-success",
                    Map.of("username", username, "ip", maskIP(ip)));
            if (msg != null) return msg;
        } catch (Exception ignored) {}

        return Component.empty()
                .append(Component.text("\n"))
                .append(Component.text("  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", NamedTextColor.DARK_GREEN))
                .append(Component.text("\n"))
                .append(Component.text("  ✓ ", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text("Doğrulama Başarılı!\n", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text("\n"))
                .append(Component.text("  IP adresiniz ve kullanıcı adınız\n", NamedTextColor.GRAY))
                .append(Component.text("  başarıyla doğrulandı.\n", NamedTextColor.GRAY))
                .append(Component.text("\n"))
                .append(Component.text("  Kullanıcı: ", NamedTextColor.DARK_GRAY))
                .append(Component.text(username + "\n", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text("  IP: ", NamedTextColor.DARK_GRAY))
                .append(Component.text(maskIP(ip) + "\n", NamedTextColor.WHITE))
                .append(Component.text("\n"))
                .append(Component.text("  Lütfen sunucuya tekrar giriş yapın.\n", NamedTextColor.YELLOW))
                .append(Component.text("\n"))
                .append(Component.text("  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", NamedTextColor.DARK_GREEN));
    }

    private Component buildFailKickMessage() {
        try {
            Component msg = plugin.getMessageManager().buildKickMessage(
                    "kick.verification-failed", Map.of());
            if (msg != null) return msg;
        } catch (Exception ignored) {}

        return Component.empty()
                .append(Component.text("\n"))
                .append(Component.text("  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", NamedTextColor.DARK_RED))
                .append(Component.text("\n"))
                .append(Component.text("  ✗ ", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("Doğrulama Başarısız!\n", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("\n"))
                .append(Component.text("  Bağlantınız doğrulanamadı.\n", NamedTextColor.GRAY))
                .append(Component.text("  Lütfen daha sonra tekrar deneyin.\n", NamedTextColor.GRAY))
                .append(Component.text("\n"))
                .append(Component.text("  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", NamedTextColor.DARK_RED));
    }

    private String maskIP(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length == 4) return parts[0] + "." + parts[1] + ".*.**";
        return ip.substring(0, Math.min(ip.length(), 8)) + "...";
    }

    // ─────────────────────────── Yardımcı ───────────────────────────

    private void transferToMainServer(Player player) {
        plugin.getProxyServer().getServer(mainServerName).ifPresentOrElse(
                server -> player.createConnectionRequest(server).fireAndForget(),
                () -> plugin.getLogManager().log("[Doğrulama] Ana sunucu bulunamadı: " + mainServerName));
    }

    private void syncVerifiedMarks(String ip, UUID uuid, String username) {
        if (plugin.getAntiBotModule() != null) plugin.getAntiBotModule().markVerifiedLocal(ip);
        if (plugin.getVpnModule() != null) plugin.getVpnModule().markAsVerifiedClean(ip);
        if (plugin.getFirewallModule() != null) plugin.getFirewallModule().getReputationEngine().rewardSuccessfulLogin(ip);
        if (plugin.getBackendCommunicator() != null) plugin.getBackendCommunicator().broadcastPlayerVerified(username, ip);
    }

    private void scheduleTimeout(UUID uuid, String username) {
        plugin.getProxyServer().getScheduler()
                .buildTask(plugin, () -> {
                    VerificationSession session = activeSessions.get(uuid);
                    if (session == null || session.isComplete()) return;
                    if (!session.isTimedOut(timeoutSeconds)) return;

                    activeSessions.remove(uuid);
                    queue.release(session.getIp());

                    plugin.getStatisticsManager().increment("limbo_timeout");
                    plugin.getLogManager().log("[Doğrulama] TIMEOUT: " + username + " (" + session.getIp() + ")");

                    Player player = plugin.getProxyServer().getPlayer(uuid).orElse(null);
                    if (player != null && player.isActive()) {
                        player.disconnect(Component.text("\n")
                                .append(Component.text("  ⏱ Doğrulama Zaman Aşımı\n\n", NamedTextColor.YELLOW, TextDecoration.BOLD))
                                .append(Component.text("  Süre doldu. Lütfen tekrar deneyin.\n", NamedTextColor.GRAY)));
                    }

                    if (plugin.getFirewallModule() != null) {
                        plugin.getFirewallModule().recordViolation(session.getIp(), 5, "verification-timeout");
                    }
                })
                .delay(timeoutSeconds + 2, TimeUnit.SECONDS)
                .schedule();
    }

    public int getActiveSessionCount() { return activeSessions.size(); }
    public boolean hasActiveSession(UUID uuid) { return activeSessions.containsKey(uuid); }
    public void shutdown() { activeSessions.clear(); }
}
