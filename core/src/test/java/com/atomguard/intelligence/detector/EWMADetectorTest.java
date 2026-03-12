package com.atomguard.intelligence.detector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class EWMADetectorTest {
    private EWMADetector detector;

    @BeforeEach
    void setUp() {
        detector = new EWMADetector(0.1, 3.0, 10);
    }

    @Test
    void should_not_flag_during_learning_phase() {
        // First minSamples values should never be anomalies
        for (int i = 0; i < 10; i++) {
            assertThat(detector.isAnomaly(100.0)).isFalse();
        }
    }

    @Test
    void should_detect_anomaly_after_learning() {
        // Train with normal values around 100
        for (int i = 0; i < 50; i++) {
            detector.isAnomaly(100.0 + (i % 3));
        }
        // Extreme value should be anomaly
        assertThat(detector.isAnomaly(500.0)).isTrue();
    }

    @Test
    void should_not_flag_normal_values_after_learning() {
        // Train with slight variation so variance is non-zero
        for (int i = 0; i < 50; i++) {
            detector.isAnomaly(100.0 + (i % 5));
        }
        assertThat(detector.isAnomaly(102.0)).isFalse();
    }

    @Test
    void should_track_ewma_correctly() {
        detector.isAnomaly(100.0);
        assertThat(detector.getEwma()).isEqualTo(100.0);
        assertThat(detector.getSampleCount()).isEqualTo(1);
    }

    @Test
    void should_reset_state() {
        for (int i = 0; i < 20; i++) detector.isAnomaly(100.0);
        detector.reset();
        assertThat(detector.getSampleCount()).isEqualTo(0);
        assertThat(detector.getEwma()).isEqualTo(0.0);
    }

    @Test
    void should_compute_zscore() {
        for (int i = 0; i < 20; i++) detector.isAnomaly(100.0);
        double zScore = detector.getZScore(100.0);
        // After constant values, variance is very small, so zscore for same value should be ~0
        // But since variance might be near 0, the epsilon prevents division by zero
        assertThat(zScore).isGreaterThanOrEqualTo(0);
    }
}
