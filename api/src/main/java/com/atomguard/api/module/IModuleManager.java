package com.atomguard.api.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Modül yönetim arayüzü.
 * Modüllerin kaydı, etkinleştirilmesi ve sorgulanmasını sağlar.
 *
 * @author AtomGuard Team
 * @since 1.0.0
 */
public interface IModuleManager {

    /**
     * İsme göre modül alır.
     *
     * @param name Modül adı
     * @return Modül instance veya null
     */
    @Nullable
    IModule getModule(@NotNull String name);

    /**
     * Tüm modülleri alır.
     *
     * @return Modül collection
     */
    @NotNull
    Collection<? extends IModule> getAllModules();

    /**
     * Aktif modülleri alır.
     *
     * @return Aktif modül listesi
     */
    @NotNull
    List<? extends IModule> getEnabledModules();

    /**
     * Devre dışı modülleri alır.
     *
     * @return Devre dışı modül listesi
     */
    @NotNull
    List<? extends IModule> getDisabledModules();

    /**
     * Tüm modül isimlerini alır.
     *
     * @return Modül isimleri
     */
    @NotNull
    Set<String> getModuleNames();

    /**
     * Aktif modül sayısını alır.
     *
     * @return Aktif modül sayısı
     */
    int getEnabledModuleCount();

    /**
     * Toplam modül sayısını alır.
     *
     * @return Toplam modül sayısı
     */
    int getTotalModuleCount();

    /**
     * Tüm modüllerin toplam engelleme sayısını alır.
     *
     * @return Toplam engelleme sayısı
     */
    long getTotalBlockedCount();

    /**
     * Modül var mı kontrol eder.
     *
     * @param name Modül adı
     * @return Var ise true
     */
    boolean hasModule(@NotNull String name);

    /**
     * Modülün aktif olup olmadığını kontrol eder.
     *
     * @param name Modül adı
     * @return Aktif ise true
     */
    boolean isModuleEnabled(@NotNull String name);

    /**
     * Modül istatistiklerini Map olarak döner.
     *
     * @return Modül adı -&gt; Engelleme sayısı
     */
    @NotNull
    Map<String, Long> getModuleStatistics();
}
