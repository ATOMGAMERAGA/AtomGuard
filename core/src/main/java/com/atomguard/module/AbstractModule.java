package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.atomguard.api.module.IModule;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tüm modüllerin extend edeceği soyut modül sınıfı
 * Ortak modül fonksiyonlarını ve alanlarını sağlar
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public abstract class AbstractModule implements IModule {

    protected final AtomGuard plugin;
    protected final String name;
    protected final String description;

    // Modül durumu (thread-safe)
    protected volatile boolean enabled;

    // Engelleme sayacı (thread-safe)
    protected final AtomicLong blockedCount;

    /**
     * AbstractModule constructor
     *
     * @param plugin Ana plugin instance
     * @param name Modül adı (config'deki key ile aynı olmalı)
     * @param description Modül açıklaması
     */
    public AbstractModule(@NotNull AtomGuard plugin, @NotNull String name, @NotNull String description) {
        this.plugin = plugin;
        this.name = name;
        this.description = description;
        this.enabled = false;
        this.blockedCount = new AtomicLong(0);
    }

        /**

         * Modül etkinleştirildiğinde çağrılır

         * Alt sınıflar bu metodu override ederek kendi başlatma kodlarını ekler

         */

        public void onEnable() {

            this.enabled = true;

            plugin.getLogManager().info(name + " modülü etkinleştirildi.");

        }

    

        /**

         * Modül devre dışı bırakıldığında çağrılır

         * Alt sınıflar bu metodu override ederek kendi temizleme kodlarını ekler

         */

        public void onDisable() {

            this.enabled = false;

            plugin.getLogManager().info(name + " modülü devre dışı bırakıldı.");

        }

    /**
     * Modülün aktif olup olmadığını kontrol eder
     *
     * @return Aktif ise true
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Modülün durumunu değiştirir (toggle)
     *
     * @return Yeni durum (true = etkin, false = devre dışı)
     */
    public boolean toggle() {
        if (enabled) {
            onDisable();
        } else {
            onEnable();
        }
        return enabled;
    }

    /**
     * Modül adını alır
     *
     * @return Modül adı
     */
    @NotNull
    public String getName() {
        return name;
    }

    /**
     * Modül açıklamasını alır
     *
     * @return Modül açıklaması
     */
    @NotNull
    public String getDescription() {
        return description;
    }

    /**
     * Toplam engelleme sayısını alır
     *
     * @return Engelleme sayısı
     */
    public long getBlockedCount() {
        return blockedCount.get();
    }

    /**
     * Engelleme sayısını sıfırlar
     */
    public void resetBlockedCount() {
        blockedCount.set(0);
    }

    /**
     * Engelleme sayısını artırır
     *
     * @return Yeni engelleme sayısı
     */
    protected long incrementBlockedCount() {
        long count = blockedCount.incrementAndGet();
        // Statistics recording
        if (plugin.getStatisticsManager() != null) {
            plugin.getStatisticsManager().recordBlock(name);
        }
        return count;
    }

    /**
     * Engelleme sayısını belirli bir miktar artırır
     *
     * @param amount Artırılacak miktar
     * @return Yeni engelleme sayısı
     */
    protected long addBlockedCount(long amount) {
        return blockedCount.addAndGet(amount);
    }

    /**
     * Modül hakkında bilgi döner
     *
     * @return Modül bilgisi
     */
    @Override
    public String toString() {
        return String.format("Module{name='%s', enabled=%s, blocked=%d}",
            name, enabled, blockedCount.get());
    }

    /**
     * Ana plugin instance alır
     *
     * @return AtomGuard instance
     */
    @NotNull
    public AtomGuard getPlugin() {
        return plugin;
    }

    /**
     * Config'den boolean değer alır
     *
     * @param key Config anahtarı (moduller.{modül_adı}. otomatik eklenir)
     * @param def Varsayılan değer
     * @return Config değeri
     */
    public boolean getConfigBoolean(@NotNull String key, boolean def) {
        return plugin.getConfigManager().getBoolean("moduller." + name + "." + key, def);
    }

    /**
     * Config'den int değer alır
     *
     * @param key Config anahtarı
     * @param def Varsayılan değer
     * @return Config değeri
     */
    public int getConfigInt(@NotNull String key, int def) {
        return plugin.getConfigManager().getInt("moduller." + name + "." + key, def);
    }

    /**
     * Config'den long değer alır
     *
     * @param key Config anahtarı
     * @param def Varsayılan değer
     * @return Config değeri
     */
    public long getConfigLong(@NotNull String key, long def) {
        return plugin.getConfigManager().getLong("moduller." + name + "." + key, def);
    }

    /**
     * Config'den double değer alır
     *
     * @param key Config anahtarı
     * @param def Varsayılan değer
     * @return Config değeri
     */
    public double getConfigDouble(@NotNull String key, double def) {
        return plugin.getConfigManager().getDouble("moduller." + name + "." + key, def);
    }

    /**
     * Config'den string değer alır
     *
     * @param key Config anahtarı
     * @param def Varsayılan değer
     * @return Config değeri
     */
    @NotNull
    public String getConfigString(@NotNull String key, @NotNull String def) {
        return plugin.getConfigManager().getString("moduller." + name + "." + key, def);
    }

    /**
     * Debug modunda log yazar
     *
     * @param message Log mesajı
     */
    public void debug(@NotNull String message) {
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogManager().debug("[" + name + "] " + message);
        }
    }

    /**
     * Info seviyesinde log yazar
     *
     * @param message Log mesajı
     */
    public void info(@NotNull String message) {
        plugin.getLogManager().info("[" + name + "] " + message);
    }

    /**
     * Warning seviyesinde log yazar
     *
     * @param message Log mesajı
     */
    public void warning(@NotNull String message) {
        plugin.getLogManager().warning("[" + name + "] " + message);
    }

    /**
     * Error seviyesinde log yazar
     *
     * @param message Log mesajı
     */
    public void error(@NotNull String message) {
        plugin.getLogManager().error("[" + name + "] " + message);
    }

    /**
     * Exploit engelleme logu yazar
     *
     * @param playerName Oyuncu adı
     * @param details Detaylar
     */
    public void logExploit(@NotNull String playerName, @NotNull String details) {
        plugin.getLogManager().logExploit(playerName, name, details);
        // Discord webhook notification
        if (plugin.getDiscordWebhookManager() != null) {
            plugin.getDiscordWebhookManager().notifyExploitBlocked(name, playerName, details);
        }
        // Web panel event recording
        if (plugin.getWebPanel() != null) {
            plugin.getWebPanel().recordEvent(name, playerName, details);
        }
    }
}
