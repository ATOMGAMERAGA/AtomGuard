package com.atomguard.forensics;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class RecordingSessionTest {
    @Test
    void should_add_recordings() {
        RecordingSession session = new RecordingSession(UUID.randomUUID(), "test", 5);
        session.addRecording(new PacketRecording(System.currentTimeMillis(), "TEST", true, 10, "summary"));
        assertThat(session.getRecordings()).hasSize(1);
    }

    @Test
    void should_evict_oldest_when_full() {
        RecordingSession session = new RecordingSession(UUID.randomUUID(), "test", 3);
        for (int i = 0; i < 5; i++) {
            session.addRecording(new PacketRecording(System.currentTimeMillis(), "PKT-" + i, true, 10, "s"));
        }
        var recordings = session.getRecordings();
        assertThat(recordings).hasSize(3);
        assertThat(recordings.get(0).packetType()).isEqualTo("PKT-2");
    }

    @Test
    void should_return_immutable_copy() {
        RecordingSession session = new RecordingSession(UUID.randomUUID(), "test", 10);
        session.addRecording(new PacketRecording(System.currentTimeMillis(), "TEST", true, 10, "s"));
        var list = session.getRecordings();
        assertThatThrownBy(() -> list.add(new PacketRecording(0, "X", true, 0, "")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_track_duration() throws InterruptedException {
        RecordingSession session = new RecordingSession(UUID.randomUUID(), "test", 10);
        Thread.sleep(50);
        assertThat(session.getDurationMs()).isGreaterThanOrEqualTo(40);
    }
}
