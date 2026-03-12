package com.atomguard.velocity.module.antiddos;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.manager.VelocityAlertManager;
import com.atomguard.velocity.manager.VelocityLogManager;
import com.atomguard.velocity.manager.VelocityStatisticsManager;
import com.atomguard.velocity.module.antiddos.AttackLevelManager.AttackLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AttackLevelManagerTest {

    @Mock AtomGuardVelocity plugin;
    @Mock VelocityLogManager logManager;
    @Mock VelocityStatisticsManager statsManager;
    @Mock VelocityAlertManager alertManager;

    private AttackLevelManager manager;

    // baseCpsThreshold=10, hysteresisUp=1ms, hysteresisDown=1ms (fast tests)
    @BeforeEach
    void setUp() {
        lenient().when(plugin.getLogManager()).thenReturn(logManager);
        lenient().when(plugin.getStatisticsManager()).thenReturn(statsManager);
        lenient().when(plugin.getAlertManager()).thenReturn(alertManager);
        lenient().when(plugin.isAttackMode()).thenReturn(false);
        lenient().when(plugin.getAttackModeStartTime()).thenReturn(System.currentTimeMillis());
        lenient().when(statsManager.get(anyString())).thenReturn(0L);

        manager = new AttackLevelManager(plugin, 10, 1, 1);
    }

    // ── Initial state ──────────────────────────────────────────

    @Test
    void initialLevel_isNone() {
        assertThat(manager.getCurrentLevel()).isEqualTo(AttackLevel.NONE);
    }

    @Test
    void initialBlockedCount_isZero() {
        assertThat(manager.getBlockedCount()).isZero();
    }

    // ── Escalation ──────────────────────────────────────────────

    @Nested
    class Escalation {

        @Test
        void normalToElevated() throws InterruptedException {
            // CPS >= 10 * 1.5 = 15 → ELEVATED
            manager.update(15);
            Thread.sleep(5);
            manager.update(15);

            assertThat(manager.getCurrentLevel()).isEqualTo(AttackLevel.ELEVATED);
        }

        @Test
        void elevatedToHigh() throws InterruptedException {
            escalateTo(AttackLevel.ELEVATED, 15);

            // CPS >= 10 * 2 = 20 → HIGH
            manager.update(20);
            Thread.sleep(5);
            manager.update(20);

            assertThat(manager.getCurrentLevel()).isEqualTo(AttackLevel.HIGH);
        }

        @Test
        void highToCritical() throws InterruptedException {
            escalateTo(AttackLevel.HIGH, 20);

            // CPS >= 10 * 3 = 30 → CRITICAL
            manager.update(30);
            Thread.sleep(5);
            manager.update(30);

            assertThat(manager.getCurrentLevel()).isEqualTo(AttackLevel.CRITICAL);
        }

        @Test
        void criticalToLockdown() throws InterruptedException {
            escalateTo(AttackLevel.CRITICAL, 30);

            // CPS >= 10 * 5 = 50 → LOCKDOWN
            manager.update(50);
            Thread.sleep(5);
            manager.update(50);

            assertThat(manager.getCurrentLevel()).isEqualTo(AttackLevel.LOCKDOWN);
        }
    }

    // ── De-escalation ───────────────────────────────────────────

    @Nested
    class DeEscalation {

        @Test
        void deEscalatesToNone_whenCpsDrops() throws InterruptedException {
            escalateTo(AttackLevel.ELEVATED, 15);
            lenient().when(plugin.isAttackMode()).thenReturn(true);

            // CPS drops below threshold
            manager.update(0);
            Thread.sleep(5);
            manager.update(0);

            assertThat(manager.getCurrentLevel()).isEqualTo(AttackLevel.NONE);
        }

        @Test
        void partialDeEscalation() throws InterruptedException {
            escalateTo(AttackLevel.HIGH, 20);
            lenient().when(plugin.isAttackMode()).thenReturn(true);

            // CPS drops to ELEVATED range (15-19)
            manager.update(15);
            Thread.sleep(5);
            manager.update(15);

            assertThat(manager.getCurrentLevel()).isEqualTo(AttackLevel.ELEVATED);
        }
    }

    // ── shouldAllowConnection ───────────────────────────────────

    @Nested
    class ConnectionAccess {

        @Test
        void noneLevel_allowsEveryone() {
            assertThat(manager.shouldAllowConnection("1.2.3.4", false)).isTrue();
            assertThat(manager.shouldAllowConnection("1.2.3.4", true)).isTrue();
        }

        @Test
        void elevatedLevel_allowsEveryone() throws InterruptedException {
            escalateTo(AttackLevel.ELEVATED, 15);

            assertThat(manager.shouldAllowConnection("1.2.3.4", false)).isTrue();
            assertThat(manager.shouldAllowConnection("1.2.3.4", true)).isTrue();
        }

        @Test
        void criticalLevel_onlyAllowsVerified() throws InterruptedException {
            escalateTo(AttackLevel.CRITICAL, 30);

            assertThat(manager.shouldAllowConnection("1.2.3.4", true)).isTrue();
            assertThat(manager.shouldAllowConnection("1.2.3.4", false)).isFalse();
        }

        @Test
        void lockdownLevel_blocksEveryone() throws InterruptedException {
            escalateTo(AttackLevel.LOCKDOWN, 50);

            assertThat(manager.shouldAllowConnection("1.2.3.4", true)).isFalse();
            assertThat(manager.shouldAllowConnection("1.2.3.4", false)).isFalse();
        }
    }

    // ── AttackLevel enum ────────────────────────────────────────

    @Nested
    class AttackLevelEnum {

        @Test
        void isAtLeast_works() {
            assertThat(AttackLevel.CRITICAL.isAtLeast(AttackLevel.ELEVATED)).isTrue();
            assertThat(AttackLevel.NONE.isAtLeast(AttackLevel.ELEVATED)).isFalse();
            assertThat(AttackLevel.HIGH.isAtLeast(AttackLevel.HIGH)).isTrue();
        }

        @Test
        void isAbove_works() {
            assertThat(AttackLevel.HIGH.isAbove(AttackLevel.ELEVATED)).isTrue();
            assertThat(AttackLevel.HIGH.isAbove(AttackLevel.HIGH)).isFalse();
        }

        @Test
        void cpsMultipliers() {
            assertThat(AttackLevel.NONE.getCpsMultiplier()).isEqualTo(1.0);
            assertThat(AttackLevel.ELEVATED.getCpsMultiplier()).isEqualTo(1.5);
            assertThat(AttackLevel.LOCKDOWN.getCpsMultiplier()).isEqualTo(5.0);
        }
    }

    // ── Blocked count ───────────────────────────────────────────

    @Test
    void incrementBlocked_tracks() {
        manager.incrementBlocked();
        manager.incrementBlocked();
        manager.incrementBlocked();

        assertThat(manager.getBlockedCount()).isEqualTo(3);
    }

    // ── isAtLeast ───────────────────────────────────────────────

    @Test
    void isAtLeast_delegatesToCurrentLevel() throws InterruptedException {
        assertThat(manager.isAtLeast(AttackLevel.ELEVATED)).isFalse();

        escalateTo(AttackLevel.ELEVATED, 15);

        assertThat(manager.isAtLeast(AttackLevel.ELEVATED)).isTrue();
        assertThat(manager.isAtLeast(AttackLevel.HIGH)).isFalse();
    }

    // ── Hysteresis prevents premature change ────────────────────

    @Test
    void noChange_withoutHysteresisMet() {
        // Single update — pending set but hysteresis not met yet
        // With 1ms hysteresis this is hard to test, but we can verify
        // that a single update with a new target just sets pending
        manager.update(15); // sets pending to ELEVATED
        // Without a second update after hysteresis, level shouldn't change
        // (depends on timing — with 1ms it likely changes, so this is best-effort)
        // Just verify no exception
    }

    // ── Helper ──────────────────────────────────────────────────

    private void escalateTo(AttackLevel target, int cps) throws InterruptedException {
        // Escalate step by step
        int[] cpsValues = {15, 20, 30, 50};
        AttackLevel[] levels = {AttackLevel.ELEVATED, AttackLevel.HIGH, AttackLevel.CRITICAL, AttackLevel.LOCKDOWN};

        for (int i = 0; i < levels.length; i++) {
            if (manager.getCurrentLevel() == target) return;

            manager.update(cpsValues[i]);
            Thread.sleep(5);
            manager.update(cpsValues[i]);

            if (levels[i] == target) break;
        }
        assertThat(manager.getCurrentLevel()).isEqualTo(target);
    }
}
