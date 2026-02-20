package com.atomguard.velocity.pipeline;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.antiddos.DDoSProtectionModule;
import com.atomguard.velocity.module.antibot.VelocityAntiBotModule;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * DDoS kontrol pipeline adımı.
 * <p>
 * Bug düzeltmesi: isVerified parametresi artık AntiBot modülünden doğru olarak okunuyor.
 * Eski hatalı davranış: {@code ddos.checkConnection(ctx.ip(), false)} — her zaman false.
 * Yeni davranış: {@code plugin.getAntiBotModule().isVerified(ip)} ile kontrol.
 */
public class DDoSCheck implements ConnectionCheck {

    private final AtomGuardVelocity plugin;

    public DDoSCheck(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String name() { return "ddos"; }

    @Override
    public int priority() { return 30; }

    @Override
    public boolean isEnabled() {
        return plugin.getDdosModule() != null && plugin.getDdosModule().isEnabled();
    }

    @Override
    public @NotNull CheckResult check(@NotNull ConnectionContext ctx) {
        DDoSProtectionModule ddos = plugin.getDdosModule();
        if (ddos == null) return CheckResult.allowed();

        // ── isVerified bug düzeltmesi ───────────────────────────────────
        // Eski: ddos.checkConnection(ctx.ip(), false)  ← HER ZAMAN false!
        // Yeni: AntiBot modülünden doğru verified durumu al
        boolean isVerified = false;
        VelocityAntiBotModule antiBot = plugin.getAntiBotModule();
        if (antiBot != null) {
            isVerified = antiBot.isVerified(ctx.ip());
        }

        DDoSProtectionModule.ConnectionCheckResult result =
                ddos.checkConnection(ctx.ip(), isVerified);

        if (!result.allowed()) {
            // Geo engelleme için özel audit log
            if ("geo".equals(result.reason()) && plugin.getAuditLogger() != null) {
                plugin.getAuditLogger().log(
                    com.atomguard.velocity.audit.AuditLogger.EventType.COUNTRY_BLOCKED,
                    ctx.ip(), ctx.username(), name(), "GeoBlocker tarafından engellendi",
                    com.atomguard.velocity.audit.AuditLogger.Severity.INFO
                );
            }

            String kickKey = result.kickMessageKey() != null ? result.kickMessageKey() : "kick.ddos";
            return CheckResult.deny(
                plugin.getMessageManager().buildKickMessage(kickKey, Map.of()),
                name(),
                result.reason()
            );
        }

        return CheckResult.allowed();
    }
}
