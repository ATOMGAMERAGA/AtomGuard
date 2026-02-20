package com.atomguard.velocity.event;

import com.atomguard.velocity.AtomGuardVelocity;
import com.velocitypowered.api.proxy.ProxyServer;

public class VelocityEventBus {

    private final ProxyServer server;
    private final Object plugin;

    public VelocityEventBus(AtomGuardVelocity plugin) {
        this.server = plugin.getProxyServer();
        this.plugin = plugin;
    }

    public void fireIPBlocked(String ip, String reason, String module) {
        server.getEventManager().fireAndForget(
            new VelocityIPBlockedEvent(ip, reason, module));
    }

    public void firePlayerVerified(String ip, String username) {
        server.getEventManager().fireAndForget(
            new VelocityPlayerVerifiedEvent(ip, username));
    }

    public void fireAttackModeToggle(boolean enabled, int connectionRate) {
        server.getEventManager().fireAndForget(
            new VelocityAttackModeEvent(enabled, connectionRate));
    }

    public void fireThreatScoreChanged(String ip, int oldScore, int newScore, String reason) {
        server.getEventManager().fireAndForget(
            new VelocityThreatScoreEvent(ip, oldScore, newScore, reason));
    }
}
