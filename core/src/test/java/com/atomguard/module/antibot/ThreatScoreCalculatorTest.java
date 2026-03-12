package com.atomguard.module.antibot;

import com.atomguard.AtomGuard;
import com.atomguard.data.VerifiedPlayerCache;
import com.atomguard.module.antibot.action.ActionType;
import com.atomguard.module.antibot.verification.WhitelistManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.scheduler.BukkitScheduler;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ThreatScoreCalculatorTest {

    @Mock AntiBotModule module;
    @Mock AtomGuard plugin;
    @Mock WhitelistManager whitelistManager;
    @Mock AttackTracker attackTracker;
    @Mock VerifiedPlayerCache verifiedPlayerCache;

    private ThreatScoreCalculator calculator;

    @BeforeEach
    void setUp() throws Exception {
        // Mock Bukkit.server for GravityCheck (calls Bukkit.getTPS())
        Server mockServer = mock(Server.class);
        lenient().when(mockServer.getTPS()).thenReturn(new double[]{20.0, 20.0, 20.0});
        BukkitScheduler mockScheduler = mock(BukkitScheduler.class);
        lenient().when(mockServer.getScheduler()).thenReturn(mockScheduler);
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, mockServer);

        // Wire mocks
        lenient().when(module.getPlugin()).thenReturn(plugin);
        lenient().when(module.getWhitelistManager()).thenReturn(whitelistManager);
        lenient().when(module.getAttackTracker()).thenReturn(attackTracker);
        lenient().when(plugin.getVerifiedPlayerCache()).thenReturn(verifiedPlayerCache);

        // Default: not whitelisted, not verified, not under attack
        lenient().when(whitelistManager.isWhitelisted(any(UUID.class))).thenReturn(false);
        lenient().when(verifiedPlayerCache.isVerified(anyString(), anyString())).thenReturn(false);
        lenient().when(attackTracker.isUnderAttack()).thenReturn(false);
        lenient().when(attackTracker.getRecentUsernames()).thenReturn(java.util.List.of());

        // Default config values — return defaults for all getConfig calls
        lenient().when(module.getConfigBoolean(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        lenient().when(module.getConfigInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        lenient().when(module.getConfigDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));
        lenient().when(module.getConfigString(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));

        calculator = new ThreatScoreCalculator(module);
    }

    @Nested
    class CleanProfile {

        @Test
        void freshProfile_getsAllowAction() {
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "NormalPlayer", "192.168.1.1");
            // Fresh profile: no ticks, no packets, no suspicious data

            ThreatScoreCalculator.ThreatResult result = calculator.evaluate(profile);

            assertThat(result.getAction()).isEqualTo(ActionType.ALLOW);
            assertThat(result.getScore()).isLessThan(30); // below allow threshold
        }

        @Test
        void resultContainsBreakdown() {
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "TestPlayer", "10.0.0.1");

            ThreatScoreCalculator.ThreatResult result = calculator.evaluate(profile);

            assertThat(result.getBreakdown()).isNotNull();
            assertThat(result.getBreakdown()).isNotEmpty();
        }
    }

    @Nested
    class WhitelistedPlayer {

        @Test
        void whitelistedPlayer_alwaysAllowed() {
            UUID uuid = UUID.randomUUID();
            lenient().when(whitelistManager.isWhitelisted(uuid)).thenReturn(true);

            PlayerProfile profile = new PlayerProfile(uuid, "WhitelistedPlayer", "10.0.0.1");

            ThreatScoreCalculator.ThreatResult result = calculator.evaluate(profile);

            assertThat(result.getAction()).isEqualTo(ActionType.ALLOW);
            assertThat(result.getScore()).isZero();
            assertThat(result.getBreakdown()).isEmpty();
        }
    }

    @Nested
    class VerifiedPlayer {

        @Test
        void verifiedPlayer_notAffectedByAttackModeMultiplier() {
            lenient().when(verifiedPlayerCache.isVerified("VerifiedUser", "10.0.0.1")).thenReturn(true);
            lenient().when(attackTracker.isUnderAttack()).thenReturn(true);

            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "VerifiedUser", "10.0.0.1");
            // Simulate some ticks to trigger checks
            for (int i = 0; i < 200; i++) profile.tick();

            ThreatScoreCalculator.ThreatResult resultVerified = calculator.evaluate(profile);

            // Now test with non-verified under same attack conditions
            lenient().when(verifiedPlayerCache.isVerified("UnverifiedUser", "10.0.0.2")).thenReturn(false);
            PlayerProfile profile2 = new PlayerProfile(UUID.randomUUID(), "UnverifiedUser", "10.0.0.2");
            for (int i = 0; i < 200; i++) profile2.tick();

            ThreatScoreCalculator.ThreatResult resultUnverified = calculator.evaluate(profile2);

            // Under attack mode, unverified should have higher or equal score (due to multiplier)
            assertThat(resultVerified.getScore()).isLessThanOrEqualTo(resultUnverified.getScore());
        }
    }

    @Nested
    class ScoreThresholds {

        @Test
        void scoreUpdatesOnProfile() {
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player", "10.0.0.1");

            calculator.evaluate(profile);

            // currentThreatScore should be updated
            assertThat(profile.getCurrentThreatScore()).isGreaterThanOrEqualTo(0);
        }

        @Test
        void maxThreatScoreTracked() {
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player", "10.0.0.1");

            calculator.evaluate(profile);

            assertThat(profile.getMaxThreatScore()).isGreaterThanOrEqualTo(profile.getCurrentThreatScore());
        }
    }

    @Nested
    class BreakdownContent {

        @Test
        void breakdownContainsCheckNames() {
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player", "10.0.0.1");

            ThreatScoreCalculator.ThreatResult result = calculator.evaluate(profile);

            // Should contain entries for each enabled check
            assertThat(result.getBreakdown()).containsKey("baglanti-hizi");
            assertThat(result.getBreakdown()).containsKey("ping-handshake");
            assertThat(result.getBreakdown()).containsKey("kullanici-adi");
            assertThat(result.getBreakdown()).containsKey("protokol");
        }
    }

    @Nested
    class ThreatResultClass {

        @Test
        void threatResult_holdsData() {
            var result = new ThreatScoreCalculator.ThreatResult(42, ActionType.DELAY, java.util.Map.of("check1", 20, "check2", 22));

            assertThat(result.getScore()).isEqualTo(42);
            assertThat(result.getAction()).isEqualTo(ActionType.DELAY);
            assertThat(result.getBreakdown()).hasSize(2);
        }
    }
}
