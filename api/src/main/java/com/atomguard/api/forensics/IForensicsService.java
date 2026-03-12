package com.atomguard.api.forensics;

import org.jetbrains.annotations.NotNull;

/**
 * Forensics kayıt servisi arayüzü.
 * Saldırı anında detaylı paket/olay kaydı başlatır ve durdurur.
 *
 * @author AtomGuard Team
 * @since 2.0.0
 */
public interface IForensicsService {

    /**
     * Kayıt yapılıp yapılmadığını kontrol eder.
     *
     * @return Kayıt aktif ise true
     */
    boolean isRecording();

    /**
     * Forensics kaydını başlatır.
     *
     * @param reason Kayıt başlatma nedeni
     */
    void startRecording(@NotNull String reason);

    /**
     * Forensics kaydını durdurur.
     */
    void stopRecording();
}
