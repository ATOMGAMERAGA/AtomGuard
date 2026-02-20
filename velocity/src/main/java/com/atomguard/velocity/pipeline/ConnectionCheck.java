package com.atomguard.velocity.pipeline;

import org.jetbrains.annotations.NotNull;

public interface ConnectionCheck {
    
    @NotNull
    String name();
    
    int priority();
    
    boolean isEnabled();
    
    @NotNull
    CheckResult check(@NotNull ConnectionContext ctx);
}
