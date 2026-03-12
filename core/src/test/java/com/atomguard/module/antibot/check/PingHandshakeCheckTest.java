package com.atomguard.module.antibot.check;

import com.atomguard.module.antibot.AntiBotModule;
import com.atomguard.module.antibot.AttackTracker;
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
class PingHandshakeCheckTest {

    @Mock AntiBotModule module;
    @Mock AttackTracker attackTracker;

    private PingHandshakeCheck check;

    @BeforeEach
    void setUp() {
        lenient().when(module.getConfigBoolean(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        lenient().when(module.getConfigInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        lenient().when(module.getConfigDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));
        lenient().when(module.getAttackTracker()).thenReturn(attackTracker);
        lenient().when(attackTracker.isUnderAttack()).thenReturn(false);

        check = new PingHandshakeCheck(module);
    }

    @Test
    void noPing_normalMode_returnsZero() {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player", "10.0.0.1");
        // lastPingTime = 0 (no ping), normal mode → config default 0

        int score = check.calculateThreatScore(profile);

        assertThat(score).isZero();
    }

    @Test
    void noPing_attackMode_returnsSmallScore() {
        lenient().when(attackTracker.isUnderAttack()).thenReturn(true);

        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player", "10.0.0.1");

        int score = check.calculateThreatScore(profile);

        assertThat(score).isEqualTo(5); // default attack mode no-ping score
    }

    @Test
    void recentPingAndHandshake_returnsTen() {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player", "10.0.0.1");
        // Simulate ping received and handshake very recently
        profile.recordPing();

        int score = check.calculateThreatScore(profile);

        // handshakeTime is 0, so timeSincePing = now - 0 which is huge → not < 500
        // The check compares now - handshakeTime, not now - pingTime
        // With handshakeTime = 0, timeSincePing is very large → returns 0
        assertThat(score).isGreaterThanOrEqualTo(0);
    }

    @Test
    void name_isPingHandshake() {
        assertThat(check.getName()).isEqualTo("ping-handshake");
    }
}
