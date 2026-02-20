package com.atomguard.velocity.adapter;

import com.atomguard.api.stats.IStatisticsProvider;
import com.atomguard.velocity.manager.VelocityStatisticsManager;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class VelocityStatisticsAdapter implements IStatisticsProvider {
    private final VelocityStatisticsManager delegate;

    public VelocityStatisticsAdapter(VelocityStatisticsManager delegate) {
        this.delegate = delegate;
    }

    @Override
    public long getTotalBlocked() {
        return delegate.getLong("total-blocked", 0);
    }

    @Override
    public long getModuleBlockedToday(@NotNull String moduleName) {
        return delegate.getLong("module-" + moduleName + "-today", 0);
    }

    @Override
    public long getModuleBlockedTotal(@NotNull String moduleName) {
        com.atomguard.velocity.module.VelocityModule m = com.atomguard.velocity.AtomGuardVelocity.getInstance().getModuleManager().getModule(moduleName);
        return m != null ? m.getBlockedCount() : 0;
    }

    @Override
    public @NotNull Map<String, Long> getAllModuleTotals() {
        return com.atomguard.velocity.AtomGuardVelocity.getInstance().getModuleManager().getStatistics();
    }

    @Override
    public int getAttackCount() {
        return delegate.getInt("attack-count", 0);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
