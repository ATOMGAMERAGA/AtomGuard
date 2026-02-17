package com.atomguard.api.module;

import org.jetbrains.annotations.NotNull;

/**
 * Exploit fixer modül arayüzü.
 * Tüm modüller bu arayüzü implement eder.
 *
 * @author AtomGuard Team
 * @since 1.0.0
 */
public interface IModule {

    /**
     * Modül adını alır (config key ile aynı).
     *
     * @return Modül adı
     */
    @NotNull
    String getName();

    /**
     * Modül açıklamasını alır.
     *
     * @return Modül açıklaması
     */
    @NotNull
    String getDescription();

    /**
     * Modülün aktif olup olmadığını kontrol eder.
     *
     * @return Aktif ise true
     */
    boolean isEnabled();

    /**
     * Toplam engelleme sayısını alır.
     *
     * @return Engelleme sayısı
     */
    long getBlockedCount();
}
