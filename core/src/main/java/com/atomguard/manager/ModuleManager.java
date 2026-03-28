package com.atomguard.manager;

import com.atomguard.AtomGuard;
import com.atomguard.api.module.IModule;
import com.atomguard.api.module.IModuleManager;
import com.atomguard.module.AbstractModule;
import com.atomguard.module.BotProtectionModule;
import com.atomguard.module.antibot.AntiBotModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Level;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages the lifecycle of all AtomGuard exploit-fixer modules.
 *
 * <p>This class implements {@link IModuleManager} (the public API interface) and is responsible
 * for registering, enabling, disabling, and looking up modules. Modules are stored in a
 * thread-safe {@link ConcurrentHashMap} indexed by both their Turkish config-key name and
 * their Java class.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li><b>Registration:</b> Modules are registered during {@link com.atomguard.AtomGuard#registerModules()}
 *       and can be looked up by name or class.</li>
 *   <li><b>Enable/Disable:</b> {@code enableAllModules()} reads each module's {@code aktif} config flag;
 *       on failure, the module is rolled back via {@code onDisable()}. Individual modules can be
 *       toggled at runtime.</li>
 *   <li><b>Statistics:</b> Aggregates per-module block counts for the statistics and web panel systems.</li>
 * </ul>
 *
 * @see com.atomguard.module.AbstractModule
 * @see com.atomguard.api.module.IModuleManager
 */
public class ModuleManager implements IModuleManager {

    private final AtomGuard plugin;

    // Thread-safe modül storage
    private final ConcurrentHashMap<String, AbstractModule> modules;
    private final ConcurrentHashMap<Class<? extends AbstractModule>, AbstractModule> modulesByClass;

    /**
     * ModuleManager constructor
     *
     * @param plugin Ana plugin instance
     */
    public ModuleManager(@NotNull AtomGuard plugin) {
        this.plugin = plugin;
        this.modules = new ConcurrentHashMap<>();
        this.modulesByClass = new ConcurrentHashMap<>();
    }

    /**
     * Modül kaydeder
     *
     * @param module Kaydedilecek modül
     * @return Başarılı ise true
     */
    public boolean registerModule(@NotNull AbstractModule module) {
        String moduleName = module.getName();

        if (moduleName == null || moduleName.isEmpty()) {
            plugin.getLogger().warning("Modül kaydı reddedildi: getName() null veya boş döndü (" + module.getClass().getSimpleName() + ")");
            return false;
        }

        // Zaten kayıtlı mı kontrol et
        if (modules.containsKey(moduleName)) {
            plugin.getLogger().warning("Modül zaten kayıtlı: " + moduleName);
            return false;
        }

        // Modülü kaydet
        modules.put(moduleName, module);
        modulesByClass.put(module.getClass(), module);

        plugin.getLogger().info("Modül kaydedildi: " + moduleName);
        return true;
    }

    /**
     * Modül kaydını kaldırır
     *
     * @param moduleName Modül adı
     * @return Başarılı ise true
     */
    public boolean unregisterModule(@NotNull String moduleName) {
        AbstractModule module = modules.remove(moduleName);
        if (module != null) {
            modulesByClass.remove(module.getClass());
            module.onDisable();
            plugin.getLogger().info("Modül kaydı kaldırıldı: " + moduleName);
            return true;
        }
        return false;
    }

    /**
     * Tüm modülleri etkinleştirir
     */
    public void enableAllModules() {
        int enabledCount = 0;

        for (AbstractModule module : modules.values()) {
            try {
                if (module.isEnabled()) {
                    continue; // Zaten aktif
                }

                // Config'den modül durumunu kontrol et
                String configKey = module.getName();
                boolean shouldEnable = plugin.getConfigManager().isModuleEnabled(configKey);

                if (shouldEnable) {
                    module.onEnable();
                    enabledCount++;
                    plugin.getLogManager().info("Modül etkinleştirildi: " + module.getName());
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Modül etkinleştirilirken hata: " + module.getName(), e);
                // Rollback: yarı-başlatılmış modülü temiz duruma geri al
                try { module.onDisable(); } catch (Exception ex) {
                    plugin.getLogger().log(Level.SEVERE, "Modül rollback sırasında hata: " + module.getName(), ex);
                }
            }
        }

        plugin.getLogger().info(enabledCount + " modül etkinleştirildi.");

        // BotProtectionModule (deprecated) ile AntiBotModule çakışma kontrolü
        BotProtectionModule legacy = getModule(BotProtectionModule.class);
        AntiBotModule modern = getModule(AntiBotModule.class);
        if (legacy != null && legacy.isEnabled() && modern != null && modern.isEnabled()) {
            plugin.getLogger().warning(
                "[ModuleManager] BotProtectionModule (legacy) devre dışı bırakıldı — AntiBotModule zaten aktif. " +
                "İkisi aynı anda çalışırsa çift-kick/ban riski oluşur.");
            try {
                legacy.onDisable();
            } catch (Exception e) {
                plugin.getLogger().warning("[ModuleManager] BotProtectionModule deaktif edilirken hata: " + e.getMessage());
            }
        }
    }

    /**
     * Tüm modülleri devre dışı bırakır
     */
    public void disableAllModules() {
        int disabledCount = 0;

        for (AbstractModule module : modules.values()) {
            try {
                if (!module.isEnabled()) {
                    continue; // Zaten devre dışı
                }

                module.onDisable();
                disabledCount++;
                plugin.getLogManager().info("Modül devre dışı bırakıldı: " + module.getName());

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Modül devre dışı bırakılırken hata: " + module.getName(), e);
            }
        }

        plugin.getLogger().info(disabledCount + " modül devre dışı bırakıldı.");
    }

    /**
     * Belirli bir modülü etkinleştirir
     *
     * @param moduleName Modül adı
     * @return Başarılı ise true
     */
    public boolean enableModule(@NotNull String moduleName) {
        AbstractModule module = getModule(moduleName);
        if (module == null) {
            return false;
        }

        if (module.isEnabled()) {
            return true; // Zaten etkin
        }

        try {
            module.onEnable();
            plugin.getLogManager().info("Modül etkinleştirildi: " + moduleName);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Modül etkinleştirilirken hata: " + moduleName, e);
            try { module.onDisable(); } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Modül rollback sırasında hata: " + moduleName, ex);
            }
            return false;
        }
    }

    /**
     * Belirli bir modülü devre dışı bırakır
     *
     * @param moduleName Modül adı
     * @return Başarılı ise true
     */
    public boolean disableModule(@NotNull String moduleName) {
        AbstractModule module = getModule(moduleName);
        if (module == null) {
            return false;
        }

        if (!module.isEnabled()) {
            return true; // Zaten devre dışı
        }

        try {
            module.onDisable();
            plugin.getLogManager().info("Modül devre dışı bırakıldı: " + moduleName);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Modül devre dışı bırakılırken hata: " + moduleName, e);
            return false;
        }
    }

    /**
     * Modülün durumunu değiştirir (toggle)
     *
     * @param moduleName Modül adı
     * @return Yeni durum (true = etkin, false = devre dışı)
     */
    public boolean toggleModule(@NotNull String moduleName) {
        AbstractModule module = getModule(moduleName);
        if (module == null) {
            return false;
        }

        boolean newState = module.toggle();

        // Config'i güncelle
        plugin.getConfigManager().set("modules." + moduleName + ".enabled", newState);
        plugin.getConfigManager().saveConfig();

        return newState;
    }

    /**
     * İsme göre modül alır
     *
     * @param moduleName Modül adı
     * @return Modül instance veya null
     */
    @Nullable
    public AbstractModule getModule(@NotNull String moduleName) {
        return modules.get(moduleName);
    }

    /**
     * Class'a göre modül alır
     *
     * @param moduleClass Modül class'ı
     * @param <T> Modül tipi
     * @return Modül instance veya null
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends AbstractModule> T getModule(@NotNull Class<T> moduleClass) {
        return (T) modulesByClass.get(moduleClass);
    }

    /**
     * Tüm modülleri alır
     *
     * @return Modül collection
     */
    @NotNull
    public Collection<AbstractModule> getAllModules() {
        return Collections.unmodifiableCollection(modules.values());
    }

    /**
     * Tüm modül isimlerini alır
     *
     * @return Modül isimleri
     */
    @NotNull
    public Set<String> getModuleNames() {
        return Collections.unmodifiableSet(modules.keySet());
    }

    /**
     * Aktif modülleri alır
     *
     * @return Aktif modül listesi
     */
    @NotNull
    public List<AbstractModule> getEnabledModules() {
        return Collections.unmodifiableList(modules.values().stream()
            .filter(AbstractModule::isEnabled)
            .collect(Collectors.toList()));
    }

    /**
     * Devre dışı modülleri alır
     *
     * @return Devre dışı modül listesi
     */
    @NotNull
    public List<AbstractModule> getDisabledModules() {
        return Collections.unmodifiableList(modules.values().stream()
            .filter(module -> !module.isEnabled())
            .collect(Collectors.toList()));
    }

    /**
     * Toplam aktif modül sayısını alır
     *
     * @return Aktif modül sayısı
     */
    public int getEnabledModuleCount() {
        return (int) modules.values().stream()
            .filter(AbstractModule::isEnabled)
            .count();
    }

    /**
     * Toplam modül sayısını alır
     *
     * @return Toplam modül sayısı
     */
    public int getTotalModuleCount() {
        return modules.size();
    }

    /**
     * Tüm modüllerin toplam engelleme sayısını alır
     *
     * @return Toplam engelleme sayısı
     */
    public long getTotalBlockedCount() {
        return modules.values().stream()
            .mapToLong(AbstractModule::getBlockedCount)
            .sum();
    }

    /**
     * Modül var mı kontrol eder
     *
     * @param moduleName Modül adı
     * @return Var ise true
     */
    public boolean hasModule(@NotNull String moduleName) {
        return modules.containsKey(moduleName);
    }

    /**
     * Modülün aktif olup olmadığını kontrol eder
     *
     * @param moduleName Modül adı
     * @return Aktif ise true
     */
    public boolean isModuleEnabled(@NotNull String moduleName) {
        AbstractModule module = getModule(moduleName);
        return module != null && module.isEnabled();
    }

    /**
     * Tüm modülleri yeniden yükler
     * Config'e göre durumları günceller
     */
    public void reloadModules() {
        plugin.getLogger().info("Modüller yeniden yükleniyor...");

        for (AbstractModule module : modules.values()) {
            try {
                String moduleName = module.getName();
                boolean shouldEnable = plugin.getConfigManager().isModuleEnabled(moduleName);
                boolean currentlyEnabled = module.isEnabled();

                if (shouldEnable && !currentlyEnabled) {
                    module.onEnable();
                    plugin.getLogManager().info("Modül etkinleştirildi: " + moduleName);
                } else if (!shouldEnable && currentlyEnabled) {
                    module.onDisable();
                    plugin.getLogManager().info("Modül devre dışı bırakıldı: " + moduleName);
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Modül yeniden yüklenirken hata: " + module.getName(), e);
            }
        }

        plugin.getLogger().info("Modüller yeniden yüklendi. Aktif: " + getEnabledModuleCount() + "/" + getTotalModuleCount());
    }

    /**
     * Modül istatistiklerini Map olarak döner
     *
     * @return Modül istatistikleri (ModülAdı -> EngellemeSayısı)
     */
    @NotNull
    public Map<String, Long> getModuleStatistics() {
        Map<String, Long> stats = new HashMap<>();
        for (AbstractModule module : modules.values()) {
            stats.put(module.getName(), module.getBlockedCount());
        }
        return Collections.unmodifiableMap(stats);
    }

    /**
     * Tüm modüllerin istatistiklerini sıfırlar
     */
    public void resetAllStatistics() {
        modules.values().forEach(AbstractModule::resetBlockedCount);
        plugin.getLogger().info("Tüm modül istatistikleri sıfırlandı.");
    }

    /**
     * Belirli bir modülün istatistiklerini sıfırlar
     *
     * @param moduleName Modül adı
     * @return Başarılı ise true
     */
    public boolean resetModuleStatistics(@NotNull String moduleName) {
        AbstractModule module = getModule(moduleName);
        if (module != null) {
            module.resetBlockedCount();
            return true;
        }
        return false;
    }
}
