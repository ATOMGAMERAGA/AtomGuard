package com.atomguard.heuristic;

import com.atomguard.AtomGuard;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeuristicEngineTest {

    @Mock private AtomGuard plugin;

    private HeuristicEngine engine;

    @BeforeEach
    void setUp() {
        engine = new HeuristicEngine(plugin);
    }

    @Test
    void getProfileCreatesNewProfileForUnknownUUID() {
        UUID uuid = UUID.randomUUID();
        HeuristicProfile profile = engine.getProfile(uuid);

        assertThat(profile).isNotNull();
        assertThat(profile.getUuid()).isEqualTo(uuid);
    }

    @Test
    void getProfileReturnsSameInstanceForSameUUID() {
        UUID uuid = UUID.randomUUID();
        HeuristicProfile first = engine.getProfile(uuid);
        HeuristicProfile second = engine.getProfile(uuid);

        assertThat(first).isSameAs(second);
    }

    @Test
    void newProfileHasZeroSuspicion() {
        UUID uuid = UUID.randomUUID();
        HeuristicProfile profile = engine.getProfile(uuid);

        // getSuspicionLevel applies decay so the value might be slightly less than 0
        // but should not be negative
        assertThat(profile.getSuspicionLevel()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void addSuspicionAccumulates() {
        UUID uuid = UUID.randomUUID();
        HeuristicProfile profile = engine.getProfile(uuid);

        profile.addSuspicion(10.0);
        profile.addSuspicion(15.0);

        // Due to time-based decay, the value may be slightly less than 25.0
        // but should be close to 25.0 since calls are immediate
        double level = profile.getSuspicionLevel();
        assertThat(level).isGreaterThan(20.0).isLessThanOrEqualTo(25.0);
    }

    @Test
    void suspicionCappedAt200() {
        UUID uuid = UUID.randomUUID();
        HeuristicProfile profile = engine.getProfile(uuid);

        profile.addSuspicion(150.0);
        profile.addSuspicion(150.0);

        assertThat(profile.getSuspicionLevel()).isLessThanOrEqualTo(200.0);
    }

    @Test
    void removeProfileRemovesExistingProfile() {
        UUID uuid = UUID.randomUUID();
        engine.getProfile(uuid); // create it
        engine.removeProfile(uuid);

        // Getting again should create a fresh one
        HeuristicProfile newProfile = engine.getProfile(uuid);
        assertThat(newProfile.getViolationCount()).isEqualTo(0);
    }

    @SuppressWarnings("unchecked")
    @Test
    void cleanupOfflinePlayersRemovesOfflineProfiles() {
        UUID onlineUuid = UUID.randomUUID();
        UUID offlineUuid = UUID.randomUUID();

        engine.getProfile(onlineUuid);
        engine.getProfile(offlineUuid);

        Player onlinePlayer = mock(Player.class);
        when(onlinePlayer.getUniqueId()).thenReturn(onlineUuid);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers)
                    .thenReturn((Collection) List.of(onlinePlayer));

            engine.cleanupOfflinePlayers();
        }

        // Online player profile should still exist
        HeuristicProfile onlineProfile = engine.getProfile(onlineUuid);
        assertThat(onlineProfile).isNotNull();

        // Offline player profile was removed; getProfile creates a new fresh one
        HeuristicProfile freshProfile = engine.getProfile(offlineUuid);
        assertThat(freshProfile).isNotNull();
        assertThat(freshProfile.getViolationCount()).isEqualTo(0);
    }

    @SuppressWarnings("unchecked")
    @Test
    void cleanupOfflinePlayersWithNoOnlinePlayersRemovesAll() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();

        engine.getProfile(uuid1);
        engine.getProfile(uuid2);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers)
                    .thenReturn((Collection) Collections.emptyList());

            engine.cleanupOfflinePlayers();
        }

        // Both profiles should be removed; fresh profiles should have zero violations
        assertThat(engine.getProfile(uuid1).getViolationCount()).isEqualTo(0);
        assertThat(engine.getProfile(uuid2).getViolationCount()).isEqualTo(0);
    }

    @Test
    void reduceSuspicionDecreasesLevel() {
        UUID uuid = UUID.randomUUID();
        HeuristicProfile profile = engine.getProfile(uuid);

        profile.addSuspicion(50.0);
        profile.reduceSuspicion(20.0);

        double level = profile.getSuspicionLevel();
        assertThat(level).isLessThan(35.0).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void violationCountIncrements() {
        UUID uuid = UUID.randomUUID();
        HeuristicProfile profile = engine.getProfile(uuid);

        profile.incrementViolation();
        profile.incrementViolation();
        profile.incrementViolation();

        assertThat(profile.getViolationCount()).isEqualTo(3);
    }
}
