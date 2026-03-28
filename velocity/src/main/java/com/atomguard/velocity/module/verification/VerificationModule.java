package com.atomguard.velocity.module.verification;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;
import com.atomguard.velocity.module.verification.storage.VerifiedPlayerStore;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

import java.util.concurrent.TimeUnit;

/**
 * Sonar-tarzı Limbo Doğrulama Modülü.
 *
 * <p>Oyuncuları sahte bir limbo sunucuya yönlendirerek gerçek bir Minecraft
 * client'ı olduklarını doğrular. Bot yazılımları fizik simülasyonu
 * yapamadığı için %99.9+ doğruluk oranıyla tespit edilir.
 *
 * <p>Akış:
 * <ol>
 *   <li>PreLogin → FirewallCheck + RateLimitCheck (hard filtreler)
 *   <li>Verified oyuncu → VerifiedBypassCheck → tüm pipeline atlanır
 *   <li>Doğrulanmamış → LoginEvent → limbo sunucusuna yönlendir
 *   <li>Limbo'da fizik/paket testleri (AtomGuard-Limbo companion plugin)
 *   <li>PASS → markVerified + ana sunucuya transfer
 *   <li>FAIL → kick + firewall violation
 * </ol>
 *
 * <p>Config anahtarı: {@code modules.dogrulama}
 */
public class VerificationModule extends VelocityModule {

    /** Plugin messaging kanalı — AtomGuard-Limbo ile iletişim */
    public static final MinecraftChannelIdentifier VERIFY_CHANNEL =
            MinecraftChannelIdentifier.create("atomguard", "verify");

    private VerifiedPlayerStore verifiedStore;
    private ConnectionQueue connectionQueue;
    private VerificationLimbo limbo;

    // Config değerleri
    private int maxConcurrent;
    private int timeoutSeconds;
    private int maxQueueSize;
    private int perIPMax;
    private int expiryDays;
    private String mainServerName;

    public VerificationModule(AtomGuardVelocity plugin) {
        super(plugin, "dogrulama");
    }

    @Override
    public int getPriority() { return 5; } // En erken başla

    @Override
    protected void onEnable() {
        maxConcurrent   = getConfigInt("limbo.max-concurrent", 50);
        timeoutSeconds  = getConfigInt("limbo.timeout-seconds", 15);
        maxQueueSize    = getConfigInt("queue.max-size", 200);
        perIPMax        = getConfigInt("queue.per-ip-max", 3);
        expiryDays      = getConfigInt("storage.expiry-days", 30);
        mainServerName  = getConfigString("main-server", "lobby");

        verifiedStore   = new VerifiedPlayerStore(plugin, expiryDays);
        connectionQueue = new ConnectionQueue(maxConcurrent, maxQueueSize, perIPMax);
        limbo           = new VerificationLimbo(plugin, verifiedStore, connectionQueue,
                                                 timeoutSeconds, mainServerName);

        // Plugin messaging kanalı kaydet
        plugin.getProxyServer().getChannelRegistrar().register(VERIFY_CHANNEL);

        // DB'den verified oyuncuları yükle
        verifiedStore.loadIntoCache();

        // Periyodik cleanup
        plugin.getProxyServer().getScheduler()
              .buildTask(plugin, () -> {
                  verifiedStore.cleanup();
                  connectionQueue.cleanup();
              })
              .repeat(10, TimeUnit.MINUTES)
              .schedule();

        logger.info("Limbo Doğrulama modülü aktif — maxConcurrent={}, timeout={}s, expiryDays={}",
                maxConcurrent, timeoutSeconds, expiryDays);
    }

    @Override
    protected void onDisable() {
        if (limbo != null) limbo.shutdown();
        try {
            plugin.getProxyServer().getChannelRegistrar().unregister(VERIFY_CHANNEL);
        } catch (Exception ignored) {}
    }

    // ───────────────────────────── API ─────────────────────────────

    /** Bu IP daha önce limbo doğrulamasından geçti mi? */
    public boolean isVerified(String ip) {
        return verifiedStore != null && verifiedStore.isVerified(ip);
    }

    public VerifiedPlayerStore getVerifiedStore() { return verifiedStore; }
    public ConnectionQueue getConnectionQueue()   { return connectionQueue; }
    public VerificationLimbo getLimbo()           { return limbo; }
}
