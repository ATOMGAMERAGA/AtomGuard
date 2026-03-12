package com.atomguard.api.trust;

/**
 * Oyuncu güven seviyeleri.
 * Güven puanına göre oyuncuların hangi seviyede olduğunu belirler.
 *
 * @author AtomGuard Team
 * @since 2.0.0
 */
public enum TrustTier {

    /** Yeni oyuncu (0-29 puan) */
    NEW(0, 29),

    /** Normal oyuncu (30-59 puan) */
    REGULAR(30, 59),

    /** Güvenilir oyuncu (60-84 puan) */
    TRUSTED(60, 84),

    /** Veteran oyuncu (85-100 puan) */
    VETERAN(85, 100);

    private final int minScore;
    private final int maxScore;

    TrustTier(int minScore, int maxScore) {
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    /**
     * Bu seviye için minimum puan.
     *
     * @return Minimum puan
     */
    public int getMinScore() {
        return minScore;
    }

    /**
     * Bu seviye için maksimum puan.
     *
     * @return Maksimum puan
     */
    public int getMaxScore() {
        return maxScore;
    }

    /**
     * Puana göre güven seviyesi belirler.
     *
     * @param score Güven puanı
     * @return İlgili güven seviyesi
     */
    public static TrustTier fromScore(int score) {
        if (score >= VETERAN.minScore) return VETERAN;
        if (score >= TRUSTED.minScore) return TRUSTED;
        if (score >= REGULAR.minScore) return REGULAR;
        return NEW;
    }
}
