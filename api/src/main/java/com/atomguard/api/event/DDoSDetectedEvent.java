package com.atomguard.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Bir DDoS saldırısı veya aşırı bağlantı yükü tespit edildiğinde tetiklenir.
 *
 * @author AtomGuard Team
 * @since 1.0.0
 */
public class DDoSDetectedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int connectionRate;
    private final int threshold;
    private final boolean attackModeActive;

    public DDoSDetectedEvent(int connectionRate, int threshold, boolean attackModeActive) {
        super(true); // async
        this.connectionRate = connectionRate;
        this.threshold = threshold;
        this.attackModeActive = attackModeActive;
    }

    public int getConnectionRate() {
        return connectionRate;
    }

    public int getThreshold() {
        return threshold;
    }

    public boolean isAttackModeActive() {
        return attackModeActive;
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
