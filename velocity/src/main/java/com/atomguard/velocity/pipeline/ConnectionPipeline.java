package com.atomguard.velocity.pipeline;

import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
            
            CheckResult result = check.check(ctx);
            if (result.denied()) {
                return result;
            }
        }
        return CheckResult.allowed();
    }
}
