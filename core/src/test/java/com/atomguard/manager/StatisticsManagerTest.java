package com.atomguard.manager;

import com.atomguard.AtomGuard;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StatisticsManagerTest {

    @Mock private AtomGuard plugin;
    @Mock private FileConfiguration config;
    @Mock private java.util.logging.Logger logger;

    @TempDir
    File tempDir;

    private StatisticsManager manager;

    @BeforeEach
    void setUp() {
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(tempDir);
        when(plugin.getLogger()).thenReturn(logger);

        when(config.getBoolean("istatistik.aktif", true)).thenReturn(true);
        when(config.getInt("istatistik.otomatik-kaydetme-dakika", 5)).thenReturn(5);
        when(config.getInt("istatistik.max-saldiri-gecmisi", 100)).thenReturn(100);

        manager = new StatisticsManager(plugin);
    }

    @Test
    void initialTotalBlockedIsZero() {
        assertThat(manager.getTotalBlockedAllTime()).isEqualTo(0);
    }

    @Test
    void recordBlockIncrementsTotalCounter() {
        manager.recordBlock("test-module");
        assertThat(manager.getTotalBlockedAllTime()).isEqualTo(1);
    }

    @Test
    void recordBlockMultipleTimesIncrements() {
        manager.recordBlock("module-a");
        manager.recordBlock("module-a");
        manager.recordBlock("module-b");

        assertThat(manager.getTotalBlockedAllTime()).isEqualTo(3);
    }

    @Test
    void getTotalBlockedReturnsSameAsTotalBlockedAllTime() {
        manager.recordBlock("module-a");
        manager.recordBlock("module-b");

        assertThat(manager.getTotalBlocked()).isEqualTo(manager.getTotalBlockedAllTime());
    }

    @Test
    void getModuleBlockedTotalReturnsCorrectCount() {
        manager.recordBlock("module-a");
        manager.recordBlock("module-a");
        manager.recordBlock("module-b");

        assertThat(manager.getModuleBlockedTotal("module-a")).isEqualTo(2);
        assertThat(manager.getModuleBlockedTotal("module-b")).isEqualTo(1);
    }

    @Test
    void getModuleBlockedTotalReturnsZeroForUnknownModule() {
        assertThat(manager.getModuleBlockedTotal("nonexistent")).isEqualTo(0);
    }

    @Test
    void getModuleBlockedTodayReturnsCorrectCount() {
        manager.recordBlock("module-a");
        manager.recordBlock("module-a");

        assertThat(manager.getModuleBlockedToday("module-a")).isEqualTo(2);
    }

    @Test
    void getModuleBlockedTodayReturnsZeroForUnknownModule() {
        assertThat(manager.getModuleBlockedToday("nonexistent")).isEqualTo(0);
    }

    @Test
    void getAllModuleTotalsReturnsAllModules() {
        manager.recordBlock("module-a");
        manager.recordBlock("module-b");
        manager.recordBlock("module-b");

        var totals = manager.getAllModuleTotals();
        assertThat(totals).containsEntry("module-a", 1L);
        assertThat(totals).containsEntry("module-b", 2L);
    }

    @Test
    void recordAttackAddsToHistory() {
        long now = System.currentTimeMillis();
        manager.recordAttack(now - 60000, now, 150, 500);

        assertThat(manager.getAttackCount()).isEqualTo(1);
        assertThat(manager.getAttackHistory()).hasSize(1);

        var record = manager.getAttackHistory().get(0);
        assertThat(record.peakConnectionRate).isEqualTo(150);
        assertThat(record.blockedCount).isEqualTo(500);
    }

    @Test
    void attackHistoryTrimmedToMaxSize() {
        // The max is 100 per our config mock
        for (int i = 0; i < 110; i++) {
            long now = System.currentTimeMillis();
            manager.recordAttack(now - 1000, now, i, i * 10L);
        }
        assertThat(manager.getAttackCount()).isEqualTo(100);
    }

    @Test
    void attackRecordDurationSeconds() {
        StatisticsManager.AttackRecord record = new StatisticsManager.AttackRecord();
        record.startTime = 1000;
        record.endTime = 61000;
        assertThat(record.getDurationSeconds()).isEqualTo(60);
    }

    @Test
    void isEnabledReturnsTrue() {
        assertThat(manager.isEnabled()).isTrue();
    }
}
