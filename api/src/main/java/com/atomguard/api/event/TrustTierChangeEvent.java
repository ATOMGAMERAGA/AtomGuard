package com.atomguard.api.event;

import com.atomguard.api.trust.TrustTier;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Bir oyuncunun güven seviyesi değiştiğinde tetiklenen event.
 *
 * @author AtomGuard Team
 * @since 2.0.0
 */
public class TrustTierChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final TrustTier oldTier;
    private final TrustTier newTier;
    private final int newScore;

    /**
     * @param playerId Oyuncu UUID
     * @param oldTier  Eski güven seviyesi
     * @param newTier  Yeni güven seviyesi
     * @param newScore Yeni güven puanı
     */
    public TrustTierChangeEvent(
            @NotNull UUID playerId,
            @NotNull TrustTier oldTier,
            @NotNull TrustTier newTier,
            int newScore
    ) {
        super(true); // async
        this.playerId = playerId;
        this.oldTier = oldTier;
        this.newTier = newTier;
        this.newScore = newScore;
    }

    /**
     * Güven seviyesi değişen oyuncunun UUID'si.
     *
     * @return Oyuncu UUID
     */
    @NotNull
    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * Oyuncunun eski güven seviyesi.
     *
     * @return Eski güven seviyesi
     */
    @NotNull
    public TrustTier getOldTier() {
        return oldTier;
    }

    /**
     * Oyuncunun yeni güven seviyesi.
     *
     * @return Yeni güven seviyesi
     */
    @NotNull
    public TrustTier getNewTier() {
        return newTier;
    }

    /**
     * Oyuncunun yeni güven puanı.
     *
     * @return Yeni güven puanı (0-100)
     */
    public int getNewScore() {
        return newScore;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
