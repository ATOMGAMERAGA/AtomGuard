package com.atomguard.limbo;

import com.atomguard.limbo.check.GravityCheck;
import com.atomguard.limbo.check.KeepAliveCheck;
import com.atomguard.limbo.check.PacketOrderCheck;
import com.atomguard.limbo.protocol.VelocityBridge;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limbo sunucudaki oyuncu doğrulama oturumlarını yönetir.
 *
 * <p>Akış:
 * <ol>
 *   <li>{@link PlayerJoinEvent} → limbo dünyasına teleport, oturum başlat
 *   <li>{@link PlayerMoveEvent} → position analizi (yerçekimi kontrolü)
 *   <li>{@link PlayerSettingsChangeEvent} → client settings alındı
 *   <li>Her tick scheduler → timeout kontrolü, tüm check'ler tamamlandı mı?
 *   <li>PASS/FAIL → {@link VelocityBridge} ile Velocity'ye bildir
 * </ol>
 */
public class VerificationHandler implements Listener {

    /** Oturum başına state */
    private static class Session {
        final GravityCheck gravity;
        final PacketOrderCheck packetOrder;
        final KeepAliveCheck keepAlive;
        final long startTime;

        Session(double spawnY) {
            this.gravity    = new GravityCheck(spawnY);
            this.packetOrder = new PacketOrderCheck();
            this.keepAlive  = new KeepAliveCheck();
            this.startTime  = System.currentTimeMillis();
        }

        boolean isTimedOut(int seconds) {
            return System.currentTimeMillis() - startTime > seconds * 1000L;
        }
    }

    private final AtomGuardLimbo plugin;
    private final LimboWorldManager worldManager;
    private final VelocityBridge velocityBridge;
    private final int timeoutSeconds;

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public VerificationHandler(AtomGuardLimbo plugin,
                                LimboWorldManager worldManager,
                                VelocityBridge velocityBridge,
                                int timeoutSeconds) {
        this.plugin         = plugin;
        this.worldManager   = worldManager;
        this.velocityBridge = velocityBridge;
        this.timeoutSeconds = timeoutSeconds;
    }

    // ───────────────────────────── Events ─────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Limbo dünyası dışına mesaj gönderme
        event.setJoinMessage(null);

        // Limbo dünyasına teleport
        Location spawn = worldManager.getSpawnLocation();
        if (spawn == null) {
            plugin.getLogger().warning("[Limbo] Spawn konumu yok, oyuncu kabul edildi: " + player.getName());
            velocityBridge.sendPass(player);
            return;
        }

        player.teleport(spawn);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setInvisible(true);  // Başka oyuncular göremez

        // Oturum başlat
        Session session = new Session(LimboWorldManager.SPAWN_Y);
        sessions.put(player.getUniqueId(), session);

        plugin.getLogger().info("[Limbo] Doğrulama başladı: " + player.getName());

        // Tick scheduler — her tick kontrol et
        new BukkitRunnable() {
            @Override
            public void run() {
                Session s = sessions.get(player.getUniqueId());
                if (s == null || !player.isOnline()) {
                    cancel();
                    return;
                }

                // Timeout
                if (s.isTimedOut(timeoutSeconds)) {
                    sessions.remove(player.getUniqueId());
                    velocityBridge.sendFail(player, "timeout");
                    player.kickPlayer("Doğrulama zaman aşımı");
                    cancel();
                    return;
                }

                // Minimum paket gereksinimleri karşılandı ve yerçekimi doğrulandı mı?
                if (s.packetOrder.isMinimumMet()) {
                    Boolean gravityResult = getLatestGravityResult(s);
                    if (gravityResult != null) {
                        sessions.remove(player.getUniqueId());
                        cancel();
                        if (gravityResult && s.keepAlive.isSatisfied()) {
                            velocityBridge.sendPass(player);
                        } else {
                            String reason = !gravityResult ? "gravity" : "keepalive";
                            velocityBridge.sendFail(player, reason);
                        }
                    }
                } else if (s.packetOrder.isDefiniteBot()) {
                    // Position hiç gelmedi → kesin bot
                    sessions.remove(player.getUniqueId());
                    velocityBridge.sendFail(player, "no-position");
                    player.kickPlayer("Bot tespit edildi");
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 1L); // 1 saniye sonra başla, her tick çalış
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) return;

        // Sadece Y değişimini analiz et
        double fromY = event.getFrom().getY();
        double toY = event.getTo().getY();
        if (Math.abs(toY - fromY) < 0.001) return; // Yatay hareket, yoksay

        session.gravity.onPositionUpdate(toY);
        session.packetOrder.onPositionReceived();
    }

    /** CLIENT_SETTINGS paketi dil değişikliği olarak da gelen PlayerLocaleChangeEvent ile tespit edilir. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLocaleChange(PlayerLocaleChangeEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null) {
            session.packetOrder.onClientSettings();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
        event.setQuitMessage(null);
    }

    // ───────────────────────────── Yardımcı ─────────────────────────────

    /**
     * Oturumun en güncel gravity sonucunu döndür.
     * Karar verilemezse null.
     */
    private Boolean getLatestGravityResult(Session session) {
        // GravityCheck kendi state'ini tutar; son onPositionUpdate sonucuna eriş
        // Bu metod her tick çağrılır; GravityCheck zaten hesaplamış olur
        // Burada doğrudan accuracy threshold kontrolü yapıyoruz
        if (session.gravity.getTickCount() < 5) return null;
        double acc = session.gravity.getAccuracy();
        if (acc >= 0.80) return Boolean.TRUE;
        if (acc <= 0.25) return Boolean.FALSE;
        return null; // Belirsiz
    }

    /** Plugin mesajı alındığında brand kontrolü için çağrılır. */
    public void onBrandReceived(UUID uuid) {
        Session session = sessions.get(uuid);
        if (session != null) {
            session.packetOrder.onBrandReceived();
        }
    }

    /** KeepAlive gönderildiğinde çağrılır. */
    public void onKeepAliveSent(UUID uuid) {
        Session session = sessions.get(uuid);
        if (session != null) {
            session.keepAlive.onKeepAliveSent();
        }
    }

    /** KeepAlive yanıtı alındığında çağrılır. */
    public void onKeepAliveResponse(UUID uuid) {
        Session session = sessions.get(uuid);
        if (session != null) {
            session.keepAlive.onKeepAliveResponse();
        }
    }

    public int getActiveSessionCount() { return sessions.size(); }
}
