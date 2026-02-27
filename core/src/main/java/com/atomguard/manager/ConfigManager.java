package com.atomguard.manager;

import com.atomguard.AtomGuard;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Yapılandırma dosyalarını yöneten manager sınıfı
 * Thread-safe ve performanslı config yönetimi sağlar
 *
 * @author AtomGuard Team
 * @version 1.2.0
 */
public class ConfigManager {

    private final AtomGuard plugin;
    private FileConfiguration config;
    private FileConfiguration messages;

    // Cache sistemi - config değerlerini önbellekte tutar
    private final ConcurrentHashMap<String, Object> configCache;

    /**
     * ConfigManager constructor
     *
     * @param plugin Ana plugin instance
     */
    public ConfigManager(@NotNull AtomGuard plugin) {
        this.plugin = plugin;
        this.configCache = new ConcurrentHashMap<>();
    }

    /**
     * Tüm yapılandırma dosyalarını yükler
     * Config.yml ve messages.yml dosyalarını başlatır
     */
    public void load() {
        // Config dosyasını yükle
        loadConfig();

        // Versiyon kontrolü ve otomatik güncelleme (Sprint 3)
        checkConfigVersion();

        // Messages dosyasını yükle
        loadMessages();

        // Cache'i temizle
        configCache.clear();

        plugin.getLogger().info("Yapılandırma dosyaları başarıyla yüklendi.");
    }

    private void checkConfigVersion() {
        String currentVersion = "1.2.4";
        String configVersion = config.getString("config-version", "1.0.0");

        if (!configVersion.equals(currentVersion)) {
            plugin.getLogger().info("Config versiyonu eski (" + configVersion + " → " + currentVersion + "), güncelleniyor...");

            if (plugin.getMigrationManager() != null) {
                plugin.getMigrationManager().migrate(config, configVersion, currentVersion);
            }

            config.set("config-version", currentVersion);
            saveConfig();
        }
    }

    /**
     * config.yml dosyasını yükler veya oluşturur
     */
    private void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");

        // Dosya yoksa kaynaklardan kopyala
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        this.config = YamlConfiguration.loadConfiguration(configFile);

