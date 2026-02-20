package com.atomguard.velocity.pipeline;

import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ConnectionPipeline {

    private final List<ConnectionCheck> checks = new ArrayList<>();

    public void addCheck(@NotNull ConnectionCheck check) {
        checks.add(check);
        checks.sort(Comparator.comparingInt(ConnectionCheck::priority));
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
