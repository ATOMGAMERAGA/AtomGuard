package com.atomguard.velocity.pipeline;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.antivpn.VPNDetectionModule;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
        try {
            // 2 saniye timeout (async pipeline için 3'ten düşürüldü)
            return checkAsync(ctx)
                .orTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                .exceptionally(e -> CheckResult.allowed()) // Timeout → fail-open
                .join();
        } catch (Exception e) {
            return CheckResult.allowed(); // Her türlü hatada fail-open
        }
    }

    @Override
    public @NotNull CompletableFuture<CheckResult> checkAsync(@NotNull ConnectionContext ctx) {
        VPNDetectionModule vpn = plugin.getVpnModule();

        if (vpn.isVerifiedClean(ctx.ip())) {
            return CompletableFuture.completedFuture(CheckResult.allowed());
        }

        return vpn.check(ctx.ip(), false)
                .orTimeout(3, TimeUnit.SECONDS)
                .exceptionally(e -> new VPNDetectionModule.DetectionResult(false, 0, "timeout", List.of(), "timeout"))
                .thenApply(vpnResult -> {
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
                    return CheckResult.allowed();
                });
    }
}
