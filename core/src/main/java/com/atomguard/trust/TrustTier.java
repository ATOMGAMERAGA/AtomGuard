package com.atomguard.trust;

/**
 * Oyuncu güven kademeleri.
 *
 * @author AtomGuard Team
 * @version 1.2.0
 */
public enum TrustTier {
    NEW_PLAYER(0, "Yeni Oyuncu", "<gray>"),
    REGULAR(30, "Düzenli", "<green>"),
    TRUSTED(60, "Güvenilir", "<aqua>"),
    VETERAN(85, "Veteran", "<gold>");

    private final int minScore;
    private final String displayName;
    private final String color;

    TrustTier(int minScore, String displayName, String color) {
        this.minScore = minScore;
        this.displayName = displayName;
        this.color = color;
    }

    public int getMinScore() { return minScore; }
    public String getDisplayName() { return displayName; }
    public String getColor() { return color; }

    /**
     * Verilen skora göre uygun TrustTier döner.
     */
    public static TrustTier fromScore(double score) {
        if (score >= VETERAN.minScore) return VETERAN;
        if (score >= TRUSTED.minScore) return TRUSTED;
        if (score >= REGULAR.minScore) return REGULAR;
        return NEW_PLAYER;
    }
}
