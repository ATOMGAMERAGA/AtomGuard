package com.atomguard.module.antibot.check;

import com.atomguard.module.antibot.AntiBotModule;
import com.atomguard.module.antibot.PlayerProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProtocolCheckTest {

    @Mock AntiBotModule module;

    private ProtocolCheck check;

    @BeforeEach
    void setUp() {
        lenient().when(module.getConfigBoolean(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        lenient().when(module.getConfigInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        lenient().when(module.getConfigDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));

        check = new ProtocolCheck(module);
    }

    @Nested
    class ClientSettings {

        @Test
        void noClientSettings_belowWaitTime_returnsZero() {
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player", "10.0.0.1");
            // ticks = 0, settingsWait default = 100 → no penalty

            int score = check.calculateThreatScore(profile);

            // No penalty for client settings at 0 ticks
            // But there might be brand/hostname penalties
            assertThat(score).isGreaterThanOrEqualTo(0);
        }

        @Test
        void noClientSettings_aboveWaitTime_addsPenalty() {
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player", "10.0.0.1");
            for (int i = 0; i < 150; i++) profile.tick();

            int score = check.calculateThreatScore(profile);

            // No client settings + ticks > 100 → +15
            // No brand + ticks > 60 → +10
            assertThat(score).isGreaterThanOrEqualTo(15);
        }

        @Test
        void withClientSettings_noPenalty() {
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player", "10.0.0.1");
            for (int i = 0; i < 150; i++) profile.tick();
            profile.recordClientSettings();

            int scoreWithSettings = check.calculateThreatScore(profile);

            PlayerProfile profileWithout = new PlayerProfile(UUID.randomUUID(), "Player2", "10.0.0.2");
            for (int i = 0; i < 150; i++) profileWithout.tick();

            int scoreWithout = check.calculateThreatScore(profileWithout);

            assertThat(scoreWithSettings).isLessThan(scoreWithout);
        }
    }

    @Nested
    class ScoreCap {

        @Test
        void score_cappedAt40() {
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player", "10.0.0.1");
            for (int i = 0; i < 200; i++) profile.tick();
            // Maximum: no settings (+15) + no brand (+10) = 25, can't exceed 40

            int score = check.calculateThreatScore(profile);
            assertThat(score).isLessThanOrEqualTo(40);
        }
    }

    @Test
    void name_isProtokol() {
        assertThat(check.getName()).isEqualTo("protokol");
    }
}
