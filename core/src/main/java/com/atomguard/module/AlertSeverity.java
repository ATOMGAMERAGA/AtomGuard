package com.atomguard.module;

/**
 * Yönetici uyarı seviyesi.
 *
 * <p>{@code AbstractModule.alertAdmins()} metodu ile kullanılır.
 * Seviye ne kadar yüksekse, o kadar fazla kanal üzerinden bildirim gönderilir.
 */
public enum AlertSeverity {

    /** Sadece dosyaya log yazar, in-game bildirim yok. */
    LOW,

    /** Log + in-game chat mesajı (ses yok). */
    MEDIUM,

    /** Log + in-game chat mesajı + uyarı sesi. */
    HIGH,

    /** Log + in-game chat mesajı + ses + tam ekran title + Discord webhook. */
    CRITICAL
}