        // Varsayılan değerleri yükle
        InputStream defaultStream = plugin.getResource("config.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
            );
            config.setDefaults(defaultConfig);
        }
    }

    /**
     * messages.yml dosyasını yükler veya oluşturur
     */
    private void loadMessages() {
        String lang = config.getString("genel.dil", "tr").toLowerCase();
        String fileName = "messages_" + lang + ".yml";
        File messagesFile = new File(plugin.getDataFolder(), fileName);

        // Dosya yoksa kaynaklardan kopyala
        if (!messagesFile.exists()) {
            try {
                plugin.saveResource(fileName, false);
            } catch (Exception e) {
                // Fallback to Turkish if requested language not found in resources
                plugin.getLogger().warning("Dil dosyası bulunamadı: " + fileName + " - Türkçe kullanılıyor.");
                fileName = "messages_tr.yml";
                messagesFile = new File(plugin.getDataFolder(), fileName);
                if (!messagesFile.exists()) {
                    plugin.saveResource("messages_tr.yml", false);
                }
            }
        }

        this.messages = YamlConfiguration.loadConfiguration(messagesFile);

        // Varsayılan değerleri yükle
        InputStream defaultStream = plugin.getResource(fileName);
        if (defaultStream == null) {
            defaultStream = plugin.getResource("messages_tr.yml");
        }
        
        if (defaultStream != null) {
            YamlConfiguration defaultMessages = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
            );
            messages.setDefaults(defaultMessages);
        }
    }

    /**
     * config.yml dosyasını kaydeder
     *
     * @return Başarılı ise true
     */
    public boolean saveConfig() {
        try {
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            config.save(configFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Config.yml kaydedilemedi!", e);
            return false;
        }
    }

    /**
     * Yapılandırmayı yeniden yükler
     */
    public void reload() {
        load();
    }

    /**
     * Config.yml'den string değer alır
     *
     * @param path Config yolu (örn: "genel.onek")
     * @return String değer veya null
     */
    @Nullable
    public String getString(@NotNull String path) {
        return config.getString(path);
    }

    /**
     * Config.yml'den string değer alır, yoksa varsayılan döner
     *
     * @param path Config yolu
     * @param def Varsayılan değer
     * @return String değer
     */
    @NotNull
    public String getString(@NotNull String path, @NotNull String def) {
        return config.getString(path, def);
    }

    /**
     * Config.yml'den int değer alır
     *
     * @param path Config yolu
     * @return Int değer
     */
    public int getInt(@NotNull String path) {
        return config.getInt(path);
    }

    /**
     * Config.yml'den int değer alır, yoksa varsayılan döner
     *
     * @param path Config yolu
     * @param def Varsayılan değer
     * @return Int değer
     */
    public int getInt(@NotNull String path, int def) {
        return config.getInt(path, def);
    }

    /**
     * Config.yml'den long değer alır
     *
     * @param path Config yolu
     * @return Long değer
     */
    public long getLong(@NotNull String path) {
        return config.getLong(path);
    }

    /**
     * Config.yml'den long değer alır, yoksa varsayılan döner
     *
     * @param path Config yolu
     * @param def Varsayılan değer
     * @return Long değer
     */
    public long getLong(@NotNull String path, long def) {
        return config.getLong(path, def);
    }

    /**
     * Config.yml'den double değer alır
     *
     * @param path Config yolu
     * @return Double değer
     */
    public double getDouble(@NotNull String path) {
        return config.getDouble(path);
    }

    /**
     * Config.yml'den double değer alır, yoksa varsayılan döner
     *
     * @param path Config yolu
     * @param def Varsayılan değer
     * @return Double değer
     */
    public double getDouble(@NotNull String path, double def) {
        return config.getDouble(path, def);
    }

    /**
     * Config.yml'den boolean değer alır
     *
     * @param path Config yolu
     * @return Boolean değer
     */
    public boolean getBoolean(@NotNull String path) {
        return config.getBoolean(path);
    }

    /**
     * Config.yml'den boolean değer alır, yoksa varsayılan döner
     *
     * @param path Config yolu
     * @param def Varsayılan değer
     * @return Boolean değer
     */
    public boolean getBoolean(@NotNull String path, boolean def) {
        return config.getBoolean(path, def);
    }

    /**
     * Config.yml'den liste alır
     *
     * @param path Config yolu
     * @return String listesi veya null
     */
    @Nullable
    public List<String> getStringList(@NotNull String path) {
        return config.getStringList(path);
    }

    /**
     * messages.yml'den mesaj alır
     *
     * @param path Mesaj yolu (örn: "genel.onek")
     * @return Mesaj string'i veya null
     */
    @Nullable
    public String getMessage(@NotNull String path) {
        return messages.getString(path);
    }

    /**
     * messages.yml'den mesaj alır, yoksa varsayılan döner
     *
     * @param path Mesaj yolu
     * @param def Varsayılan mesaj
     * @return Mesaj string'i
     */
    @NotNull
    public String getMessage(@NotNull String path, @NotNull String def) {
        return messages.getString(path, def);
    }

    /**
     * Config.yml'e değer ayarlar
     *
     * @param path Config yolu
     * @param value Ayarlanacak değer
     */
    public void set(@NotNull String path, @Nullable Object value) {
        config.set(path, value);
        configCache.remove(path); // Cache'ten temizle
    }

    /**
     * Debug modunun aktif olup olmadığını kontrol eder
     *
     * @return Debug aktif ise true
     */
    public boolean isDebugEnabled() {
        return getBoolean("genel.debug", false);
    }

    /**
     * Log sisteminin aktif olup olmadığını kontrol eder
     *
     * @return Log aktif ise true
     */
    public boolean isLogEnabled() {
        return getBoolean("genel.log.aktif", true);
    }

    /**
     * Log klasörü yolunu alır
     *
     * @return Log klasörü yolu
     */
    @NotNull
    public String getLogFolder() {
        return getString("genel.log.klasor", "logs/atomguard");
    }

    /**
     * Günlük log dosyası oluşturma durumunu alır
     *
     * @return Günlük dosya aktif ise true
     */
    public boolean isDailyLogEnabled() {
        return getBoolean("genel.log.gunluk-dosya", true);
    }

    /**
     * Log saklama süresini alır (gün cinsinden)
     *
     * @return Saklama süresi (gün)
     */
    public int getLogRetentionDays() {
        return getInt("genel.log.log-saklama-gunu", 7);
    }

    /**
     * Modülün aktif olup olmadığını kontrol eder
     *
     * @param moduleName Modül adı (örn: "kitap-crash")
     * @return Modül aktif ise true
     */
    public boolean isModuleEnabled(@NotNull String moduleName) {
        return getBoolean("moduller." + moduleName + ".aktif", false);
    }

    /**
     * Ana config nesnesini alır
     *
     * @return FileConfiguration nesnesi
     */
    @NotNull
    public FileConfiguration getConfig() {
        return config;
    }

    /**
     * Messages config nesnesini alır
     *
     * @return FileConfiguration nesnesi
     */
    @NotNull
    public FileConfiguration getMessages() {
        return messages;
    }
}
