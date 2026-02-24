package com.atomguard.migration;

import com.atomguard.AtomGuard;
import com.atomguard.migration.steps.Migration_1_0_0_to_1_1_0;
import com.atomguard.migration.steps.Migration_1_1_0_to_1_1_1;
import com.atomguard.migration.steps.Migration_1_1_1_to_1_2_0;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Versiyon bazlı yapılandırma migration yöneticisi.
 * Zincirleme migration, otomatik yedekleme ve akıllı birleştirme destekler.
 *
 * @author AtomGuard Team
 * @version 1.2.0
 */
public class ConfigMigrationManager {

    private final AtomGuard plugin;
    private final List<MigrationStep> registeredSteps = new ArrayList<>();

    public ConfigMigrationManager(AtomGuard plugin) {
        this.plugin = plugin;
        registerAllSteps();
    }

    private void registerAllSteps() {
        registeredSteps.add(new Migration_1_0_0_to_1_1_0());
        registeredSteps.add(new Migration_1_1_0_to_1_1_1());
        registeredSteps.add(new Migration_1_1_1_to_1_2_0());
    }

    /**
     * Config'i belirtilen versiyondan hedef versiyona migrate eder.
     * Zincirli migration: her ara versiyon tek tek işlenir.
     */
    public MigrationResult migrate(FileConfiguration config, String fromVersion, String toVersion) {
        if (fromVersion.equals(toVersion)) {
            return new MigrationResult.Builder(fromVersion, toVersion).success(true).build();
        }

        // Yedek oluştur
        backupConfig(fromVersion);

        // Tüm migration sonuçlarını birleştirmek için
        MigrationResult.Builder combined = new MigrationResult.Builder(fromVersion, toVersion);
        long totalStart = System.currentTimeMillis();

        // Semantic versiyona göre sıralanmış adımları çalıştır
        String current = fromVersion;

        for (MigrationStep step : registeredSteps) {
            // current versiyondan başlayan adımları uygula
            if (step.getFromVersion().equals(current)
                    && compareVersions(step.getToVersion(), toVersion) <= 0) {

                plugin.getLogger().info("[Migration] " + step.getFromVersion()
                    + " → " + step.getToVersion() + ": " + step.getDescription());

                MigrationResult stepResult = step.migrate(config);

                // Sonuçları birleştir
                stepResult.getAddedKeys().forEach(combined::addKey);
                stepResult.getRemovedKeys().forEach(combined::removeKey);
                stepResult.getRenamedKeys().forEach(combined::renameKey);
                stepResult.getModifiedKeys().forEach(combined::modifyKey);
                stepResult.getWarnings().forEach(combined::warn);
                stepResult.getErrors().forEach(combined::error);

                if (!stepResult.isSuccess()) {
                    combined.error("Migration adımı başarısız: " + step.getFromVersion() + " → " + step.getToVersion());
                    return combined.durationMs(System.currentTimeMillis() - totalStart).success(false).build();
                }

                current = step.getToVersion();
            }
        }

        return combined.durationMs(System.currentTimeMillis() - totalStart).success(true).build();
    }

    /**
     * Migration öncesi config yedeği alır.
     */
    public void backupConfig(String version) {
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        File backupFile = new File(plugin.getDataFolder(), "config.yml.backup-" + version + "-" + timestamp);

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) return;

        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
            cfg.save(backupFile);
            plugin.getLogger().info("[Migration] Yedek oluşturuldu: " + backupFile.getName());
        } catch (IOException e) {
            plugin.getLogger().warning("[Migration] Yedek oluşturulamadı: " + e.getMessage());
        }
    }

    /**
     * Son yedeği geri yükler.
     */
    public boolean restoreBackup(String version) {
        File dataFolder = plugin.getDataFolder();
        File[] backups = dataFolder.listFiles(f ->
            f.getName().startsWith("config.yml.backup-" + version + "-"));

        if (backups == null || backups.length == 0) {
            plugin.getLogger().warning("[Migration] Geri yüklenecek yedek bulunamadı: " + version);
            return false;
        }

        // En yeni yedeği bul
        File latest = backups[0];
        for (File b : backups) {
            if (b.lastModified() > latest.lastModified()) latest = b;
        }

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        try {
            FileConfiguration backup = YamlConfiguration.loadConfiguration(latest);
            backup.save(configFile);
            plugin.getLogger().info("[Migration] Yedekten geri yüklendi: " + latest.getName());
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("[Migration] Geri yükleme başarısız: " + e.getMessage());
            return false;
        }
    }

    /**
     * Migration sonucunu konsola yazar.
     */
    public void logMigrationResult(MigrationResult result) {
        plugin.getLogger().info("[AtomGuard] ═══ Config Migration: "
            + result.getFromVersion() + " → " + result.getToVersion() + " ═══");

        if (!result.getAddedKeys().isEmpty()) {
            plugin.getLogger().info("[AtomGuard] + " + result.getAddedKeys().size() + " yeni ayar eklendi");
        }
        if (!result.getRemovedKeys().isEmpty()) {
            plugin.getLogger().info("[AtomGuard] - " + result.getRemovedKeys().size() + " eski ayar kaldırıldı");
        }
        if (!result.getRenamedKeys().isEmpty()) {
            plugin.getLogger().info("[AtomGuard] ~ " + result.getRenamedKeys().size() + " ayar yeniden adlandırıldı");
        }
        if (!result.getWarnings().isEmpty()) {
            result.getWarnings().forEach(w -> plugin.getLogger().warning("[AtomGuard] ⚠ " + w));
        }
        if (!result.getErrors().isEmpty()) {
            result.getErrors().forEach(e -> plugin.getLogger().severe("[AtomGuard] ✗ " + e));
        }

        String status = result.isSuccess() ? "Migration başarıyla tamamlandı" : "Migration başarısız";
        plugin.getLogger().info("[AtomGuard] ═══ " + status + " (" + result.getDurationMs() + "ms) ═══");
    }

    /**
     * Semantic versiyon karşılaştırması: -1, 0, veya 1 döner.
     */
    private int compareVersions(String v1, String v2) {
        int[] a = parseVersion(v1);
        int[] b = parseVersion(v2);

        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) return Integer.compare(a[i], b[i]);
        }
        return 0;
    }

    private int[] parseVersion(String version) {
        String[] parts = version.split("\\.");
        int[] result = new int[3];
        for (int i = 0; i < 3 && i < parts.length; i++) {
            try { result[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException ignored) {}
        }
        return result;
    }
}
