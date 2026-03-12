package com.atomguard.migration;

import com.atomguard.AtomGuard;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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
class ConfigMigrationManagerTest {

    @Mock private AtomGuard plugin;
    @Mock private java.util.logging.Logger logger;

    @TempDir
    File tempDir;

    private ConfigMigrationManager migrationManager;

    @BeforeEach
    void setUp() {
        when(plugin.getDataFolder()).thenReturn(tempDir);
        when(plugin.getLogger()).thenReturn(logger);

        migrationManager = new ConfigMigrationManager(plugin);
    }

    @Test
    void sameVersionReturnsSuccess() {
        FileConfiguration config = new YamlConfiguration();
        MigrationResult result = migrationManager.migrate(config, "1.2.0", "1.2.0");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFromVersion()).isEqualTo("1.2.0");
        assertThat(result.getToVersion()).isEqualTo("1.2.0");
    }

    @Test
    void migrationChainFrom1_0_0To1_1_0() {
        FileConfiguration config = new YamlConfiguration();
        MigrationResult result = migrationManager.migrate(config, "1.0.0", "1.1.0");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFromVersion()).isEqualTo("1.0.0");
        assertThat(result.getToVersion()).isEqualTo("1.1.0");
    }

    @Test
    void migrationChainFrom1_0_0To1_2_0() {
        FileConfiguration config = new YamlConfiguration();
        MigrationResult result = migrationManager.migrate(config, "1.0.0", "1.2.0");

        // Should chain through: 1.0.0 -> 1.1.0 -> 1.1.1 -> 1.2.0
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFromVersion()).isEqualTo("1.0.0");
        assertThat(result.getToVersion()).isEqualTo("1.2.0");
    }

    @Test
    void migrationChainFrom1_0_0To2_0_0() {
        FileConfiguration config = new YamlConfiguration();
        MigrationResult result = migrationManager.migrate(config, "1.0.0", "2.0.0");

        // Should chain through: 1.0.0 -> 1.1.0 -> 1.1.1 -> 1.2.0 -> 2.0.0
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFromVersion()).isEqualTo("1.0.0");
        assertThat(result.getToVersion()).isEqualTo("2.0.0");
    }

    @Test
    void migrationFrom1_2_0To2_0_0() {
        FileConfiguration config = new YamlConfiguration();
        MigrationResult result = migrationManager.migrate(config, "1.2.0", "2.0.0");

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void noMatchingMigrationPathReturnsSuccess() {
        // From a version that has no matching step, the loop just skips
        FileConfiguration config = new YamlConfiguration();
        MigrationResult result = migrationManager.migrate(config, "0.5.0", "1.0.0");

        // No steps match fromVersion=0.5.0, so it succeeds without applying any steps
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void migrationResultHasAddedKeys() {
        FileConfiguration config = new YamlConfiguration();
        MigrationResult result = migrationManager.migrate(config, "1.0.0", "1.1.0");

        // The migration step should add some keys
        assertThat(result.getAddedKeys()).isNotNull();
    }

    @Test
    void backupConfigCreatesFile() throws Exception {
        // Create a config.yml in tempDir so backup can work
        File configFile = new File(tempDir, "config.yml");
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("test-key", "test-value");
        cfg.save(configFile);

        migrationManager.backupConfig("1.0.0");

        File[] backups = tempDir.listFiles(f -> f.getName().startsWith("config.yml.backup-1.0.0-"));
        assertThat(backups).isNotNull().isNotEmpty();
    }

    @Test
    void restoreBackupReturnsFalseWhenNoBackup() {
        boolean restored = migrationManager.restoreBackup("9.9.9");
        assertThat(restored).isFalse();
    }

    @Test
    void migrationResultBuildingWithErrors() {
        MigrationResult result = new MigrationResult.Builder("1.0.0", "2.0.0")
                .error("Something failed")
                .build();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).contains("Something failed");
    }

    @Test
    void migrationResultBuildingWithWarnings() {
        MigrationResult result = new MigrationResult.Builder("1.0.0", "2.0.0")
                .warn("Deprecated key")
                .success(true)
                .build();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getWarnings()).contains("Deprecated key");
    }
}
