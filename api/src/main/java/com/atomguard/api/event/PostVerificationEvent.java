package com.atomguard.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Bir oyuncu dogrulama surecinden gectiginde tetiklenen event.
 * Dogrulama yontemleri: "captcha", "gravity", "behavior", "trusted".
 *
 * @author AtomGuard Team
 * @since 2.0.0
 */
public class PostVerificationEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final boolean verified;
    private final String method;

    /**
     * @param playerId Oyuncu UUID
     * @param verified Dogrulama basarili mi
     * @param method   Dogrulama yontemi ("captcha", "gravity", "behavior", "trusted")
     */
    public PostVerificationEvent(
            @NotNull UUID playerId,
            boolean verified,
            @NotNull String method
    ) {
        super(true); // async
        this.playerId = playerId;
        this.verified = verified;
        this.method = method;
    }

    /**
     * Dogrulanan oyuncunun UUID'si.
     *
     * @return Oyuncu UUID
     */
    @NotNull
    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * Dogrulama basarili mi.
     *
     * @return Dogrulama durumu
     */
    public boolean isVerified() {
        return verified;
    }

    /**
     * Dogrulama yontemi.
     *
     * @return Yontem ("captcha", "gravity", "behavior", "trusted")
     */
    @NotNull
    public String getMethod() {
        return method;
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
