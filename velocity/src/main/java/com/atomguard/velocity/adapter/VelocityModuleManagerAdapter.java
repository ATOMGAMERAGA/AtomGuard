package com.atomguard.velocity.adapter;

import com.atomguard.api.module.IModule;
import com.atomguard.api.module.IModuleManager;
import com.atomguard.velocity.manager.VelocityModuleManager;
import com.atomguard.velocity.module.VelocityModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class VelocityModuleManagerAdapter implements IModuleManager {
    private final VelocityModuleManager delegate;

    public VelocityModuleManagerAdapter(VelocityModuleManager delegate) {
        this.delegate = delegate;
    }

    @Override
    public @Nullable IModule getModule(@NotNull String name) {
        return delegate.getModule(name);
    }

    @Override
    public @NotNull Collection<? extends IModule> getAllModules() {
        return delegate.getAll();
    }

    @Override
    public @NotNull List<? extends IModule> getEnabledModules() {
        return delegate.getAll().stream()
                .filter(VelocityModule::isEnabled)
                .collect(Collectors.toList());
    }

    @Override
    public @NotNull List<? extends IModule> getDisabledModules() {
        return delegate.getAll().stream()
                .filter(m -> !m.isEnabled())
                .collect(Collectors.toList());
    }

    @Override
    public @NotNull Set<String> getModuleNames() {
        return delegate.getAll().stream()
                .map(VelocityModule::getName)
                .collect(Collectors.toSet());
    }

    @Override
    public int getEnabledModuleCount() {
        return delegate.getEnabledCount();
    }

    @Override
    public int getTotalModuleCount() {
        return delegate.getAll().size();
    }

    @Override
    public long getTotalBlockedCount() {
        return delegate.getAll().stream()
                .mapToLong(VelocityModule::getBlockedCount)
                .sum();
    }

    @Override
    public boolean hasModule(@NotNull String name) {
        return delegate.getModule(name) != null;
    }

    @Override
    public boolean isModuleEnabled(@NotNull String name) {
        VelocityModule m = delegate.getModule(name);
        return m != null && m.isEnabled();
    }

    @Override
    public @NotNull Map<String, Long> getModuleStatistics() {
        return delegate.getStatistics();
    }
}
