package com.atomguard.velocity.module.verification;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.verification.storage.VerifiedPlayerStore;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limbo sunucu yöneticisi.
 *
 * <p>Doğrulanmamış oyuncuları Velocity config'de tanımlı {@code "limbo"} backend
 * sunucusuna yönlendirir. Doğrulama sonucu {@link #onVerificationResult} ile
 * AtomGuard-Limbo companion plugin'den plugin messaging kanalı
 * {@code "atomguard:verify"} üzerinden alınır.
 *
 * <p>Limbo sunucu bulunamazsa (graceful degradation): bypass uygular,
 * oyuncu doğrudan bağlanabilir.
 */
public class VerificationLimbo {

    /** Plugin messaging kanalı — AtomGuard-Limbo companion plugin ile iletişim */
    public static final String VERIFY_CHANNEL = "atomguard:verify";

    private final AtomGuardVelocity plugin;
    private final VerifiedPlayerStore verifiedStore;
    private final ConnectionQueue queue;
    private final int timeoutSeconds;
    private final String mainServerName;

    /** UUID → oturum haritası */
    private final ConcurrentHashMap<UUID, VerificationSession> activeSessions = new ConcurrentHashMap<>();

    public VerificationLimbo(AtomGuardVelocity plugin,
                              VerifiedPlayerStore verifiedStore,
                              ConnectionQueue queue,
                              int timeoutSeconds,
                              String mainServerName) {
        this.plugin = plugin;
        this.verifiedStore = verifiedStore;
        this.queue = queue;
        this.timeoutSeconds = timeoutSeconds;
        this.mainServerName = mainServerName;
    }

    // ───────────────────────────── Yönlendirme ─────────────────────────────

    /**
     * Oyuncuyu limbo sunucusuna yönlendir.
     * Limbo sunucu yoksa bypass uygular (graceful degradation).
     */
    public void sendToLimbo(Player player) {
        String ip = player.getRemoteAddress().getAddress().getHostAddress();
        UUID uuid = player.getUniqueId();

        RegisteredServer limboServer = plugin.getProxyServer()
                .getServer("limbo")
                .orElse(null);

        if (limboServer == null) {
            // Limbo sunucu tanımlı değil → bypass (oyuncu normal bağlanır)
            plugin.getLogManager().log("[Limbo] 'limbo' sunucusu bulunamadı — bypass uygulandı: " + player.getUsername());
            verifiedStore.markVerified(ip, uuid, player.getUsername());
            syncVerifiedMarks(ip, uuid, player.getUsername());
            return;
        }

        // Oturum oluştur
        VerificationSession session = new VerificationSession(uuid, ip, player.getUsername());
        activeSessions.put(uuid, session);

        // Limbo'ya yönlendir
        player.createConnectionRequest(limboServer)
              .connectWithIndication()
              .thenAccept(success -> {
                  if (!success) {
                      activeSessions.remove(uuid);
                      queue.release(ip);
                      plugin.getLogManager().log("[Limbo] Yönlendirme başarısız: " + player.getUsername());
                  }
              })
              .exceptionally(e -> {
                  activeSessions.remove(uuid);
                  queue.release(ip);
                  plugin.getLogManager().log("[Limbo] Yönlendirme hatası: " + player.getUsername() + " — " + e.getMessage());
                  return null;
              });

        // Timeout denetleyicisi
        scheduleTimeout(uuid, player.getUsername());
    }

    // ───────────────────────────── Sonuç İşleme ─────────────────────────────

    /**
     * AtomGuard-Limbo companion plugin'den gelen doğrulama sonucunu işle.
     * Kanal: {@code atomguard:verify}
     * Format: {@code "PASS:<uuid>"} veya {@code "FAIL:<uuid>:<reason>"}
     */
    public void onVerificationResult(UUID uuid, boolean passed, String reason) {
        VerificationSession session = activeSessions.remove(uuid);
        if (session == null) return; // Zaten işlendi veya timeout oldu

        session.complete(passed);

        Player player = plugin.getProxyServer().getPlayer(uuid).orElse(null);
        String ip = session.getIp();

        queue.release(ip);

        if (passed) {
            verifiedStore.markVerified(ip, uuid, session.getUsername());
            syncVerifiedMarks(ip, uuid, session.getUsername());

            plugin.getStatisticsManager().increment("limbo_passed");
            plugin.getLogManager().log(String.format(
                "[Limbo] DOĞRULANDI: %s (%s) — %dms",
                session.getUsername(), ip, session.getElapsedMs()
            ));

            if (player != null) {
                transferToMainServer(player);
            }
        } else {
            plugin.getStatisticsManager().increment("limbo_failed");
            plugin.getLogManager().log(String.format(
                "[Limbo] BAŞARISIZ: %s (%s) — sebep: %s",
                session.getUsername(), ip, reason
            ));

            if (player != null) {
                Component kickMsg = plugin.getMessageManager()
                        .buildKickMessage("kick.verification-failed", Map.of());
                player.disconnect(kickMsg);
            }

            // Firewall'a ihlal kaydet
            if (plugin.getFirewallModule() != null) {
                plugin.getFirewallModule().recordViolation(ip, 20, "bot-tespiti");
            }
        }
    }

    // ───────────────────────────── Yardımcı ─────────────────────────────

    /** Doğrulama sonrası ana sunucuya transfer. */
    private void transferToMainServer(Player player) {
        plugin.getProxyServer().getServer(mainServerName).ifPresentOrElse(
            server -> player.createConnectionRequest(server).fireAndForget(),
            () -> plugin.getLogManager().log("[Limbo] Ana sunucu bulunamadı: " + mainServerName)
        );
    }

    /** Verified durumunu antibot + VPN + backend communicator ile senkronize et. */
    private void syncVerifiedMarks(String ip, UUID uuid, String username) {
        if (plugin.getAntiBotModule() != null) {
            plugin.getAntiBotModule().markVerifiedLocal(ip);
        }
        if (plugin.getVpnModule() != null) {
            plugin.getVpnModule().markAsVerifiedClean(ip);
        }
        if (plugin.getFirewallModule() != null) {
            plugin.getFirewallModule().getReputationEngine().rewardSuccessfulLogin(ip);
        }
        if (plugin.getBackendCommunicator() != null) {
            plugin.getBackendCommunicator().broadcastPlayerVerified(username, ip);
        }
    }

    /** Timeout görevi: belirtilen süre sonra oturumu iptal et ve kick. */
    private void scheduleTimeout(UUID uuid, String username) {
        plugin.getProxyServer().getScheduler()
            .buildTask(plugin, () -> {
                VerificationSession session = activeSessions.get(uuid);
                if (session == null || session.isComplete()) return;
                if (!session.isTimedOut(timeoutSeconds)) return;

                activeSessions.remove(uuid);
                queue.release(session.getIp());

                plugin.getStatisticsManager().increment("limbo_timeout");
                plugin.getLogManager().log("[Limbo] TIMEOUT: " + username + " (" + session.getIp() + ")");

                Player player = plugin.getProxyServer().getPlayer(uuid).orElse(null);
                if (player != null) {
                    player.disconnect(plugin.getMessageManager()
                            .buildKickMessage("kick.verification-timeout", Map.of()));
                }
            })
            .delay(timeoutSeconds + 2, java.util.concurrent.TimeUnit.SECONDS)
            .schedule();
    }

    /** Aktif oturum sayısı. */
    public int getActiveSessionCount() { return activeSessions.size(); }

    /** UUID için aktif oturum var mı? */
    public boolean hasActiveSession(UUID uuid) { return activeSessions.containsKey(uuid); }

    /** Temizlik — sunucu kapanırken çağrılır. */
    public void shutdown() {
        activeSessions.clear();
    }
}
