package com.atomguard.intelligence.detector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class IsolationForestDetectorTest {
    private IsolationForestDetector detector;

    @BeforeEach
    void setUp() {
        detector = new IsolationForestDetector(50, 64, 0.6, 20, 50);
    }

    @Test
    void should_not_flag_during_learning_phase() {
        for (int i = 0; i < 19; i++) {
            assertThat(detector.isAnomaly(100.0)).isFalse();
        }
    }

    @Test
    void should_detect_anomaly_after_learning() {
        // Use a detector with lower threshold for reliable detection
        IsolationForestDetector sensitiveDetector =
                new IsolationForestDetector(100, 64, 0.55, 20, 50);
        // Train with normal values around 100 with some variance
        for (int i = 0; i < 200; i++) {
            sensitiveDetector.isAnomaly(100.0 + (i % 10) - 5.0);
        }
        // Extreme value should be anomaly
        double score = sensitiveDetector.getAnomalyScore(100000.0);
        assertThat(score).isGreaterThan(0.55);
    }

    @Test
    void should_not_flag_normal_values() {
        for (int i = 0; i < 100; i++) {
            detector.isAnomaly(100.0 + (i % 5));
        }
        assertThat(detector.isAnomaly(102.0)).isFalse();
    }

    @Test
    void should_reset_state() {
        for (int i = 0; i < 30; i++) detector.isAnomaly(100.0);
        detector.reset();
        assertThat(detector.getSampleCount()).isEqualTo(0);
    }

    @Test
    void should_compute_anomaly_score() {
        for (int i = 0; i < 100; i++) {
            detector.isAnomaly(100.0 + (i % 3));
        }
        double normalScore = detector.getAnomalyScore(101.0);
        double abnormalScore = detector.getAnomalyScore(10000.0);
        assertThat(abnormalScore).isGreaterThan(normalScore);
    }

    @Test
    void should_compute_average_path_length() {
        assertThat(IsolationForestDetector.averagePathLength(1)).isEqualTo(1.0);
        assertThat(IsolationForestDetector.averagePathLength(256)).isGreaterThan(0);
    }

    @Test
    void should_track_sample_count() {
        assertThat(detector.getSampleCount()).isEqualTo(0);
        detector.isAnomaly(1.0);
        assertThat(detector.getSampleCount()).isEqualTo(1);
    }
}
