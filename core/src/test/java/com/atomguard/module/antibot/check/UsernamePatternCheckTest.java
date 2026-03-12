package com.atomguard.module.antibot.check;

import com.atomguard.module.antibot.AntiBotModule;
import com.atomguard.module.antibot.AttackTracker;
import com.atomguard.module.antibot.PlayerProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsernamePatternCheckTest {

    @Mock AntiBotModule module;
    @Mock AttackTracker attackTracker;

    private UsernamePatternCheck check;

    @BeforeEach
    void setUp() {
        lenient().when(module.getConfigBoolean(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        lenient().when(module.getConfigInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        lenient().when(module.getConfigDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));
        lenient().when(module.getAttackTracker()).thenReturn(attackTracker);
        lenient().when(attackTracker.isUnderAttack()).thenReturn(false);
        lenient().when(attackTracker.getRecentUsernames()).thenReturn(List.of());

        check = new UsernamePatternCheck(module);
    }

    @Nested
    class NormalUsernames {

        @Test
        void normalName_returnsZero() {
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "SteveMC", "10.0.0.1");

            int score = check.calculateThreatScore(profile);

            assertThat(score).isZero();
        }

        @Test
        void nullUsername_returnsZero() {
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), null, "10.0.0.1");

            int score = check.calculateThreatScore(profile);

            assertThat(score).isZero();
        }
    }

    @Nested
    class BotPatterns {

        @Test
        void botPrefix_matchesPattern() {
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Bot_123", "10.0.0.1");

            int score = check.calculateThreatScore(profile);

            assertThat(score).isGreaterThan(0);
        }

        @Test
        void playerPrefix_matchesPattern() {
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player42", "10.0.0.1");

            int score = check.calculateThreatScore(profile);

            assertThat(score).isGreaterThan(0);
        }

        @Test
        void attackPrefix_matchesPattern() {
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Attack99", "10.0.0.1");

            int score = check.calculateThreatScore(profile);

            assertThat(score).isGreaterThan(0);
        }

        @Test
        void shortLetterLongNumber_matchesPattern() {
            // Pattern: ^[a-z]{2,3}\d{6,10}$
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "ab12345678", "10.0.0.1");

            int score = check.calculateThreatScore(profile);

            assertThat(score).isGreaterThan(0);
        }
    }

    @Nested
    class ScoreCap {

        @Test
        void score_cappedAt20() {
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Bot_123", "10.0.0.1");

            int score = check.calculateThreatScore(profile);

            assertThat(score).isLessThanOrEqualTo(20);
        }
    }

    @Test
    void name_isKullaniciAdi() {
        assertThat(check.getName()).isEqualTo("kullanici-adi");
    }
}
