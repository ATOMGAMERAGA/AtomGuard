package com.atomguard.velocity.pipeline;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.firewall.AccountFirewallModule;
import com.atomguard.velocity.module.firewall.AccountFirewallResult;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class AccountFirewallCheck implements ConnectionCheck {
    private final AtomGuardVelocity plugin;

    public AccountFirewallCheck(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String name() { return "account-firewall"; }

    @Override
    public int priority() { return 40; } // DDoS (30) sonrası, AntiBot (50) öncesi

    @Override
    public boolean isEnabled() {
        return plugin.getAccountFirewallModule() != null && plugin.getAccountFirewallModule().isEnabled();
    }

    @Override
    public @NotNull CheckResult check(@NotNull ConnectionContext ctx) {
        AccountFirewallModule module = plugin.getAccountFirewallModule();
        try {
            // Async kontrolü 2 saniye timeout ile bekle (Pipeline sync çalıştığı için)
            AccountFirewallResult result = module.checkAsync(ctx.username(), ctx.uuid(), true)
                    .get(2, TimeUnit.SECONDS);
            
            if (!result.isAllowed()) {
                return CheckResult.deny(
                    plugin.getMessageManager().parse(result.getReason()),
                    name(),
                    "account-blocked"
                );
            }
        } catch (Exception ignored) {
            // Hata veya timeout durumunda fail-open
        }
        return CheckResult.allowed();
    }
}
