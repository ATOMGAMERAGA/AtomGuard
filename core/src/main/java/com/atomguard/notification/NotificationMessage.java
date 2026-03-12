package com.atomguard.notification;

import java.time.Instant;
import java.util.Map;

/** Bildirim sağlayıcılarına gönderilen mesaj kaydı. @since 2.0.0 */
public record NotificationMessage(
    NotificationType type,
    String title,
    String description,
    Map<String, String> fields,
    Severity severity,
    Instant timestamp
) {
    public enum Severity { INFO, WARNING, CRITICAL }

    public static NotificationMessage of(NotificationType type, String title, String description, Severity severity) {
        return new NotificationMessage(type, title, description, Map.of(), severity, Instant.now());
    }

    public static NotificationMessage of(NotificationType type, String title, String description,
                                          Map<String, String> fields, Severity severity) {
        return new NotificationMessage(type, title, description, fields, severity, Instant.now());
    }
}
