package com.atomguard.trust;

import com.atomguard.AtomGuard;
import com.atomguard.manager.ConfigManager;
import com.atomguard.manager.LogManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrustScoreManagerTest {

    @Mock private AtomGuard plugin;
    @Mock private FileConfiguration config;
    @Mock private ConfigManager configManager;
    @Mock private LogManager logManager;
    @Mock private java.util.logging.Logger logger;

    @TempDir
    File tempDir;

    private TrustScoreManager manager;

    @BeforeEach
    void setUp() {
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(tempDir);
        when(plugin.getConfigManager()).thenReturn(configManager);
        when(plugin.getLogManager()).thenReturn(logManager);
        when(plugin.getLogger()).thenReturn(logger);

        // Config defaults
        when(config.getBoolean("guven-skoru.aktif", true)).thenReturn(true);
        when(config.getInt("guven-skoru.baz-puan", 20)).thenReturn(20);
        when(config.getInt("guven-skoru.esikler.regular", 30)).thenReturn(30);
        when(config.getInt("guven-skoru.esikler.trusted", 60)).thenReturn(60);
        when(config.getInt("guven-skoru.esikler.veteran", 85)).thenReturn(85);
        when(config.getInt("guven-skoru.bypass.saldiri-modu-esik", 70)).thenReturn(70);
        when(config.getBoolean("guven-skoru.bypass.bot-kontrol-atla", true)).thenReturn(true);
        when(config.getBoolean("guven-skoru.bypass.vpn-kontrol-atla", false)).thenReturn(false);
        when(config.getInt("guven-skoru.kaydetme-araligi-dk", 10)).thenReturn(10);
        when(config.getInt("guven-skoru.guncelleme-araligi-dk", 5)).thenReturn(5);
        when(config.getLong("guven-skoru.ihlal-sifirlama-saat", 24)).thenReturn(24L);

        when(configManager.isDebugEnabled()).thenReturn(false);

        manager = new TrustScoreManager(plugin);
    }

    @Test
    void initialScoreEqualsBasePuan() {
        UUID uuid = UUID.randomUUID();
        // No profile exists yet, getScoreDouble returns basePuan
        double score = manager.getScoreDouble(uuid);
        assertThat(score).isEqualTo(20.0);
    }

    @Test
    void getOrCreateReturnsProfileWithDefaultScore() {
        UUID uuid = UUID.randomUUID();
        TrustProfile profile = manager.getOrCreate(uuid);
        assertThat(profile).isNotNull();
        assertThat(profile.getUuid()).isEqualTo(uuid);
        // Default TrustProfile score is 20.0
        assertThat(profile.getTrustScore()).isEqualTo(20.0);
    }

    @Test
    void tierTransitionNewToRegular() {
        UUID uuid = UUID.randomUUID();
        manager.setScore(uuid, "TestPlayer", 35.0);
        com.atomguard.api.trust.TrustTier tier = manager.getTier(uuid);
        assertThat(tier).isEqualTo(com.atomguard.api.trust.TrustTier.REGULAR);
    }

    @Test
    void tierTransitionRegularToTrusted() {
        UUID uuid = UUID.randomUUID();
        manager.setScore(uuid, "TestPlayer", 65.0);
        com.atomguard.api.trust.TrustTier tier = manager.getTier(uuid);
        assertThat(tier).isEqualTo(com.atomguard.api.trust.TrustTier.TRUSTED);
    }

    @Test
    void tierTransitionTrustedToVeteran() {
        UUID uuid = UUID.randomUUID();
        manager.setScore(uuid, "TestPlayer", 90.0);
        com.atomguard.api.trust.TrustTier tier = manager.getTier(uuid);
        assertThat(tier).isEqualTo(com.atomguard.api.trust.TrustTier.VETERAN);
    }

    @Test
    void tierIsNewWhenScoreBelow30() {
        UUID uuid = UUID.randomUUID();
        manager.setScore(uuid, "TestPlayer", 15.0);
        com.atomguard.api.trust.TrustTier tier = manager.getTier(uuid);
        assertThat(tier).isEqualTo(com.atomguard.api.trust.TrustTier.NEW);
    }

    @Test
    void addBonusIncreasesScore() {
        UUID uuid = UUID.randomUUID();
        manager.getOrCreate(uuid); // creates profile at 20.0
        manager.addBonus(uuid, 15, "test bonus");
        assertThat(manager.getScoreDouble(uuid)).isEqualTo(35.0);
    }

    @Test
    void addBonusCappedAt100() {
        UUID uuid = UUID.randomUUID();
        manager.setScore(uuid, "TestPlayer", 95.0);
        manager.addBonus(uuid, 20, "large bonus");
        assertThat(manager.getScoreDouble(uuid)).isEqualTo(100.0);
    }

    @Test
    void addPenaltyDecreasesScore() {
        UUID uuid = UUID.randomUUID();
        manager.setScore(uuid, "TestPlayer", 50.0);
        manager.addPenalty(uuid, 10, "test penalty");
        assertThat(manager.getScoreDouble(uuid)).isEqualTo(40.0);
    }

    @Test
    void addPenaltyMinAt0() {
        UUID uuid = UUID.randomUUID();
        manager.setScore(uuid, "TestPlayer", 5.0);
        manager.addPenalty(uuid, 20, "large penalty");
        assertThat(manager.getScoreDouble(uuid)).isEqualTo(0.0);
    }

    @Test
    void isTrustedReturnsTrueWhenScoreAboveThreshold() {
        UUID uuid = UUID.randomUUID();
        manager.setScore(uuid, "TestPlayer", 60.0);
        assertThat(manager.isTrusted(uuid)).isTrue();
    }

    @Test
    void isTrustedReturnsFalseWhenScoreBelowThreshold() {
        UUID uuid = UUID.randomUUID();
        manager.setScore(uuid, "TestPlayer", 59.0);
        assertThat(manager.isTrusted(uuid)).isFalse();
    }

    @Test
    void isVeteranReturnsTrueWhenScoreAboveThreshold() {
        UUID uuid = UUID.randomUUID();
        manager.setScore(uuid, "TestPlayer", 85.0);
        assertThat(manager.isVeteran(uuid)).isTrue();
    }

    @Test
    void isVeteranReturnsFalseWhenScoreBelowThreshold() {
        UUID uuid = UUID.randomUUID();
        manager.setScore(uuid, "TestPlayer", 84.0);
        assertThat(manager.isVeteran(uuid)).isFalse();
    }

    @Test
    void recordViolationIncrementsCounterAndReducesScore() {
        UUID uuid = UUID.randomUUID();
        manager.setScore(uuid, "TestPlayer", 50.0);
        TrustProfile profileBefore = manager.getOrCreate(uuid);
        int violationsBefore = profileBefore.getTotalViolations();

        manager.recordViolation(uuid, "test-module");

        TrustProfile profileAfter = manager.getOrCreate(uuid);
        assertThat(profileAfter.getTotalViolations()).isEqualTo(violationsBefore + 1);
        assertThat(profileAfter.getRecentViolations()).isGreaterThan(0);
        // Score should have decreased due to violation penalty in recalculate
        assertThat(profileAfter.getTrustScore()).isLessThan(50.0);
    }

    @Test
    void recordViolationSetsViolationTimestamp() {
        UUID uuid = UUID.randomUUID();
        manager.getOrCreate(uuid);
        long before = System.currentTimeMillis();
        manager.recordViolation(uuid, "test-module");
        long after = System.currentTimeMillis();

        TrustProfile profile = manager.getOrCreate(uuid);
        assertThat(profile.getLastViolationTimestamp())
                .isGreaterThanOrEqualTo(before)
                .isLessThanOrEqualTo(after);
    }

    @Test
    void canBypassAttackModeWhenScoreHighEnough() {
        UUID uuid = UUID.randomUUID();
        manager.setScore(uuid, "TestPlayer", 75.0);
        assertThat(manager.canBypassAttackMode(uuid)).isTrue();
    }

    @Test
    void cannotBypassAttackModeWhenScoreTooLow() {
        UUID uuid = UUID.randomUUID();
        manager.setScore(uuid, "TestPlayer", 50.0);
        assertThat(manager.canBypassAttackMode(uuid)).isFalse();
    }

    @Test
    void resetProfileResetsToBasePuan() {
        UUID uuid = UUID.randomUUID();
        manager.setScore(uuid, "TestPlayer", 90.0);
        manager.resetProfile(uuid, "TestPlayer");
        assertThat(manager.getScoreDouble(uuid)).isEqualTo(20.0);
    }

    @Test
    void isEnabledReturnsTrue() {
        assertThat(manager.isEnabled()).isTrue();
    }

    @Test
    void getBasePuanReturnsConfiguredValue() {
        assertThat(manager.getBasePuan()).isEqualTo(20);
    }
}
