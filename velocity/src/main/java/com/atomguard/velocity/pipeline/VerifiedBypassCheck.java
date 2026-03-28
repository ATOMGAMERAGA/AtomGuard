package com.atomguard.velocity.pipeline;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.verification.VerificationModule;
import org.jetbrains.annotations.NotNull;

/**
 * Limbo doğrulamasından daha önce geçmiş oyuncuları pipeline'dan bypass eder.
 *
 * <p>Bu check {@link CheckResult#verifiedBypass()} döndürürse
 * {@link ConnectionPipeline} geri kalan tüm check'leri atlar —
 * doğrulanmış oyuncuya tekrar bot/VPN/rate-limit testi uygulanmaz.
 *
 * <p>Priority = 11 → ProtocolCheck (5) ve FirewallCheck (10)'ten SONRA çalışır.
 * Bu sayede verified oyuncular da protocol ve blacklist/ban kontrolünden geçer.
 * RateLimit (20), DDoS (30), AntiBot (50), VPN (60) check'leri atlanır.
 */
public class VerifiedBypassCheck implements ConnectionCheck {

    private final AtomGuardVelocity plugin;

    public VerifiedBypassCheck(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String name() { return "verified-bypass"; }

    @Override
    public int priority() { return 11; }

    @Override
    public boolean isEnabled() {
        VerificationModule vm = plugin.getVerificationModule();
        return vm != null && vm.isEnabled();
    }

    @Override
    public @NotNull CheckResult check(@NotNull ConnectionContext ctx) {
        VerificationModule vm = plugin.getVerificationModule();
        if (vm == null) return CheckResult.allowed();

        if (vm.isVerified(ctx.ip())) {
            // pipelineComplete=true → sonraki check'ler (RateLimit, DDoS, AntiBot, VPN) atlanır.
            // Protocol (5) ve Firewall (10) check'leri priority < 11 olduğu için zaten çalışmıştır.
            return CheckResult.verifiedBypass();
        }

        return CheckResult.allowed();
    }
}
