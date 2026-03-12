package com.atomguard.api.trust;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Oyuncu güven puanı servisi.
 * Oyuncuların güven seviyelerini yönetir ve sorgular.
 *
 * @author AtomGuard Team
 * @since 2.0.0
 */
public interface ITrustService {

    /**
     * Oyuncunun güven puanını alır.
     *
     * @param playerId Oyuncu UUID
     * @return Güven puanı (0-100)
     */
    int getScore(@NotNull UUID playerId);

    /**
     * Oyuncunun güven seviyesini alır.
     *
     * @param playerId Oyuncu UUID
     * @return Güven seviyesi
     */
    @NotNull
    TrustTier getTier(@NotNull UUID playerId);

    /**
     * Oyuncuya bonus puan ekler.
     *
     * @param playerId Oyuncu UUID
     * @param amount   Eklenecek puan miktarı
     * @param reason   Bonus nedeni
     */
    void addBonus(@NotNull UUID playerId, int amount, @NotNull String reason);

    /**
     * Oyuncuya ceza puanı ekler.
     *
     * @param playerId Oyuncu UUID
     * @param amount   Düşürülecek puan miktarı
     * @param reason   Ceza nedeni
     */
    void addPenalty(@NotNull UUID playerId, int amount, @NotNull String reason);

    /**
     * Oyuncunun güvenilir olup olmadığını kontrol eder.
     *
     * @param playerId Oyuncu UUID
     * @return Güvenilir ise true (TRUSTED veya üzeri)
     */
    boolean isTrusted(@NotNull UUID playerId);

    /**
     * Oyuncunun veteran olup olmadığını kontrol eder.
     *
     * @param playerId Oyuncu UUID
     * @return Veteran ise true
     */
    boolean isVeteran(@NotNull UUID playerId);
}
