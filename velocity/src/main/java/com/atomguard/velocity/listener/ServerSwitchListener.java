package com.atomguard.velocity.listener;

import com.atomguard.velocity.AtomGuardVelocity;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;

/**
 * Sunucu değiştirme olayı dinleyicisi.
 */
public class ServerSwitchListener {

    private final AtomGuardVelocity plugin;

    public ServerSwitchListener(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    @Subscribe(order = PostOrder.EARLY)
    public void onServerSwitch(ServerPreConnectEvent event) {
        if (plugin.getExploitModule() == null) return;
        if (!plugin.getExploitModule().allowServerSwitch(event.getPlayer().getUniqueId())) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            event.getPlayer().sendMessage(
                plugin.getMessageManager().getMessage("mesaj.sunucu-degistirme-limit"));
        }
    }
}
