package com.atomguard.velocity.module.verification;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;
import com.atomguard.velocity.module.verification.storage.VerifiedPlayerStore;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

import java.util.concurrent.TimeUnit;

/**
 * Gelişmiş Limbo Doğrulama Modülü — v2 (IP + Kullanıcı Adı Çifti Sistemi).
 *
 * <h2>Çalışma Prensibi</h2>
 * <ol>
 *   <li><b>İlk Giriş:</b> Oyuncu → Limbo sunucusuna yönlendirilir → Fizik/paket testleri uygulanır</li>
 *   <li><b>Doğrulama Başarılı:</b> Oyuncu kick edilir → "IP adresiniz ve kullanıcı adınız doğrulandı, lütfen tekrar giriş yapın" mesajı gösterilir</li>
 *   <li><b>Kayıt:</b> IP + kullanıcı adı çifti veritabanına ve dosyaya kaydedilir</li>
 *   <li><b>Tekrar Giriş:</b> Aynı IP + aynı kullanıcı adı → doğrulama atlanır, direkt giriş</li>
 *   <li><b>IP Değişikliği:</b> Aynı kullanıcı adı + farklı IP → tekrar doğrulama gerekir</li>
 * </ol>
 *
 * <h2>Bypass Koruması</h2>
 * <ul>
 *   <li>IP + username çifti zorunlu — sadece IP veya sadece username yeterli DEĞİL</li>
 *   <li>Limbo fizik testleri — botlar fizik simülasyonu yapamaz</li>
 *   <li>Açık/exploit kullanılarak bypass edilemez</li>
 *   <li>Her IP değişikliğinde tekrar doğrulama — VPN rotasyonu koruması</li>
 *   <li>Dosya + DB çift katmanlı depolama</li>
 * </ul>
 *
 * <h2>Performans</h2>
 * <ul>
 *   <li>Caffeine cache ile hızlı lookup</li>
 *   <li>ConnectionQueue ile eş zamanlı doğrulama sınırlaması</li>
 *   <li>Yüzlerce bot aynı anda gelse bile kuyruğa alınır ve kontrol edilir</li>
 * </ul>
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
    private boolean kickAfterVerify;
    private boolean requireIPUsernamePair;

    public VerificationModule(AtomGuardVelocity plugin) {
        super(plugin, "dogrulama");
    }

    @Override
    public int getPriority() { return 5; }

    @Override
    protected void onEnable() {
        maxConcurrent        = getConfigInt("limbo.max-concurrent", 50);
        timeoutSeconds       = getConfigInt("limbo.timeout-seconds", 15);
        maxQueueSize         = getConfigInt("queue.max-size", 200);
        perIPMax             = getConfigInt("queue.per-ip-max", 3);
        expiryDays           = getConfigInt("storage.expiry-days", 30);
        mainServerName       = getConfigString("main-server", "lobby");
        kickAfterVerify      = getConfigBoolean("kick-after-verify", true);
        requireIPUsernamePair = getConfigBoolean("require-ip-username-pair", true);

        verifiedStore   = new VerifiedPlayerStore(plugin, expiryDays);
        connectionQueue = new ConnectionQueue(maxConcurrent, maxQueueSize, perIPMax);
        limbo           = new VerificationLimbo(plugin, verifiedStore, connectionQueue,
                                                 timeoutSeconds, mainServerName, kickAfterVerify);

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

        logger.info("Doğrulama Modülü v2 aktif — IP+Username çifti sistemi");
        logger.info("  maxConcurrent={}, timeout={}s, expiryDays={}, kickAfterVerify={}, requirePair={}",
                maxConcurrent, timeoutSeconds, expiryDays, kickAfterVerify, requireIPUsernamePair);
    }

    @Override
    protected void onDisable() {
        if (limbo != null) limbo.shutdown();
        try {
            plugin.getProxyServer().getChannelRegistrar().unregister(VERIFY_CHANNEL);
        } catch (Exception ignored) {}
    }

    // ─────────────────────────── API ───────────────────────────

    /**
     * Bu IP + username çifti daha önce doğrulanmış mı?
     *
     * <p>Doğrulama mantığı:
     * <ul>
     *   <li>IP + username çifti eşleşiyorsa → doğrulanmış (true)</li>
     *   <li>Kullanıcı daha önce doğrulanmış ama farklı IP'den geliyorsa → doğrulanMAMIŞ (false)</li>
     *   <li>Hiç doğrulanmamışsa → doğrulanMAMIŞ (false)</li>
     * </ul>
     */
    public boolean isVerifiedPair(String ip, String username) {
        if (verifiedStore == null) return false;
        if (!requireIPUsernamePair) {
            // Eski mod: sadece IP kontrolü
            return verifiedStore.isVerified(ip);
        }
        return verifiedStore.isVerifiedPair(ip, username);
    }

    /**
     * Geriye dönük uyumluluk: sadece IP ile kontrol.
     * Bu yöntem pipeline'ın VerifiedBypassCheck'inde kullanılır.
     *
     * <p>requireIPUsernamePair=true ise bu yöntem her zaman false döner
     * (pipeline'da IP+username kontrolü ConnectionListener'da yapılır).
     */
    public boolean isVerified(String ip) {
        if (verifiedStore == null) return false;
        if (requireIPUsernamePair) {
            // Pair modunda sadece IP ile bypass yapma — her zaman false
            // Gerçek kontrol ConnectionListener'da isVerifiedPair ile yapılır
            return false;
        }
        return verifiedStore.isVerified(ip);
    }

    /**
     * Bu kullanıcı adı farklı bir IP'den mi geliyor?
     * IP değişikliği tespiti.
     */
    public boolean isIPChanged(String ip, String username) {
        if (verifiedStore == null) return false;
        String lastIP = verifiedStore.getLastVerifiedIP(username);
        if (lastIP == null) return false; // Hiç doğrulanmamış
        return !lastIP.equals(ip);
    }

    /**
     * Bu kullanıcı adı daha önce herhangi bir IP ile doğrulanmış mı?
     */
    public boolean hasEverBeenVerified(String username) {
        return verifiedStore != null && verifiedStore.hasEverBeenVerified(username);
    }

    public boolean isKickAfterVerify() { return kickAfterVerify; }
    public boolean isRequireIPUsernamePair() { return requireIPUsernamePair; }
    public VerifiedPlayerStore getVerifiedStore() { return verifiedStore; }
    public ConnectionQueue getConnectionQueue() { return connectionQueue; }
    public VerificationLimbo getLimbo() { return limbo; }
}
