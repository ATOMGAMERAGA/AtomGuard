package com.atomguard.notification;

import java.util.concurrent.CompletableFuture;

/** Bildirim gönderim sağlayıcısı arayüzü (Discord, Telegram vb.). @since 2.0.0 */
public interface NotificationProvider {
    String getName();
    boolean isEnabled();
    void send(NotificationMessage message);
    CompletableFuture<Void> sendAsync(NotificationMessage message);
    void start();
    void stop();
}
