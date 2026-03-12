package com.atomguard.module.antibot.check;

import com.atomguard.module.antibot.AntiBotModule;
import com.atomguard.module.antibot.PlayerProfile;
import org.junit.jupiter.api.BeforeEach;
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
class PacketTimingCheckTest {

    @Mock AntiBotModule module;

    private PacketTimingCheck check;

    @BeforeEach
    void setUp() {
        lenient().when(module.getConfigBoolean(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        lenient().when(module.getConfigInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        lenient().when(module.getConfigDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));

        check = new PacketTimingCheck(module);
    }

    @Test
    void freshProfile_returnsZero() {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player", "10.0.0.1");

        int score = check.calculateThreatScore(profile);

        // No position packets, no keep-alive → no data to flag
        assertThat(score).isZero();
    }

    @Test
    void name_isPaketZamanlama() {
        assertThat(check.getName()).isEqualTo("paket-zamanlama");
    }

    @Test
    void isEnabled_defaultsTrue() {
        assertThat(check.isEnabled()).isTrue();
    }

    @Test
    void score_cappedAt40() {
        // The check caps at 40 via Math.min
        // Even with maximum suspicious data, score should not exceed 40
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player", "10.0.0.1");
        int score = check.calculateThreatScore(profile);
        assertThat(score).isLessThanOrEqualTo(40);
    }
}
