package com.atomguard.velocity.pipeline;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.firewall.FirewallModule;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class FirewallCheck implements ConnectionCheck {
    private final AtomGuardVelocity plugin;

    public FirewallCheck(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String name() { return "firewall"; }

    @Override
    public int priority() { return 10; }

    @Override
    public boolean isEnabled() {
        return plugin.getFirewallModule() != null && plugin.getFirewallModule().isEnabled();
    }

    @Override
    public @NotNull CheckResult check(@NotNull ConnectionContext ctx) {
        FirewallModule firewall = plugin.getFirewallModule();
        FirewallModule.FirewallCheckResult fwResult = firewall.check(ctx.ip());
        
        if (fwResult.verdict() == FirewallModule.FirewallVerdict.DENY) {
            return CheckResult.deny(
                plugin.getMessageManager().buildKickMessage("kick.yasakli",
                    Map.of("ip", ctx.ip(), "sebep", fwResult.reason())),
                name(),
                fwResult.reason()
            );
        }
        return CheckResult.allowed();
    }
}
