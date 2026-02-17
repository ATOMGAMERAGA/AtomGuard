package com.atomguard.velocity.listener;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.communication.MessagingBridge;
import com.atomguard.velocity.communication.SyncProtocol;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;

/**
 * Plugin Messaging kanalı mesaj dinleyicisi (Core → Velocity).
 */
public class PluginMessageListener {

    private final AtomGuardVelocity plugin;

    public PluginMessageListener(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(MessagingBridge.CHANNEL_ID)) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        try {
            SyncProtocol.DecodedMessage msg = SyncProtocol.decode(event.getData());
            handleMessage(msg.type(), msg.payload());
        } catch (Exception e) {
            plugin.getLogManager().warn("Plugin mesajı ayrıştırılamadı: " + e.getMessage());
        }
    }

    private void handleMessage(SyncProtocol.MessageType type, String payload) {
        switch (type) {
            case ATTACK_MODE_SYNC -> plugin.setAttackMode(Boolean.parseBoolean(payload));
            case CONFIG_RELOAD -> plugin.getConfigManager().reload();
            case IP_BLOCK_SYNC -> {
                String[] parts = payload.split(":", 2);
                if (parts.length == 2 && plugin.getFirewallModule() != null)
                    plugin.getFirewallModule().banIP(parts[0], 3_600_000L, parts[1]);
            }
            case IP_UNBLOCK_SYNC -> {
                if (plugin.getFirewallModule() != null) plugin.getFirewallModule().unbanIP(payload);
            }
            default -> plugin.getLogManager().log("Core mesajı alındı: " + type + " - " + payload);
        }
    }
}
