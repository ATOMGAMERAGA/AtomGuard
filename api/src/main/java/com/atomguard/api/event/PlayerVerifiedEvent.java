package com.atomguard.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Bir oyuncu başarıyla doğrulandığında (CAPTCHA veya bot kontrolü geçildiğinde) tetiklenir.
 *
 * @author AtomGuard Team
 * @since 1.0.0
 */
public class PlayerVerifiedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String ipAddress;
    private final long timestamp;

    public PlayerVerifiedEvent(@NotNull Player player, @NotNull String ipAddress) {
        super(true); // async
        this.player = player;
        this.ipAddress = ipAddress;
        this.timestamp = System.currentTimeMillis();
    }

    @NotNull
    public Player getPlayer() {
        return player;
    }

    @NotNull
    public String getIpAddress() {
        return ipAddress;
    }

    public long getTimestamp() {
        return timestamp;
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
