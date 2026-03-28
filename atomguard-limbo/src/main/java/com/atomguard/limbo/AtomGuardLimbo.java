package com.atomguard.limbo;

import com.atomguard.limbo.protocol.VelocityBridge;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * AtomGuard Limbo — Velocity bot doğrulama companion plugin'i.
 *
 * <p>Bu plugin Velocity proxy'nin yönlendirdiği oyuncuları karşılar,
 * fizik simülasyonu ve paket analizi ile gerçek MC client'ı olup
 * olmadıklarını doğrular, sonucu plugin messaging ile Velocity'ye bildirir.
 *
 * <p>Kurulum:
 * <ol>
 *   <li>Bu jar'ı limbo sunucunun {@code plugins/} klasörüne koy
 *   <li>Limbo sunucuyu Velocity {@code velocity.toml}'a {@code "limbo"} adıyla ekle
 *   <li>Velocity'de {@code modules.dogrulama.enabled: true} yap
 * </ol>
 */
public class AtomGuardLimbo extends JavaPlugin implements PluginMessageListener {

    private LimboWorldManager worldManager;
    private VerificationHandler verificationHandler;
    private VelocityBridge velocityBridge;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        int timeoutSeconds = getConfig().getInt("verification.timeout-seconds", 15);

        // Velocity kanallarını kaydet
        getServer().getMessenger().registerOutgoingPluginChannel(this, VelocityBridge.CHANNEL);
        // Brand kanalını dinle (brand paketi = client gerçek)
        getServer().getMessenger().registerIncomingPluginChannel(this, "minecraft:brand", this);

        velocityBridge = new VelocityBridge(this);

        worldManager = new LimboWorldManager(this);
        if (worldManager.getOrCreateWorld() == null) {
            getLogger().severe("Limbo dünyası oluşturulamadı! Plugin devre dışı bırakılıyor.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        verificationHandler = new VerificationHandler(this, worldManager, velocityBridge, timeoutSeconds);
        getServer().getPluginManager().registerEvents(verificationHandler, this);

        // Güvenlik: oyuncuların komut kullanmasını engelle
        getServer().getPluginManager().registerEvents(new SecurityListener(this), this);

        getLogger().info("AtomGuard Limbo başlatıldı — timeout=" + timeoutSeconds + "s");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getLogger().info("AtomGuard Limbo kapatıldı.");
    }

    /**
     * "minecraft:brand" kanalından brand paketi geldiğinde tetiklenir.
     * Brand paketi alan oyuncu gerçek Minecraft client'ı kullanıyordur.
     */
    @Override
    public void onPluginMessageReceived(String channel, org.bukkit.entity.Player player, byte[] message) {
        if ("minecraft:brand".equals(channel) && verificationHandler != null) {
            verificationHandler.onBrandReceived(player.getUniqueId());
        }
    }

    // ───────────────────────────── Getters ─────────────────────────────

    public LimboWorldManager getWorldManager()             { return worldManager; }
    public VerificationHandler getVerificationHandler()   { return verificationHandler; }
    public VelocityBridge getVelocityBridge()             { return velocityBridge; }
}
