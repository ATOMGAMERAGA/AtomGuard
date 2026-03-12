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
class PostJoinBehaviorCheckTest {

    @Mock AntiBotModule module;
    @Mock AttackTracker attackTracker;

    private PostJoinBehaviorCheck check;

    @BeforeEach
    void setUp() {
        lenient().when(module.getConfigBoolean(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        lenient().when(module.getConfigInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        lenient().when(module.getConfigDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));
        lenient().when(module.getAttackTracker()).thenReturn(attackTracker);
        lenient().when(attackTracker.isUnderAttack()).thenReturn(false);

        check = new PostJoinBehaviorCheck(module);
    }

    @Test
    void belowAnalysisTime_returnsZero() {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player", "10.0.0.1");
        // Default analysisTime = 1200 ticks, 0 ticks elapsed
        int score = check.calculateThreatScore(profile);

        assertThat(score).isZero();
    }

    @Test
    void aboveAnalysisTime_withChat_returnsZero() throws InterruptedException {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player", "10.0.0.1");
        profile.onJoin();
        // Ensure firstChatTime > firstJoinTime so getFirstChatDelayMs() > 0
        Thread.sleep(5);
        profile.recordChat(); // FP-12: chat exemption
        for (int i = 0; i < 1300; i++) profile.tick();

        int score = check.calculateThreatScore(profile);

        assertThat(score).isZero();
    }

    @Test
    void aboveAnalysisTime_noMovement_scoreIncreases() {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player", "10.0.0.1");
        profile.onJoin();
        for (int i = 0; i < 1300; i++) profile.tick();
        // No chat, no movement → uniquePositionCount < 3 → score += 5

        int score = check.calculateThreatScore(profile);

        assertThat(score).isGreaterThan(0);
    }

    @Test
    void score_cappedAt25() {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player", "10.0.0.1");
        profile.onJoin();
        for (int i = 0; i < 1300; i++) profile.tick();

        int score = check.calculateThreatScore(profile);

        assertThat(score).isLessThanOrEqualTo(25);
    }

    @Test
    void name_isGirisSonrasiDavranis() {
        assertThat(check.getName()).isEqualTo("giris-sonrasi-davranis");
    }
}
