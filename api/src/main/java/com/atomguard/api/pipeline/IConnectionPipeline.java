package com.atomguard.api.pipeline;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Bağlantı pipeline arayüzü.
 * Bağlantı kontrol adımlarını yönetir.
 *
 * @author AtomGuard Team
 * @since 2.0.0
 */
public interface IConnectionPipeline {

    /**
     * Pipeline'a yeni bir kontrol adımı ekler.
     *
     * @param check Eklenecek kontrol
     */
    void registerCheck(@NotNull IConnectionCheck check);

    /**
     * Pipeline'dan bir kontrol adımını kaldırır.
     *
     * @param checkName Kaldırılacak kontrol adı
     */
    void unregisterCheck(@NotNull String checkName);

    /**
     * Tüm kayıtlı kontrol adımlarının isimlerini alır.
     *
     * @return Kontrol isimleri listesi (öncelik sırasına göre)
     */
    @NotNull
    List<String> getCheckNames();

    /**
     * Belirtilen kontrolün aktif olup olmadığını kontrol eder.
     *
     * @param checkName Kontrol adı
     * @return Aktif ise true
     */
    boolean isCheckEnabled(@NotNull String checkName);
}
