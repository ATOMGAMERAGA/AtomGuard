package com.atomguard.api.pipeline;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Bağlantı bağlam bilgisi arayüzü.
 * Gelen bağlantının IP, kullanıcı adı, protokol versiyonu gibi bilgilerini taşır.
 *
 * @author AtomGuard Team
 * @since 2.0.0
 */
public interface IConnectionContext {

    /**
     * Bağlanan oyuncunun IP adresi.
     *
     * @return IP adresi
     */
    @NotNull
    String getIpAddress();

    /**
     * Bağlanan oyuncunun kullanıcı adı.
     *
     * @return Kullanıcı adı
     */
    @NotNull
    String getUsername();

    /**
     * Bağlanan oyuncunun UUID'si.
     * Pre-login aşamasında null olabilir.
     *
     * @return Oyuncu UUID veya null
     */
    @Nullable
    UUID getUniqueId();

    /**
     * Oyuncunun protokol versiyonu.
     *
     * @return Protokol versiyon numarası
     */
    int getProtocolVersion();

    /**
     * Belirtilen flag'in ayarlanıp ayarlanmadığını kontrol eder.
     *
     * @param key Flag anahtarı
     * @return Flag ayarlanmışsa ve true ise true
     */
    boolean hasFlag(@NotNull String key);

    /**
     * Bir flag ayarlar veya kaldırır.
     *
     * @param key   Flag anahtarı
     * @param value Flag değeri
     */
    void setFlag(@NotNull String key, boolean value);

    /**
     * Bağlam özniteliği alır.
     *
     * @param key Öznitelik anahtarı
     * @return Öznitelik değeri veya null
     */
    @Nullable
    Object getAttribute(@NotNull String key);

    /**
     * Bağlam özniteliği ayarlar.
     *
     * @param key   Öznitelik anahtarı
     * @param value Öznitelik değeri (null = kaldır)
     */
    void setAttribute(@NotNull String key, @Nullable Object value);
}
