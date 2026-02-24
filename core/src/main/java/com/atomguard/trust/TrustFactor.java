package com.atomguard.trust;

/**
 * Güven puanını etkileyen faktörler.
 *
 * @author AtomGuard Team
 * @version 1.2.0
 */
public enum TrustFactor {
    // Pozitif faktörler
    LOGIN_DAY("Giriş günü", true),
    PLAYTIME("Oynama süresi", true),
    CLEAN_SESSION("Temiz oturum", true),
    ACCOUNT_AGE("Hesap yaşı", true),
    RECENT_CLEAN("Yakın zamanda temiz", true),
    VIOLATION_FREE_TIME("İhlalsiz süre", true),

    // Negatif faktörler
    RECENT_VIOLATION("Son 24 saat ihlali", false),
    TOTAL_VIOLATION("Toplam ihlal", false),
    KICK_COUNT("Kick sayısı", false),
    SUSPICIOUS_PACKETS("Şüpheli paket", false);

    private final String description;
    private final boolean positive;

    TrustFactor(String description, boolean positive) {
        this.description = description;
        this.positive = positive;
    }

    public String getDescription() { return description; }
    public boolean isPositive() { return positive; }
}
