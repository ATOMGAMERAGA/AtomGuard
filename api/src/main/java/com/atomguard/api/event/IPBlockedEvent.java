package com.atomguard.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bir IP adresi (VPN, proxy veya manuel olarak) engellendiğinde tetiklenir.
 *
 * @author AtomGuard Team
 * @since 1.0.0
 */
public class IPBlockedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String ipAddress;
    private final String reason;
    private final String playerName;
    private final long expiry;

    public IPBlockedEvent(@NotNull String ipAddress, @NotNull String reason, @Nullable String playerName, long expiry) {
        super(true); // async
        this.ipAddress = ipAddress;
        this.reason = reason;
        this.playerName = playerName != null ? playerName : "Bilinmiyor";
        this.expiry = expiry;
    }

    @NotNull
    public String getIpAddress() {
        return ipAddress;
    }

    @NotNull
    public String getReason() {
        return reason;
    }

    @NotNull
    public String getPlayerName() {
        return playerName;
    }

    public long getExpiry() {
        return expiry;
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
