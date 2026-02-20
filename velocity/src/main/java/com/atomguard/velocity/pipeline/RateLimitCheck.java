package com.atomguard.velocity.pipeline;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.ratelimit.GlobalRateLimitModule;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class RateLimitCheck implements ConnectionCheck {
    private final AtomGuardVelocity plugin;

    public RateLimitCheck(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String name() { return "ratelimit"; }

    @Override
    public int priority() { return 20; }

    @Override
    public boolean isEnabled() {
        return plugin.getRateLimitModule() != null && plugin.getRateLimitModule().isEnabled();
    }

    @Override
    public @NotNull CheckResult check(@NotNull ConnectionContext ctx) {
        GlobalRateLimitModule rateLimiter = plugin.getRateLimitModule();
        com.atomguard.velocity.module.ratelimit.ConnectionRateLimiter.RateLimitResult result = rateLimiter.allowConnectionWithInfo(ctx.ip());
        
        if (!result.allowed()) {
            int seconds = (int) Math.ceil(result.retryAfterMs() / 1000.0);
            return CheckResult.deny(
                plugin.getMessageManager().buildKickMessage("kick.rate-limit", 
                    Map.of("sure", String.valueOf(seconds))),
                name(),
                "rate-limit-exceeded"
            );
        }
        return CheckResult.allowed();
    }
}
