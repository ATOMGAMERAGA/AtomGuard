package com.atomguard.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Bir oyuncunun tehdit skoru (heuristic veya antibot) değiştiğinde tetiklenir.
 *
 * @author AtomGuard Team
 * @since 1.0.0
 */
public class ThreatScoreChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final double oldScore;
    private final double newScore;
    private final String reason;

    public ThreatScoreChangedEvent(@NotNull Player player, double oldScore, double newScore, @NotNull String reason) {
        super(true); // async
        this.player = player;
        this.oldScore = oldScore;
        this.newScore = newScore;
        this.reason = reason;
    }

    @NotNull
    public Player getPlayer() {
        return player;
    }

    public double getOldScore() {
        return oldScore;
    }

    public double getNewScore() {
        return newScore;
    }

    @NotNull
    public String getReason() {
        return reason;
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
