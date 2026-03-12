package com.atomguard.api.pipeline;

import org.jetbrains.annotations.NotNull;

/**
 * Bağlantı kontrol adımı arayüzü.
 * Her kontrol adımı gelen bağlantıyı analiz eder ve izin/red kararı verir.
 *
 * @author AtomGuard Team
 * @since 2.0.0
 */
public interface IConnectionCheck {

    /**
     * Kontrol adımının adı.
     *
     * @return Kontrol adı
     */
    @NotNull
    String name();

    /**
     * Kontrol önceliği. Düşük değer = daha önce çalışır.
     *
     * @return Öncelik değeri
     */
    int priority();

    /**
     * Kontrolün aktif olup olmadığını kontrol eder.
     *
     * @return Aktif ise true
     */
    boolean isEnabled();

    /**
     * Bağlantı kontrolü gerçekleştirir.
     *
     * @param context Bağlantı bağlam bilgisi
     * @return Kontrol sonucu (izin veya red)
     */
    @NotNull
    ICheckResult check(@NotNull IConnectionContext context);
}
