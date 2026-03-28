package com.atomguard.velocity.pipeline;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.antibot.VelocityAntiBotModule;
import com.atomguard.velocity.module.limbo.LimboVerificationModule;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class AntiBotCheck implements ConnectionCheck {
    private final AtomGuardVelocity plugin;

    public AntiBotCheck(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String name() { return "antibot"; }

    @Override
    public int priority() { return 50; }

    @Override
    public boolean isEnabled() {
        return plugin.getAntiBotModule() != null && plugin.getAntiBotModule().isEnabled();
    }

    @Override
    public boolean skipForVerified() {
        return true; // Verified oyuncular bot analizinden muaf
    }

    @Override
    public @NotNull CheckResult check(@NotNull ConnectionContext ctx) {
        VelocityAntiBotModule antiBot = plugin.getAntiBotModule();

        // Verified bypass: skipForVerified()=true ile pipeline seviyesinde ve
        // VerifiedBypassCheck (priority=11) tarafından zaten yönetiliyor.
        // Burada ikinci kez kontrol etmeye gerek yok — log kirliliği azaltır.

        // Nickname kontrolü — ephemeral ThreatScore yerine direkt deny
        if (antiBot.isNicknameBlocked(ctx.username())) {
            String reason = antiBot.getNicknameBlockReason(ctx.username());
            if (plugin.getAuditLogger() != null) {
                plugin.getAuditLogger().log(
                    com.atomguard.velocity.audit.AuditLogger.EventType.BOT_DETECTED,
                    ctx.ip(), ctx.username(), name(),
                    "Yasaklı kullanıcı adı: " + reason,
                    com.atomguard.velocity.audit.AuditLogger.Severity.WARN
                );
            }
            if (plugin.getFirewallModule() != null) {
                plugin.getFirewallModule().recordViolation(ctx.ip(), 5, "bot-tespiti");
            }
            return CheckResult.hardDeny(
                plugin.getMessageManager().buildKickMessage("kick.bot", Map.of()),
                name(),
                "nickname-blocked"
            );
        }

        antiBot.analyzePreLogin(ctx.ip(), ctx.username(), ctx.hostname(), ctx.port(), ctx.protocol());

        if (antiBot.isHighRisk(ctx.ip())) {
            if (plugin.getAuditLogger() != null) {
                plugin.getAuditLogger().log(
                    com.atomguard.velocity.audit.AuditLogger.EventType.BOT_DETECTED,
                    ctx.ip(), ctx.username(), name(), "Threat Score: " + antiBot.getScore(ctx.ip()).getTotalScore(),
                    com.atomguard.velocity.audit.AuditLogger.Severity.WARN
                );
            }
            if (plugin.getFirewallModule() != null) {
                plugin.getFirewallModule().recordViolation(ctx.ip(), 10, "bot-tespiti");
            }
            return CheckResult.hardDeny(
                plugin.getMessageManager().buildKickMessage("kick.bot", Map.of()),
                name(),
                "high-risk"
            );
        }

        // MEDIUM_RISK → embedded limbo doğrulamasına yönlendir (VerificationModule yoksa)
        LimboVerificationModule limboModule = plugin.getLimboModule();
        if (antiBot.isMediumRisk(ctx.ip())
                && limboModule != null && limboModule.isEnabled()) {
            limboModule.scheduleVerification(ctx.ip(), ctx.username());
            // Bağlantıya devam et — LoginEvent'te limbo'ya yönlendirilecek
        }

        return CheckResult.allowed();
    }
}
