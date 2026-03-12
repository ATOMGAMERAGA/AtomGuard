package com.atomguard.forensics;

import com.atomguard.AtomGuard;
import com.atomguard.test.TestUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PacketRecorderTest {
    private PacketRecorder recorder;

    @BeforeEach
    void setUp() {
        AtomGuard plugin = TestUtils.mockPlugin();
        FileConfiguration config = plugin.getConfig();
        when(config.getInt("forensik.paket-kaydi.tampon-suresi-saniye", 30)).thenReturn(30);
        when(config.getInt("forensik.paket-kaydi.max-eszamanli-kayit", 10)).thenReturn(3);
        recorder = new PacketRecorder(plugin);
    }

    @Test
    void should_start_and_stop_recording() {
        UUID id = UUID.randomUUID();
        assertThat(recorder.startRecording(id, "test")).isTrue();
        assertThat(recorder.isRecording(id)).isTrue();
        recorder.stopRecording(id);
        assertThat(recorder.isRecording(id)).isFalse();
    }

    @Test
    void should_record_packets() {
        UUID id = UUID.randomUUID();
        recorder.startRecording(id, "test");
        recorder.recordPacket(id, "PLAYER_POSITION", true, 24, "x=0 y=64 z=0");
        var session = recorder.getSession(id);
        assertThat(session).isPresent();
        assertThat(session.get().getRecordings()).hasSize(1);
    }

    @Test
    void should_respect_max_concurrent_limit() {
        for (int i = 0; i < 3; i++) {
            assertThat(recorder.startRecording(UUID.randomUUID(), "test")).isTrue();
        }
        assertThat(recorder.startRecording(UUID.randomUUID(), "overflow")).isFalse();
    }

    @Test
    void should_ignore_packets_for_non_recording_players() {
        UUID id = UUID.randomUUID();
        recorder.recordPacket(id, "PLAYER_POSITION", true, 24, "test");
        assertThat(recorder.getSession(id)).isEmpty();
    }
}
