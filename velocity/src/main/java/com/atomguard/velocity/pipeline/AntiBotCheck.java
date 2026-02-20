package com.atomguard.velocity.pipeline;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.antibot.VelocityAntiBotModule;
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
    public @NotNull CheckResult check(@NotNull ConnectionContext ctx) {
        VelocityAntiBotModule antiBot = plugin.getAntiBotModule();
        
        // Verified player bypass
        if (antiBot.isVerified(ctx.ip())) {
            return CheckResult.allowed();
        }

        antiBot.analyzePreLogin(ctx.ip(), ctx.username(), ctx.hostname(), ctx.port(), ctx.protocol());
        
        if (antiBot.isHighRisk(ctx.ip())) {
            // Audit Log
            if (plugin.getAuditLogger() != null) {
                plugin.getAuditLogger().log(
                    com.atomguard.velocity.audit.AuditLogger.EventType.BOT_DETECTED,
                    ctx.ip(), ctx.username(), name(), "Threat Score: " + antiBot.getScore(ctx.ip()).getTotalScore(),
                    com.atomguard.velocity.audit.AuditLogger.Severity.WARN
                );
            }
            
            // Record violation in firewall if possible
            if (plugin.getFirewallModule() != null) {
                plugin.getFirewallModule().recordViolation(ctx.ip(), 10, "bot-tespiti");
            }
            return CheckResult.deny(
                plugin.getMessageManager().buildKickMessage("kick.bot", Map.of()),
                name(),
                "high-risk"
            );
        }
        return CheckResult.allowed();
    }
}
