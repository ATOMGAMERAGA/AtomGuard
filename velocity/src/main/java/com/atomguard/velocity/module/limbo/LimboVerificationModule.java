package com.atomguard.velocity.module.limbo;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Velocity-native physics limbo doğrulama modülü.
 * Config key: "limbo-dogrulama"
 *
 * <p>Akış:
 * <ol>
 *   <li>AntiBotCheck MEDIUM_RISK → {@link #scheduleVerification(String, String)}</li>
 *   <li>LoginEvent → {@link #onLogin(Player)} → pozisyon paketi gönderilir</li>
 *   <li>Position paket listener Y pozisyonlarını toplar</li>
 *   <li>{@link PhysicsChallenge#validate} → {@link #onVerificationComplete}</li>
 *   <li>PASS: gerçek sunucuya yönlendir + markVerified</li>
 *   <li>FAIL: kick + firewall violation</li>
 * </ol>
 *
 * <p>Velocity public API play-state paketlerini dinlemediğinden bu modül
 * Seçenek B (reflection channel interceptor) olarak çalışır. NanoLimbo
 * entegrasyonu için {@code LimboPacketHelper} ile kullanılabilir.
 */
public class LimboVerificationModule extends VelocityModule {

    /** Limbo bekleyen IP'ler (PreLogin → Login arası) */
    private final Set<String> pendingVerification = ConcurrentHashMap.newKeySet();

    /** UUID → LimboSession (aktif oturumlar) */
    private final Map<UUID, LimboSession> activeSessions = new ConcurrentHashMap<>();

    private final PhysicsChallenge physicsChallenge = new PhysicsChallenge();

    private int timeoutSeconds;
    private boolean failOnTimeout;
    private String defaultServer;

    public LimboVerificationModule(AtomGuardVelocity plugin) {
        super(plugin, "limbo-dogrulama");
    }

    @Override
    public int getPriority() { return 55; }

    @Override
    protected void onEnable() {
        timeoutSeconds = getConfigInt("timeout-saniye", 6);
        failOnTimeout  = getConfigBoolean("timeout-banlama", false);
        defaultServer  = getConfigString("hedef-sunucu", "survival");

        plugin.getProxyServer().getScheduler()
            .buildTask(plugin, this::cleanupTimedOut)
            .repeat(2, TimeUnit.SECONDS)
            .schedule();

        logger.info("Embedded Limbo doğrulama modülü aktif — timeout={}s, failOnTimeout={}",
                timeoutSeconds, failOnTimeout);
    }

    @Override
    protected void onDisable() {
        pendingVerification.clear();
        activeSessions.values().forEach(s -> s.getResult().cancel(true));
        activeSessions.clear();
    }

    /**
     * AntiBotCheck tarafından çağrılır: MEDIUM_RISK bağlantı → limbo doğrulaması planla.
     */
    public void scheduleVerification(String ip, String username) {
        pendingVerification.add(ip);
        plugin.getLogManager().log("[Limbo] Doğrulama planlandı: " + username + " (" + ip + ")");
    }

    /**
     * Bu IP için limbo doğrulaması bekliyor mu?
     */
    public boolean needsVerification(String ip) {
        return pendingVerification.contains(ip);
    }

    /**
     * LoginEvent'te çağrılır. Oyuncuyu oturuma alır ve teleport paketi gönderir.
     */
    public void onLogin(Player player) {
        String ip = player.getRemoteAddress().getAddress().getHostAddress();
        if (!pendingVerification.remove(ip)) return;

        if (plugin.getAntiBotModule() != null && plugin.getAntiBotModule().isVerified(ip)) return;

        LimboSession session = new LimboSession(player.getUniqueId(), ip, defaultServer,
                                                timeoutSeconds * 1000L);
        activeSessions.put(player.getUniqueId(), session);

        // 500ms sonra teleport gönder
        plugin.getProxyServer().getScheduler()
            .buildTask(plugin, () -> {
                if (player.isActive() && activeSessions.containsKey(player.getUniqueId())) {
                    boolean sent = LimboPacketHelper.sendTeleport(player,
                        0.5, PhysicsChallenge.START_Y, 0.5, 1337);
                    if (sent) {
                        session.setTeleportSent(true);
                    } else {
                        // Teleport başarısız → bypass (graceful degradation)
                        onVerificationComplete(player.getUniqueId(), true, "Teleport paketi gönderilemedi (bypass)");
                    }
                }
            })
            .delay(500, TimeUnit.MILLISECONDS)
            .schedule();

        plugin.getLogManager().log("[Limbo] Oturum başlatıldı: " + player.getUsername() + " (" + ip + ")");
    }

    /**
     * Paket dinleyici tarafından çağrılır: oyuncudan Y pozisyonu alındı.
     */
    public void recordPosition(UUID uuid, double y) {
        LimboSession session = activeSessions.get(uuid);
        if (session == null || !session.isTeleportSent()) return;

        if (y < PhysicsChallenge.START_Y + 1.0) {
            session.recordYPosition(y);
        }

        if (session.hasEnoughData()) {
            PhysicsChallenge.ValidationResult result =
                physicsChallenge.validate(session.getReceivedYPositions());
            onVerificationComplete(uuid, result.passed(), result.reason());
        }
    }

    /**
     * Doğrulama tamamlandı.
     */
    public void onVerificationComplete(UUID uuid, boolean passed, String reason) {
        LimboSession session = activeSessions.remove(uuid);
        if (session == null) return;

        Optional<Player> optPlayer = plugin.getProxyServer().getPlayer(uuid);
        if (optPlayer.isEmpty()) return;
        Player player = optPlayer.get();
        String ip = session.getIp();

        if (passed) {
            plugin.getLogManager().log("[Limbo] PASS: " + player.getUsername() + " — " + reason);

            if (plugin.getAntiBotModule() != null) {
                plugin.getAntiBotModule().markVerified(ip);
            }

            // Gerçek sunucuya yönlendir
            Optional<RegisteredServer> target = plugin.getProxyServer().getServer(session.getTargetServer());
            if (target.isPresent()) {
                player.createConnectionRequest(target.get()).fireAndForget();
            } else {
                plugin.getProxyServer().getAllServers().stream().findFirst()
                    .ifPresent(s -> player.createConnectionRequest(s).fireAndForget());
            }
        } else {
            plugin.getLogManager().log("[Limbo] FAIL: " + player.getUsername() + " — " + reason);

            if (plugin.getFirewallModule() != null) {
                plugin.getFirewallModule().recordViolation(ip, 30, "bot-tespiti");
            }
            if (plugin.getAuditLogger() != null) {
                plugin.getAuditLogger().log(
                    com.atomguard.velocity.audit.AuditLogger.EventType.BOT_DETECTED,
                    ip, player.getUsername(), "limbo", "Fizik doğrulaması başarısız: " + reason,
                    com.atomguard.velocity.audit.AuditLogger.Severity.WARN
                );
            }

            player.disconnect(plugin.getMessageManager().buildKickMessage("kick.bot", Map.of()));
        }

        session.getResult().complete(passed);
    }

    private void cleanupTimedOut() {
        activeSessions.entrySet().removeIf(entry -> {
            LimboSession session = entry.getValue();
            if (!session.isTimedOut()) return false;

            UUID uuid = entry.getKey();
            session.setState(LimboSession.State.TIMEOUT);

            plugin.getProxyServer().getPlayer(uuid).ifPresent(player -> {
                if (failOnTimeout) {
                    onVerificationComplete(uuid, false, "Zaman aşımı");
                } else {
                    onVerificationComplete(uuid, true, "Zaman aşımı (geçir)");
                }
            });

            return true;
        });
    }

    public Map<UUID, LimboSession> getActiveSessions() { return Map.copyOf(activeSessions); }
    public int getPendingCount() { return pendingVerification.size(); }
    public int getActiveCount() { return activeSessions.size(); }
}
