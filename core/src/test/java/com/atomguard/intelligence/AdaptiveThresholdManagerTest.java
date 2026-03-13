package com.atomguard.intelligence;

import com.atomguard.AtomGuard;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdaptiveThresholdManagerTest {

    @Mock private AtomGuard plugin;
    @Mock private FileConfiguration config;

    private AdaptiveThresholdManager manager;

    @BeforeEach
    void setUp() {
        when(plugin.getConfig()).thenReturn(config);
        when(config.getBoolean("threat-intelligence.adaptive-threshold.day-night-separation", true)).thenReturn(true);
        when(config.getBoolean("threat-intelligence.adaptive-threshold.weekday-weekend-separation", true)).thenReturn(true);
        when(config.getInt("threat-intelligence.adaptive-threshold.min-learning-weeks", 2)).thenReturn(2);
        when(config.getDouble("threat-intelligence.ema-alpha", 0.1)).thenReturn(0.1);

        manager = new AdaptiveThresholdManager(plugin);
    }

    @Test
    void learningNotCompleteInitially() {
        assertThat(manager.isLearningComplete()).isFalse();
    }

    @Test
    void anomalyDetectionReturnsFalseDuringLearning() {
        // During learning period, isAnomaly should always return false
        assertThat(manager.isAnomaly(1000.0)).isFalse();
        assertThat(manager.isAnomaly(5000.0)).isFalse();
    }

    @Test
    void recordSampleDoesNotTriggerAnomaly() {
        // recordSample only records, does not detect anomalies
        manager.recordSample(10.0);
        manager.recordSample(20.0);
        // After recording, learning should still not be complete (needs samples in all 168 slots)
        assertThat(manager.isLearningComplete()).isFalse();
    }

    @Test
    void separateDayNightConfigRead() {
        assertThat(manager.isSeparateDayNight()).isTrue();
    }

    @Test
    void separateWeekdayWeekendConfigRead() {
        assertThat(manager.isSeparateWeekdayWeekend()).isTrue();
    }

    @Test
    void isAnomalyReturnsFalseWithInsufficientData() {
        // Feed a few data points — insufficient for learning completion across all 168 slots
        for (int i = 0; i < 50; i++) {
            manager.recordSample(10.0);
        }
        // Learning still not complete because only 1 slot (current hour) gets samples
        assertThat(manager.isLearningComplete()).isFalse();
        assertThat(manager.isAnomaly(10000.0)).isFalse();
    }

    @Test
    void getDetectorForCurrentSlotIsNotNull() {
        assertThat(manager.getDetectorForCurrentSlot()).isNotNull();
    }
}
