package com.atomguard.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Attack mode aktif/pasif olduğunda tetiklenen event.
 *
 * @author AtomGuard Team
 * @since 1.0.0
 */
public class AttackModeToggleEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean activated;
    private final int connectionRate;

    /**
     * @param activated      Attack mode aktif mi edildi
     * @param connectionRate Tetikleme anındaki bağlantı hızı (saniyede bağlantı)
     */
    public AttackModeToggleEvent(boolean activated, int connectionRate) {
        super(true); // async
        this.activated = activated;
        this.connectionRate = connectionRate;
    }

    /**
     * Attack mode aktif mi edildi.
     *
     * @return Aktif edildiyse true, pasif edildiyse false
     */
    public boolean isActivated() {
        return activated;
    }

    /**
     * Tetikleme anındaki bağlantı hızı.
     *
     * @return Saniye başına bağlantı sayısı
     */
    public int getConnectionRate() {
        return connectionRate;
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
