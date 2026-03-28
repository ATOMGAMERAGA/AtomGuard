package com.atomguard.velocity.test;

import com.atomguard.velocity.pipeline.CheckResult;
import com.atomguard.velocity.pipeline.ConnectionCheck;
import com.atomguard.velocity.pipeline.ConnectionContext;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Shared test utilities and mock helpers for AtomGuard Velocity tests.
 */
public final class VelocityTestUtils {

    private VelocityTestUtils() {}

    // ── ConnectionContext builders ──────────────────────────────────

    public static ConnectionContext contextForIp(String ip) {
        return new ConnectionContext(ip, "TestPlayer", UUID.randomUUID(), "localhost", 25565, 767, false);
    }

    public static ConnectionContext verifiedContextForIp(String ip) {
        return new ConnectionContext(ip, "TestPlayer", UUID.randomUUID(), "localhost", 25565, 767, true);
    }

    public static ConnectionContext defaultContext() {
        return contextForIp("192.168.1.1");
    }

    // ── ConnectionCheck factories ──────────────────────────────────

    /**
     * Creates an enabled check that always allows the connection.
     */
    public static ConnectionCheck allowCheck(String name, int priority) {
        return new StubCheck(name, priority, true, CheckResult.allowed());
    }

    /**
     * Creates an enabled check that always denies the connection.
     */
    public static ConnectionCheck denyCheck(String name, int priority, String reason) {
        return new StubCheck(name, priority, true,
                CheckResult.deny(Component.text(reason), name, reason));
    }

    /**
     * Creates a disabled check (should be skipped by the pipeline).
     */
    public static ConnectionCheck disabledCheck(String name, int priority) {
        return new StubCheck(name, priority, false, CheckResult.allowed());
    }

    /**
     * Creates an enabled check that throws a RuntimeException when invoked.
     */
    public static ConnectionCheck throwingCheck(String name, int priority) {
        return new ConnectionCheck() {
            @Override
            public @NotNull String name() { return name; }

            @Override
            public int priority() { return priority; }

            @Override
            public boolean isEnabled() { return true; }

            @Override
            public @NotNull CheckResult check(@NotNull ConnectionContext ctx) {
                throw new RuntimeException("Simulated check failure in " + name);
            }
        };
    }

    // ── Stub implementation ────────────────────────────────────────

    private record StubCheck(String stubName, int stubPriority, boolean stubEnabled,
                             CheckResult stubResult) implements ConnectionCheck {
        @Override
        public @NotNull String name() { return stubName; }

        @Override
        public int priority() { return stubPriority; }

        @Override
        public boolean isEnabled() { return stubEnabled; }

        @Override
        public @NotNull CheckResult check(@NotNull ConnectionContext ctx) {
            return stubResult;
        }
    }
}
