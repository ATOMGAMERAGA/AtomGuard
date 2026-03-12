package com.atomguard.velocity.module.firewall;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TempBanManagerTest {

    @TempDir
    Path tempDir;

    private TempBanManager manager;

    @BeforeEach
    void setUp() {
        manager = new TempBanManager(tempDir, mock(Logger.class));
    }

    @Test
    void should_ban_and_check_ip() {
        manager.ban("192.168.1.1", 60_000, "test ban");
        assertThat(manager.isBanned("192.168.1.1")).isTrue();
    }

    @Test
    void should_return_false_for_unbanned_ip() {
        assertThat(manager.isBanned("10.0.0.1")).isFalse();
    }

    @Test
    void should_expire_ban() {
        // Ban with 1ms duration — effectively already expired
        manager.ban("192.168.1.1", 1, "short ban");
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        assertThat(manager.isBanned("192.168.1.1")).isFalse();
    }

    @Test
    void should_return_remaining_time() {
        manager.ban("192.168.1.1", 60_000, "test");
        long remaining = manager.getRemainingMs("192.168.1.1");
        assertThat(remaining).isGreaterThan(0).isLessThanOrEqualTo(60_000);
    }

    @Test
    void should_return_zero_remaining_for_unknown_ip() {
        assertThat(manager.getRemainingMs("10.0.0.1")).isEqualTo(0);
    }

    @Test
    void should_unban_ip() {
        manager.ban("192.168.1.1", 60_000, "test");
        manager.unban("192.168.1.1");
        assertThat(manager.isBanned("192.168.1.1")).isFalse();
    }

    @Test
    void should_return_ban_reason() {
        manager.ban("192.168.1.1", 60_000, "bot tespit");
        assertThat(manager.getBanReason("192.168.1.1")).isEqualTo("bot tespit");
    }

    @Test
    void should_cleanup_expired_entries() {
        manager.ban("192.168.1.1", 1, "expired");
        manager.ban("192.168.1.2", 60_000, "active");
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        manager.cleanup();
        assertThat(manager.size()).isEqualTo(1);
        assertThat(manager.isBanned("192.168.1.2")).isTrue();
    }

    @Test
    void should_persist_and_reload() {
        manager.ban("1.2.3.4", 600_000, "persistent ban");
        manager.save();

        TempBanManager reloaded = new TempBanManager(tempDir, mock(Logger.class));
        reloaded.load();
        assertThat(reloaded.isBanned("1.2.3.4")).isTrue();
        assertThat(reloaded.getBanReason("1.2.3.4")).isEqualTo("persistent ban");
    }
}
