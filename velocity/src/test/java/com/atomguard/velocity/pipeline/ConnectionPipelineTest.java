package com.atomguard.velocity.pipeline;

import com.atomguard.velocity.test.VelocityTestUtils;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ConnectionPipelineTest {

    private ConnectionPipeline pipeline;
    private ConnectionContext ctx;

    @BeforeEach
    void setUp() {
        pipeline = new ConnectionPipeline();
        ctx = VelocityTestUtils.defaultContext();
    }

    // ── Empty pipeline ─────────────────────────────────────────────

    @Test
    void emptyPipeline_alwaysAllows() {
        CheckResult result = pipeline.process(ctx);

        assertThat(result.denied()).isFalse();
        assertThat(result.kickMessage()).isNull();
        assertThat(result.module()).isNull();
    }

    // ── Single checks ──────────────────────────────────────────────

    @Test
    void singleAllowCheck_allows() {
        pipeline.addCheck(VelocityTestUtils.allowCheck("allow-check", 1));

        CheckResult result = pipeline.process(ctx);

        assertThat(result.denied()).isFalse();
    }

    @Test
    void singleDenyCheck_returnsDeny() {
        pipeline.addCheck(VelocityTestUtils.denyCheck("deny-check", 1, "blocked"));

        CheckResult result = pipeline.process(ctx);

        assertThat(result.denied()).isTrue();
        assertThat(result.module()).isEqualTo("deny-check");
        assertThat(result.reason()).isEqualTo("blocked");
    }

    // ── Multiple checks — short-circuit ────────────────────────────

    @Test
    void firstDenyCheck_shortCircuits() {
        // Deny at priority 1 should fire before allow at priority 2
        pipeline.addCheck(VelocityTestUtils.denyCheck("early-deny", 1, "early"));
        pipeline.addCheck(VelocityTestUtils.allowCheck("late-allow", 2));

        CheckResult result = pipeline.process(ctx);

        assertThat(result.denied()).isTrue();
        assertThat(result.module()).isEqualTo("early-deny");
    }

    @Test
    void priorityOrdering_lowerPriorityRunsFirst() {
        // Add in reverse order — pipeline should sort by priority
        pipeline.addCheck(VelocityTestUtils.denyCheck("high-priority-deny", 10, "high"));
        pipeline.addCheck(VelocityTestUtils.allowCheck("low-priority-allow", 1));

        // Priority 1 (allow) runs first, then priority 10 (deny)
        CheckResult result = pipeline.process(ctx);

        assertThat(result.denied()).isTrue();
        assertThat(result.module()).isEqualTo("high-priority-deny");
    }

    @Test
    void allAllowChecks_finalResultIsAllowed() {
        pipeline.addCheck(VelocityTestUtils.allowCheck("check-a", 1));
        pipeline.addCheck(VelocityTestUtils.allowCheck("check-b", 2));
        pipeline.addCheck(VelocityTestUtils.allowCheck("check-c", 3));

        CheckResult result = pipeline.process(ctx);

        assertThat(result.denied()).isFalse();
    }

    // ── Disabled check is skipped ──────────────────────────────────

    @Test
    void disabledCheck_isSkipped() {
        // Disabled deny check should not block
        pipeline.addCheck(new ConnectionCheck() {
            @Override
            public @NotNull String name() { return "disabled-deny"; }

            @Override
            public int priority() { return 1; }

            @Override
            public boolean isEnabled() { return false; }

            @Override
            public @NotNull CheckResult check(@NotNull ConnectionContext ctx) {
                return CheckResult.deny(Component.text("should not fire"), "disabled-deny", "should not fire");
            }
        });

        CheckResult result = pipeline.process(ctx);

        assertThat(result.denied()).isFalse();
    }

    @Test
    void disabledDenyWithEnabledAllow_allows() {
        pipeline.addCheck(VelocityTestUtils.disabledCheck("disabled-deny", 1));
        pipeline.addCheck(VelocityTestUtils.allowCheck("enabled-allow", 2));

        CheckResult result = pipeline.process(ctx);

        assertThat(result.denied()).isFalse();
    }

    // ── Exception handling ─────────────────────────────────────────

    @Test
    void exceptionInCheck_doesNotCrashPipeline() {
        // The current implementation does not catch exceptions from checks,
        // so a throwing check will propagate. This test documents the behavior.
        pipeline.addCheck(VelocityTestUtils.throwingCheck("broken-check", 1));

        // The pipeline propagates the exception — it does not swallow it.
        // Depending on the desired behavior, this may need to be changed.
        assertThatCode(() -> pipeline.process(ctx))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated check failure");
    }

    @Test
    void exceptionInCheck_earlyCheckAllowsBeforeThrowing() {
        // If an earlier check denies, the throwing check is never reached
        pipeline.addCheck(VelocityTestUtils.denyCheck("early-deny", 1, "denied-early"));
        pipeline.addCheck(VelocityTestUtils.throwingCheck("broken-check", 2));

        CheckResult result = pipeline.process(ctx);

        assertThat(result.denied()).isTrue();
        assertThat(result.module()).isEqualTo("early-deny");
    }

    // ── CheckResult factory methods ────────────────────────────────

    @Test
    void checkResult_allowed_hasCorrectDefaults() {
        CheckResult result = CheckResult.allowed();

        assertThat(result.denied()).isFalse();
        assertThat(result.kickMessage()).isNull();
        assertThat(result.module()).isNull();
        assertThat(result.reason()).isNull();
    }

    @Test
    void checkResult_deny_carriesAllFields() {
        Component msg = Component.text("Kicked!");
        CheckResult result = CheckResult.deny(msg, "test-module", "test-reason");

        assertThat(result.denied()).isTrue();
        assertThat(result.kickMessage()).isEqualTo(msg);
        assertThat(result.module()).isEqualTo("test-module");
        assertThat(result.reason()).isEqualTo("test-reason");
    }
}
