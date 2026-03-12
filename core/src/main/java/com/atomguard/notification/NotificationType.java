package com.atomguard.notification;

/** Bildirim kanallarına gönderilebilecek bildirim türleri. @since 2.0.0 */
public enum NotificationType {
    ATTACK_MODE,
    EXPLOIT_BLOCKED,
    BOT_KICKED,
    PANIC_COMMAND,
    PERFORMANCE_ALERT,
    INTELLIGENCE_ALERT,
    HONEYPOT_TRAP,
    DDOS_DETECTED,
    IP_BLOCKED,
    TRUST_DEGRADATION
}
