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
}
