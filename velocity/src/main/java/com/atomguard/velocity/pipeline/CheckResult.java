package com.atomguard.velocity.pipeline;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

public record CheckResult(boolean denied, @Nullable Component kickMessage, @Nullable String module, @Nullable String reason) {
    public static CheckResult allowed() {
        return new CheckResult(false, null, null, null);
    }

    public static CheckResult deny(Component msg, String module, String reason) {
        return new CheckResult(true, msg, module, reason);
    }
}
