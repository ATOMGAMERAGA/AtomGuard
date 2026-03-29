package com.atomguard.velocity.pipeline;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.verification.VerificationModule;
import org.jetbrains.annotations.NotNull;

/**
 * Doğrulanmış oyuncu bypass — v2 (IP + Username Çifti).
 *
 * <p>Priority = 11 → ProtocolCheck (5) ve FirewallCheck (10)'ten SONRA.
 * Doğrulanmış oyuncular protocol+blacklist kontrolünden geçer ama
 * RateLimit, DDoS, AntiBot, VPN check'leri atlanır.
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

        if (vm.isRequireIPUsernamePair()) {
            // v2: IP + Username çifti kontrolü
            if (ctx.username() != null && vm.isVerifiedPair(ctx.ip(), ctx.username())) {
                return CheckResult.verifiedBypass();
            }
            // IP değişikliği: doğrulanmış user farklı IP → pipeline devam
            // LoginEvent'te Limbo'ya yönlendirilecek
            return CheckResult.allowed();
        } else {
            // Eski mod: sadece IP
            if (vm.isVerified(ctx.ip())) {
                return CheckResult.verifiedBypass();
            }
            return CheckResult.allowed();
        }
    }
}
