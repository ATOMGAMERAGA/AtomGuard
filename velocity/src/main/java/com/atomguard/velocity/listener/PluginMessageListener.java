package com.atomguard.velocity.listener;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.communication.MessagingBridge;
import com.atomguard.velocity.communication.SyncProtocol;
import com.atomguard.velocity.module.verification.VerificationModule;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

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
        // ── AtomGuard-Limbo doğrulama sonucu ──
        if (event.getIdentifier().equals(VerificationModule.VERIFY_CHANNEL)) {
            event.setResult(PluginMessageEvent.ForwardResult.handled());
            handleVerifyResult(new String(event.getData(), StandardCharsets.UTF_8));
            return;
        }

        // ── Mevcut Core ↔ Velocity mesaj kanalı ──
        if (!event.getIdentifier().equals(MessagingBridge.CHANNEL_ID)) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        try {
            SyncProtocol.DecodedMessage msg = SyncProtocol.decode(event.getData());
            handleMessage(msg.type(), msg.payload());
        } catch (Exception e) {
            plugin.getLogManager().warn("Plugin mesajı ayrıştırılamadı: " + e.getMessage());
        }
    }

    /**
     * AtomGuard-Limbo companion plugin'den gelen doğrulama sonucu.
     * Format: {@code "PASS:<uuid>"} veya {@code "FAIL:<uuid>:<reason>"}
     */
    private void handleVerifyResult(String data) {
        VerificationModule vm = plugin.getVerificationModule();
        if (vm == null || vm.getLimbo() == null) return;

        try {
            if (data.startsWith("PASS:")) {
                UUID uuid = UUID.fromString(data.substring(5));
                vm.getLimbo().onVerificationResult(uuid, true, "ok");
            } else if (data.startsWith("FAIL:")) {
                String[] parts = data.split(":", 3);
                UUID uuid = UUID.fromString(parts[1]);
                String reason = parts.length > 2 ? parts[2] : "unknown";
                vm.getLimbo().onVerificationResult(uuid, false, reason);
            }
        } catch (Exception e) {
            plugin.getLogManager().warn("[Limbo] Doğrulama sonucu ayrıştırılamadı: " + data + " — " + e.getMessage());
        }
    }

    private void handleMessage(SyncProtocol.MessageType type, String payload) {
        switch (type) {
            case PLAYER_DATA_RESPONSE -> plugin.getLogManager().log("Oyuncu verisi yanıtı alındı: " + payload);
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
