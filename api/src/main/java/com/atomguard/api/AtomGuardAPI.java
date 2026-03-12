package com.atomguard.api;

import com.atomguard.api.forensics.IForensicsService;
import com.atomguard.api.module.IModuleManager;
import com.atomguard.api.pipeline.IConnectionPipeline;
import com.atomguard.api.stats.IStatisticsProvider;
import com.atomguard.api.storage.IStorageProvider;
import com.atomguard.api.trust.ITrustService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * AtomGuard Public API - Ana erişim noktası
 * Diğer pluginlerin AtomGuard ile entegre olmasını sağlar.
 *
 * @author AtomGuard Team
 * @since 1.0.0
 */
public final class AtomGuardAPI {

    private static AtomGuardAPI instance;

    private final IModuleManager moduleManager;
    private final IStorageProvider storageProvider;
    private final IStatisticsProvider statisticsProvider;
    private final IReputationService reputationService;
    private final ITrustService trustService;
    private final IForensicsService forensicsService;
    private final IConnectionPipeline connectionPipeline;
    private final String version;

    /**
     * API instance oluşturur. Sadece core plugin tarafından çağrılmalıdır.
     *
     * @deprecated Eski 5-parametre constructor. Yeni 7-parametre versiyonunu kullanın.
     */
    @Deprecated
    public AtomGuardAPI(
            @NotNull IModuleManager moduleManager,
            @Nullable IStorageProvider storageProvider,
            @NotNull IStatisticsProvider statisticsProvider,
            @Nullable IReputationService reputationService,
            @NotNull String version
    ) {
        this(moduleManager, storageProvider, statisticsProvider, reputationService, null, null, null, version);
    }

    /**
     * API instance oluşturur. Sadece core plugin tarafından çağrılmalıdır.
     *
     * @since 2.0.0
     */
    public AtomGuardAPI(
            @NotNull IModuleManager moduleManager,
            @Nullable IStorageProvider storageProvider,
            @NotNull IStatisticsProvider statisticsProvider,
            @Nullable IReputationService reputationService,
            @Nullable ITrustService trustService,
            @Nullable IForensicsService forensicsService,
            @Nullable IConnectionPipeline connectionPipeline,
            @NotNull String version
    ) {
        this.moduleManager = moduleManager;
        this.storageProvider = storageProvider;
        this.statisticsProvider = statisticsProvider;
        this.reputationService = reputationService;
        this.trustService = trustService;
        this.forensicsService = forensicsService;
        this.connectionPipeline = connectionPipeline;
        this.version = version;
        instance = this;
    }

    /**
     * API singleton instance alır.
     *
     * @return API instance
     * @throws IllegalStateException Plugin henüz yüklenmemişse
     */
    @NotNull
    public static AtomGuardAPI getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AtomGuard API henüz başlatılmadı!");
        }
        return instance;
    }

    /**
     * API'nin başlatılıp başlatılmadığını kontrol eder.
     *
     * @return Başlatılmışsa true
     */
    public static boolean isAvailable() {
        return instance != null;
    }

    /**
     * Modül yönetim sistemi.
     *
     * @return IModuleManager instance
     */
    @NotNull
    public IModuleManager getModuleManager() {
        return moduleManager;
    }

    /**
     * Depolama sağlayıcısı (MySQL/SQLite/File).
     * Sprint 2'de implement edilecek, o zamana kadar null dönebilir.
     *
     * @return IStorageProvider instance veya null
     */
    @Nullable
    public IStorageProvider getStorageProvider() {
        return storageProvider;
    }

    /**
     * İstatistik sağlayıcısı.
     *
     * @return IStatisticsProvider instance
     */
    @NotNull
    public IStatisticsProvider getStatistics() {
        return statisticsProvider;
    }

    /**
     * IP reputation servisi.
     * Yapılandırılmamışsa null dönebilir.
     *
     * @return IReputationService instance veya null
     */
    @Nullable
    public IReputationService getReputationService() {
        return reputationService;
    }

    /**
     * Güven puanı servisi.
     *
     * @return ITrustService instance veya null
     * @since 2.0.0
     */
    @Nullable
    public ITrustService getTrustService() {
        return trustService;
    }

    /**
     * Forensics kayıt servisi.
     *
     * @return IForensicsService instance veya null
     * @since 2.0.0
     */
    @Nullable
    public IForensicsService getForensicsService() {
        return forensicsService;
    }

    /**
     * Bağlantı pipeline servisi.
     *
     * @return IConnectionPipeline instance veya null
     * @since 2.0.0
     */
    @Nullable
    public IConnectionPipeline getConnectionPipeline() {
        return connectionPipeline;
    }

    /**
     * Plugin versiyonu.
     *
     * @return Versiyon string
     */
    @NotNull
    public String getVersion() {
        return version;
    }

    /**
     * API instance'ını temizler. Sadece core plugin onDisable'da çağırmalıdır.
     */
    public static void shutdown() {
        instance = null;
    }
}
