package com.atomguard.velocity.pipeline;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public interface ConnectionCheck {

    @NotNull
    String name();

    int priority();

    boolean isEnabled();

    @NotNull
    CheckResult check(@NotNull ConnectionContext ctx);

    default @NotNull CompletableFuture<CheckResult> checkAsync(@NotNull ConnectionContext ctx) {
        return CompletableFuture.completedFuture(check(ctx));
    }

    /**
     * Bu check, verified (daha önce başarılı giriş yapmış) oyuncular için atlanmalı mı?
     * Varsayılan: false (tüm check'ler verified için de çalışır).
     * RateLimitCheck, DDoSCheck, AntiBotCheck, TrustScoreCheck bu metodu override ederek true döner.
     */
    default boolean skipForVerified() {
        return false;
    }
}
