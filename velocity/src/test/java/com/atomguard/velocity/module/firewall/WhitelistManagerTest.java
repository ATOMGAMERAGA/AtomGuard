package com.atomguard.velocity.module.firewall;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.config.VelocityConfigManager;
import com.atomguard.velocity.manager.VelocityModuleManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class WhitelistManagerTest {

    @Mock
    AtomGuardVelocity plugin;

    @Mock
    VelocityConfigManager configManager;

    @Mock
    VelocityModuleManager moduleManager;

    @TempDir
    Path tempDir;

    private WhitelistManager manager;

    @BeforeEach
    void setUp() {
        lenient().when(plugin.getDataDirectory()).thenReturn(tempDir);
        lenient().when(plugin.getConfigManager()).thenReturn(configManager);
        lenient().when(plugin.getSlf4jLogger()).thenReturn(mock(Logger.class));
        lenient().when(configManager.getString(anyString(), anyString())).thenReturn("whitelist.json");
        lenient().when(plugin.getModuleManager()).thenReturn(moduleManager);
        lenient().when(moduleManager.getModule(anyString())).thenReturn(null);

        manager = new WhitelistManager(plugin);
    }

    @Test
    void should_add_and_query_ip() {
        manager.add("192.168.1.1");
        assertThat(manager.isWhitelisted("192.168.1.1")).isTrue();
    }

    @Test
    void should_return_false_for_unknown_ip() {
        assertThat(manager.isWhitelisted("10.0.0.1")).isFalse();
    }

    @Test
    void should_remove_ip() {
        manager.add("192.168.1.1");
        manager.remove("192.168.1.1");
        assertThat(manager.isWhitelisted("192.168.1.1")).isFalse();
    }

    @Test
    void should_whitelist_cidr_range() {
        manager.add(new WhitelistEntry(ExceptionType.CIDR, "192.168.1.0/24", "LAN range", 0));

        assertThat(manager.isWhitelisted("192.168.1.50")).isTrue();
        assertThat(manager.isWhitelisted("192.168.1.255")).isTrue();
        assertThat(manager.isWhitelisted("192.168.2.1")).isFalse();
    }

    @Test
    void should_handle_multiple_entries() {
        manager.add("10.0.0.1");
        manager.add("10.0.0.2");
        manager.add(new WhitelistEntry(ExceptionType.CIDR, "172.16.0.0/16", "Internal", 0));

        assertThat(manager.isWhitelisted("10.0.0.1")).isTrue();
        assertThat(manager.isWhitelisted("10.0.0.2")).isTrue();
        assertThat(manager.isWhitelisted("172.16.5.10")).isTrue();
        assertThat(manager.isWhitelisted("8.8.8.8")).isFalse();
    }

    @Test
    void should_cleanup_expired_entries() {
        long pastExpiry = System.currentTimeMillis() - 60_000;
        manager.add(new WhitelistEntry(ExceptionType.IP, "1.2.3.4", "Expired", pastExpiry));

        assertThat(manager.isWhitelisted("1.2.3.4")).isFalse();
        assertThat(manager.getEntries()).isEmpty();
    }

    @Test
    void should_keep_non_expired_entries() {
        long futureExpiry = System.currentTimeMillis() + 600_000;
        manager.add(new WhitelistEntry(ExceptionType.IP, "1.2.3.4", "Valid", futureExpiry));

        assertThat(manager.isWhitelisted("1.2.3.4")).isTrue();
    }

    @Test
    void should_keep_permanent_entries() {
        manager.add(new WhitelistEntry(ExceptionType.IP, "1.2.3.4", "Permanent", 0));
        assertThat(manager.isWhitelisted("1.2.3.4")).isTrue();
    }

    @Test
    void should_persist_and_reload() {
        manager.add("1.2.3.4");
        manager.add(new WhitelistEntry(ExceptionType.CIDR, "10.0.0.0/8", "Internal", 0));
        manager.save();

        WhitelistManager reloaded = new WhitelistManager(plugin);
        reloaded.load();

        assertThat(reloaded.isWhitelisted("1.2.3.4")).isTrue();
        assertThat(reloaded.isWhitelisted("10.5.5.5")).isTrue();
        assertThat(reloaded.getEntries()).hasSize(2);
    }

    @Test
    void should_add_username_entry() {
        manager.add(new WhitelistEntry(ExceptionType.USERNAME, "TestPlayer", "Trusted", 0));

        assertThat(manager.getEntries()).hasSize(1);
        assertThat(manager.getEntries().get(0).getType()).isEqualTo(ExceptionType.USERNAME);
        assertThat(manager.getEntries().get(0).getValue()).isEqualTo("TestPlayer");
    }

    @Test
    void should_return_entries_list() {
        manager.add("10.0.0.1");
        manager.add("10.0.0.2");

        assertThat(manager.getEntries()).hasSize(2);
    }

    @Test
    void should_start_empty() {
        assertThat(manager.getEntries()).isEmpty();
        assertThat(manager.isWhitelisted("1.1.1.1")).isFalse();
    }
}
