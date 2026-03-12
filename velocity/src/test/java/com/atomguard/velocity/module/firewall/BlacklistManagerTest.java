package com.atomguard.velocity.module.firewall;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class BlacklistManagerTest {

    @TempDir
    Path tempDir;

    private BlacklistManager manager;

    @BeforeEach
    void setUp() {
        manager = new BlacklistManager(tempDir, mock(Logger.class));
    }

    @Test
    void should_add_and_query_ip() {
        manager.add("192.168.1.1");
        assertThat(manager.isBlacklisted("192.168.1.1")).isTrue();
    }

    @Test
    void should_return_false_for_unknown_ip() {
        assertThat(manager.isBlacklisted("10.0.0.1")).isFalse();
    }

    @Test
    void should_remove_ip() {
        manager.add("192.168.1.1");
        manager.remove("192.168.1.1");
        assertThat(manager.isBlacklisted("192.168.1.1")).isFalse();
    }

    @Test
    void should_not_duplicate_entries() {
        manager.add("192.168.1.1");
        manager.add("192.168.1.1");
        assertThat(manager.size()).isEqualTo(1);
    }

    @Test
    void should_persist_and_reload() {
        manager.add("1.2.3.4");
        manager.add("5.6.7.8");

        BlacklistManager reloaded = new BlacklistManager(tempDir, mock(Logger.class));
        reloaded.load();
        assertThat(reloaded.isBlacklisted("1.2.3.4")).isTrue();
        assertThat(reloaded.isBlacklisted("5.6.7.8")).isTrue();
    }

    @Test
    void should_start_empty() {
        assertThat(manager.size()).isEqualTo(0);
        assertThat(manager.getAll()).isEmpty();
    }

    @Test
    void should_load_empty_file() {
        manager.load();
        assertThat(manager.size()).isEqualTo(0);
    }
}
