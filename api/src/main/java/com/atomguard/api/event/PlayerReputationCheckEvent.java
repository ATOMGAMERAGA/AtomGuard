package com.atomguard.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * IP reputation kontrolü yapıldığında tetiklenen event.
 * Cancel edilirse reputation kontrolü atlanır (oyuncu girişe izin verilir).
 *
 * @author AtomGuard Team
 * @since 1.0.0
 */
public class PlayerReputationCheckEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUUID;
    private final String playerName;
    private final String ipAddress;
    private boolean isVPN;
    private boolean cancelled;

    /**
     * @param playerUUID Oyuncu UUID
     * @param playerName Oyuncu adı
     * @param ipAddress  IP adresi
     * @param isVPN      VPN/Proxy tespit edildi mi
     */
    public PlayerReputationCheckEvent(
            @NotNull UUID playerUUID,
            @NotNull String playerName,
            @NotNull String ipAddress,
            boolean isVPN
    ) {
        super(true); // async
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.ipAddress = ipAddress;
        this.isVPN = isVPN;
        this.cancelled = false;
    }

    @NotNull
    public UUID getPlayerUUID() {
        return playerUUID;
    }

    @NotNull
    public String getPlayerName() {
        return playerName;
    }

    @NotNull
    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * VPN/Proxy olarak tespit edilip edilmediği.
     */
    public boolean isVPN() {
        return isVPN;
    }

    /**
     * VPN sonucunu override eder.
     * Diğer pluginler reputation sonucunu değiştirebilir.
     */
    public void setVPN(boolean vpn) {
        isVPN = vpn;
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
