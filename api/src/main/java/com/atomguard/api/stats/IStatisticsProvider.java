package com.atomguard.api.stats;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * İstatistik sağlayıcı arayüzü.
 * Modül ve saldırı istatistiklerini sorgulama imkanı sunar.
 *
 * @author AtomGuard Team
 * @since 1.0.0
 */
public interface IStatisticsProvider {

    /**
     * Tüm zamanların toplam engelleme sayısını alır.
     *
     * @return Toplam engelleme sayısı
     */
    long getTotalBlocked();

    /**
     * Bir modülün bugünkü engelleme sayısını alır.
     *
     * @param moduleName Modül adı
     * @return Bugünkü engelleme sayısı
     */
    long getModuleBlockedToday(@NotNull String moduleName);

    /**
     * Bir modülün toplam engelleme sayısını alır.
     *
     * @param moduleName Modül adı
     * @return Toplam engelleme sayısı
     */
    long getModuleBlockedTotal(@NotNull String moduleName);

    /**
     * Tüm modüllerin toplam engelleme istatistiklerini alır.
     *
     * @return Modül adı -&gt; Toplam engelleme sayısı
     */
    @NotNull
    Map<String, Long> getAllModuleTotals();

    /**
     * Saldırı geçmişi sayısını alır.
     *
     * @return Saldırı geçmişi sayısı
     */
    int getAttackCount();

    /**
     * İstatistik sisteminin aktif olup olmadığını kontrol eder.
     *
     * @return Aktif ise true
     */
    boolean isEnabled();
}
