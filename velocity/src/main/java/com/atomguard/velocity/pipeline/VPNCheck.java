package com.atomguard.velocity.pipeline;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.antivpn.VPNDetectionModule;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * VPN/Proxy pipeline check'i — v2.
 *
 * <h2>Değişiklikler (v1 → v2)</h2>
 * <ul>
 *   <li>Timeout 2s → 6s (çok katmanlı kontrol daha uzun sürebilir)</li>
 *   <li>Timeout durumunda configüre edilebilir davranış: fail-open veya fail-closed</li>
 *   <li>VPN tespit edildiğinde HARD severity (trust score ciddi düşer)</li>
 *   <li>Confidence score kick mesajına eklenir (debug için)</li>
 * </ul>
 */
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
            return checkAsync(ctx)
                    .orTimeout(6, TimeUnit.SECONDS)
                    .exceptionally(e -> {
                        // Timeout veya hata durumu
                        boolean failClosed = plugin.getConfigManager().getBoolean(
                                "vpn-proxy-block.fail-closed-on-timeout", false);
                        if (failClosed && plugin.isAttackMode()) {
                            // Saldırı modunda timeout → engelle (güvenli taraf)
                            return CheckResult.deny(
                                    plugin.getMessageManager().buildKickMessage("kick.vpn-timeout", Map.of()),
                                    name(), "vpn-check-timeout-attack-mode"
                            );
                        }
                        return CheckResult.allowed(); // Fail-open (normal mod)
                    })
                    .join();
        } catch (Exception e) {
            return CheckResult.allowed(); // Hata → fail-open
        }
    }

    @Override
    public @NotNull CompletableFuture<CheckResult> checkAsync(@NotNull ConnectionContext ctx) {
        VPNDetectionModule vpn = plugin.getVpnModule();

        // Doğrulanmış temiz IP → bypass
        if (vpn.isVerifiedClean(ctx.ip())) {
            return CompletableFuture.completedFuture(CheckResult.allowed());
        }

        return vpn.check(ctx.ip(), false)
                .orTimeout(6, TimeUnit.SECONDS)
                .exceptionally(e -> new VPNDetectionModule.DetectionResult(
                        false, 0, "timeout", List.of(), "timeout"))
                .thenApply(vpnResult -> {
                    if (vpnResult.isVPN()) {
                        // Audit log
                        if (plugin.getAuditLogger() != null) {
                            plugin.getAuditLogger().log(
                                    com.atomguard.velocity.audit.AuditLogger.EventType.VPN_DETECTED,
                                    ctx.ip(), ctx.username(), name(),
                                    "Yöntem: " + vpnResult.getMethod() +
                                            " | Güven: %" + vpnResult.getConfidenceScore() +
                                            " | Sağlayıcılar: " + String.join(", ", vpnResult.getDetectedBy()),
                                    com.atomguard.velocity.audit.AuditLogger.Severity.WARN
                            );
                        }

                        // VPN tespiti → HARD deny (ciddi ihlal)
                        return CheckResult.hardDeny(
                                plugin.getMessageManager().buildKickMessage("kick.vpn", Map.of()),
                                name(),
                                "vpn-detected:" + vpnResult.getMethod()
                        );
                    }
                    return CheckResult.allowed();
                });
    }
}
