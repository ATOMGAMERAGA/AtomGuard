package com.atomguard.api.event;

import com.atomguard.api.module.IModule;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Bir modül aktif/pasif edildiğinde tetiklenen event.
 *
 * @author AtomGuard Team
 * @since 1.0.0
 */
public class ModuleToggleEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final IModule module;
    private final boolean newState;
    private boolean cancelled;

    /**
     * @param module   Durumu değişen modül
     * @param newState Yeni durum (true = aktif, false = pasif)
     */
    public ModuleToggleEvent(@NotNull IModule module, boolean newState) {
        this.module = module;
        this.newState = newState;
        this.cancelled = false;
    }

    /**
     * Durumu değişen modül.
     */
    @NotNull
    public IModule getModule() {
        return module;
    }

    /**
     * Modülün yeni durumu.
     *
     * @return true = aktif edilecek, false = pasif edilecek
     */
    public boolean getNewState() {
        return newState;
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
