package com.atomguard.forensics;

/**
 * Saldırı sınıflandırma türleri.
 *
 * @author AtomGuard Team
 * @version 1.2.0
 */
public enum AttackClassification {
    BOT_ATTACK("Bot Saldırısı", "Sınırlı sayıda IP'den yüksek bağlantı"),
    DDOS("DDoS Saldırısı", "Çok sayıda farklı IP'den yüksek trafik"),
    SLOW_LORIS("Yavaş Bağlantı", "Yavaş ve uzun süreli bağlantılar"),
    CREDENTIAL_STUFFING("Kimlik Doldurma", "Login denemesi ağırlıklı saldırı"),
    MIXED("Karma Saldırı", "Birden fazla saldırı türü"),
    UNKNOWN("Bilinmeyen", "Sınıflandırılamadı");

    private final String displayName;
    private final String description;

    AttackClassification(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
