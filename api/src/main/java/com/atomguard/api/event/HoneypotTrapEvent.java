package com.atomguard.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Honeypot tuzağına düşen IP için event.
 * Bir bağlantı honeypot portuna ulaştığında async olarak fire edilir.
 */
public class HoneypotTrapEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String ip;
    private final int port;
    private final boolean blacklisted;

    public HoneypotTrapEvent(String ip, int port, boolean blacklisted) {
        super(true); // async
        this.ip = ip;
        this.port = port;
        this.blacklisted = blacklisted;
    }

    /** Bağlanan IP adresi */
    public String getIp() { return ip; }

    /** Bağlantının yapıldığı honeypot port numarası */
    public int getPort() { return port; }

    /** IP'nin kara listeye eklenip eklenmediği */
    public boolean isBlacklisted() { return blacklisted; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
