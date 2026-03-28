package com.atomguard.velocity.pipeline;

import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConnectionPipeline {

    private final CopyOnWriteArrayList<ConnectionCheck> checks = new CopyOnWriteArrayList<>();

    public void addCheck(@NotNull ConnectionCheck check) {
        checks.add(check);
        List<ConnectionCheck> sorted = new ArrayList<>(checks);
        sorted.sort(Comparator.comparingInt(ConnectionCheck::priority));
        checks.clear();
        checks.addAll(sorted);
    }

    @NotNull
    public CheckResult process(@NotNull ConnectionContext ctx) {
        for (ConnectionCheck check : checks) {
            if (!check.isEnabled()) continue;
            // Verified oyuncular için soft/medium check'leri atla
            if (ctx.verified() && check.skipForVerified()) continue;

            CheckResult result = check.check(ctx);
            if (result.denied()) return result;
            // verifiedBypass() → geri kalan check'leri atla
            if (result.pipelineComplete()) return result;
        }
        return CheckResult.allowed();
    }

    @NotNull
    public CompletableFuture<CheckResult> processAsync(@NotNull ConnectionContext ctx) {
        CompletableFuture<CheckResult> chain = CompletableFuture.completedFuture(CheckResult.allowed());
        for (ConnectionCheck check : checks) {
            if (!check.isEnabled()) continue;
            final ConnectionCheck c = check;
            chain = chain.thenCompose(prev -> {
                if (prev.denied()) return CompletableFuture.completedFuture(prev);
                // verifiedBypass() → geri kalan check'leri atla
                if (prev.pipelineComplete()) return CompletableFuture.completedFuture(prev);
                // Verified oyuncular için soft/medium check'leri atla
                if (ctx.verified() && c.skipForVerified()) return CompletableFuture.completedFuture(CheckResult.allowed());
                return c.checkAsync(ctx);
            });
        }
        return chain;
    }
}
