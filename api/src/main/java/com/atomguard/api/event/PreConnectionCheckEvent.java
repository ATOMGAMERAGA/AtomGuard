package com.atomguard.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Bağlantı kontrolleri başlamadan önce tetiklenen event.
 * İptal edilirse oyuncunun bağlantısına izin verilir (kontroller atlanır).
 *
 * @author AtomGuard Team
 * @since 2.0.0
 */
public class PreConnectionCheckEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String ipAddress;
    private final String username;
    private boolean cancelled;

    /**
     * @param ipAddress Bağlanan oyuncunun IP adresi
     * @param username  Bağlanan oyuncunun kullanıcı adı
     */
    public PreConnectionCheckEvent(@NotNull String ipAddress, @NotNull String username) {
        super(true); // async
        this.ipAddress = ipAddress;
        this.username = username;
        this.cancelled = false;
    }

    /**
     * Bağlanan oyuncunun IP adresi.
     *
     * @return IP adresi
     */
    @NotNull
    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * Bağlanan oyuncunun kullanıcı adı.
     *
     * @return Kullanıcı adı
     */
    @NotNull
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
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
