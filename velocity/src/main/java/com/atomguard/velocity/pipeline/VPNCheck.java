package com.atomguard.velocity.pipeline;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.antivpn.VPNDetectionModule;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class VPNCheck implements ConnectionCheck {
    private final AtomGuardVelocity plugin;

    public VPNCheck(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String name() { return "vpn"; }

    @Override
    public int priority() { return 60; }

    @Override
    public boolean isEnabled() {
        return plugin.getVpnModule() != null && plugin.getVpnModule().isEnabled();
    }

    @Override
    public @NotNull CheckResult check(@NotNull ConnectionContext ctx) {
        VPNDetectionModule vpn = plugin.getVpnModule();
        
        // Verified clean bypass
        if (vpn.isVerifiedClean(ctx.ip())) {
            return CheckResult.allowed();
        }

        try {
            // Wait for consensus result with timeout
            VPNDetectionModule.DetectionResult vpnResult = vpn.check(ctx.ip(), false)
                    .get(3, TimeUnit.SECONDS);
            
            if (vpnResult.isVPN()) {
                if (plugin.getAuditLogger() != null) {
                    plugin.getAuditLogger().log(
                        com.atomguard.velocity.audit.AuditLogger.EventType.VPN_DETECTED,
                        ctx.ip(), ctx.username(), name(), "Provider: " + vpnResult.getDetectedBy(),
                        com.atomguard.velocity.audit.AuditLogger.Severity.INFO
                    );
                }
                return CheckResult.deny(
                    plugin.getMessageManager().buildKickMessage("kick.vpn", Map.of()),
                    name(),
                    "vpn-detected"
                );
            }
        } catch (Exception ignored) {
            // Fail-open on timeout
        }
        return CheckResult.allowed();
    }
}
