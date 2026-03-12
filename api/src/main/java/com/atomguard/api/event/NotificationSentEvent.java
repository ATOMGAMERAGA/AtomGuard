package com.atomguard.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Bir bildirim gönderildiğinde tetiklenen event.
 * Discord webhook, Telegram vb. bildirim kanalları için kullanılır.
 *
 * @author AtomGuard Team
 * @since 2.0.0
 */
public class NotificationSentEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String type;
    private final String providerName;
    private final boolean success;

    /**
     * @param type         Bildirim tipi (örn. "exploit-blocked", "attack-mode")
     * @param providerName Bildirim sağlayıcı adı (örn. "discord-webhook", "telegram")
     * @param success      Bildirim başarılı mı gönderildi
     */
    public NotificationSentEvent(
            @NotNull String type,
            @NotNull String providerName,
            boolean success
    ) {
        super(true); // async
        this.type = type;
        this.providerName = providerName;
        this.success = success;
    }

    /**
     * Bildirim tipi.
     *
     * @return Bildirim tipi adı
     */
    @NotNull
    public String getType() {
        return type;
    }

    /**
     * Bildirim sağlayıcı adı.
     *
     * @return Sağlayıcı adı
     */
    @NotNull
    public String getProviderName() {
        return providerName;
    }

    /**
     * Bildirimin başarıyla gönderilip gönderilmediği.
     *
     * @return Başarılı ise true
     */
    public boolean isSuccess() {
        return success;
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
