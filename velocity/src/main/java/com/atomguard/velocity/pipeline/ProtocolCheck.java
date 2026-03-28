package com.atomguard.velocity.pipeline;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.protocol.ProtocolValidationModule;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class ProtocolCheck implements ConnectionCheck {
    private final AtomGuardVelocity plugin;

    public ProtocolCheck(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String name() { return "protocol-validation"; }

    @Override
    public int priority() { return 5; } // En başta sürüm kontrolü yapılır

    @Override
    public boolean isEnabled() {
        return plugin.getProtocolModule() != null && plugin.getProtocolModule().isEnabled();
    }

    @Override
    public @NotNull CheckResult check(@NotNull ConnectionContext ctx) {
        ProtocolValidationModule module = plugin.getProtocolModule();
        ProtocolValidationModule.ValidationResult result = module.validateHandshake(ctx.ip(), ctx.hostname(), ctx.port(), ctx.protocol());
        
        if (!result.valid()) {
            // Protokol numarasını kick mesajına ekle — debug ve admin için yararlı
            return CheckResult.softDeny(
                plugin.getMessageManager().buildKickMessage("kick.protocol",
                    Map.of("sebep", result.reason(),
                           "protokol", String.valueOf(ctx.protocol()))),
                name(),
                result.reason()
            );
        }
        return CheckResult.allowed();
    }
}
