package com.atomguard.velocity.listener;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.exploit.ChatSpamDetector;
import com.atomguard.velocity.module.exploit.ProxyExploitModule;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;

/**
 * Sohbet spam ve komut flood olayı dinleyicisi.
 */
public class ChatListener {

    private final AtomGuardVelocity plugin;

    public ChatListener(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    @Subscribe(order = PostOrder.EARLY)
    public void onChat(PlayerChatEvent event) {
        ProxyExploitModule exploit = plugin.getExploitModule();
        if (exploit == null) return;

        ChatSpamDetector.SpamCheckResult result = exploit.checkChat(
            event.getPlayer().getUniqueId(), event.getMessage());

        if (result.isSpam()) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            event.getPlayer().sendMessage(
                plugin.getMessageManager().getMessage("mesaj.sohbet-spam"));
        }
    }
}
