package com.atomguard.velocity.migration;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.config.VelocityConfigManager;
import com.atomguard.velocity.migration.steps.VMigration_1_1_0_to_2_0_0;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Velocity modülü için versiyon bazlı yapılandırma migration yöneticisi.
 * Zincirleme migration, otomatik yedekleme ve akıllı birleştirme destekler.
 *
 * @author AtomGuard Team
 * @version 2.0.0
 */
public class VelocityConfigMigrationManager {

    private final AtomGuardVelocity plugin;
    private final Logger logger;
    private final Path dataDirectory;
    private final List<VelocityMigrationStep> registeredSteps = new ArrayList<>();

    public VelocityConfigMigrationManager(AtomGuardVelocity plugin) {
        this.plugin = plugin;
        this.logger = plugin.getSlf4jLogger();
        this.dataDirectory = plugin.getDataDirectory();
        registerAllSteps();
    }

    private void registerAllSteps() {
        registeredSteps.add(new VMigration_1_1_0_to_2_0_0());
    }

    /**
     * Config'i mevcut versiyondan hedef versiyona migrate eder.
     * Zincirli migration: her ara versiyon tek tek işlenir.
     *
     * @param targetVersion hedef config versiyonu
     * @return migration sonucu
     */
    public VelocityMigrationResult migrate(String targetVersion) {
        VelocityConfigManager configManager = plugin.getConfigManager();
        CommentedConfigurationNode root = configManager.getRootNode();

        String currentVersion = configManager.getString("config-versiyon", "1.0.0");

        if (currentVersion.equals(targetVersion)) {
            return new VelocityMigrationResult.Builder(currentVersion, targetVersion).success(true).build();
        }

        // Yedek oluştur
        backupConfig(currentVersion);

        VelocityMigrationResult.Builder combined = new VelocityMigrationResult.Builder(currentVersion, targetVersion);
        long totalStart = System.currentTimeMillis();

        String current = currentVersion;

        for (VelocityMigrationStep step : registeredSteps) {
            if (step.getFromVersion().equals(current)
                    && compareVersions(step.getToVersion(), targetVersion) <= 0) {

                logger.info("[Migration] {} -> {}: {}", step.getFromVersion(), step.getToVersion(), step.getDescription());

                VelocityMigrationResult.Builder stepResult = new VelocityMigrationResult.Builder(
                        step.getFromVersion(), step.getToVersion());

                try {
                    step.migrate(root);
                    // Kaydet
                    saveConfig();
                    current = step.getToVersion();
                } catch (Exception e) {
                    combined.error("Migration adimi basarisiz: " + step.getFromVersion()
                            + " -> " + step.getToVersion() + ": " + e.getMessage());
                    return combined.durationMs(System.currentTimeMillis() - totalStart).success(false).build();
                }
            }
        }

        return combined.durationMs(System.currentTimeMillis() - totalStart).success(true).build();
    }

    /**
     * Migration öncesi config yedeği alır.
     */
    public void backupConfig(String version) {
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        Path configPath = dataDirectory.resolve("config.yml");
        Path backupPath = dataDirectory.resolve("config.yml.backup-" + version + "-" + timestamp);

        if (!Files.exists(configPath)) return;

        try {
            Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("[Migration] Yedek olusturuldu: {}", backupPath.getFileName());
        } catch (IOException e) {
            logger.warn("[Migration] Yedek olusturulamadi: {}", e.getMessage());
        }
    }

    /**
     * Son yedeği geri yükler.
     */
    public boolean restoreBackup(String version) {
        try {
            Path[] backups = Files.list(dataDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("config.yml.backup-" + version + "-"))
                    .toArray(Path[]::new);

            if (backups.length == 0) {
                logger.warn("[Migration] Geri yuklenecek yedek bulunamadi: {}", version);
                return false;
            }

            // En yeni yedeği bul
            Path latest = backups[0];
            for (Path b : backups) {
                if (Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(latest)) > 0) {
                    latest = b;
                }
            }

            Path configPath = dataDirectory.resolve("config.yml");
            Files.copy(latest, configPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("[Migration] Yedekten geri yuklendi: {}", latest.getFileName());
            return true;
        } catch (IOException e) {
            logger.error("[Migration] Geri yukleme basarisiz: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Migration sonucunu konsola yazar.
     */
    public void logMigrationResult(VelocityMigrationResult result) {
        logger.info("[AtomGuard] === Config Migration: {} -> {} ===",
                result.getFromVersion(), result.getToVersion());

        if (!result.getAddedKeys().isEmpty()) {
            logger.info("[AtomGuard] + {} yeni ayar eklendi", result.getAddedKeys().size());
        }
        if (!result.getRemovedKeys().isEmpty()) {
            logger.info("[AtomGuard] - {} eski ayar kaldirildi", result.getRemovedKeys().size());
        }
        if (!result.getRenamedKeys().isEmpty()) {
            logger.info("[AtomGuard] ~ {} ayar yeniden adlandirildi", result.getRenamedKeys().size());
        }
        if (!result.getWarnings().isEmpty()) {
            result.getWarnings().forEach(w -> logger.warn("[AtomGuard] ! {}", w));
        }
        if (!result.getErrors().isEmpty()) {
            result.getErrors().forEach(e -> logger.error("[AtomGuard] X {}", e));
        }

        String status = result.isSuccess() ? "Migration basariyla tamamlandi" : "Migration basarisiz";
        logger.info("[AtomGuard] === {} ({}ms) ===", status, result.getDurationMs());
    }

    /**
     * Config'i diske kaydeder.
     */
    private void saveConfig() throws IOException {
        Path configPath = dataDirectory.resolve("config.yml");
        YamlConfigurationLoader saver = YamlConfigurationLoader.builder().path(configPath).build();
        saver.save(plugin.getConfigManager().getRootNode());
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
