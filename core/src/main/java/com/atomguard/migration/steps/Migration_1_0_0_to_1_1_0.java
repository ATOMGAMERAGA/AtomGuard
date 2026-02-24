package com.atomguard.migration.steps;

import com.atomguard.migration.MigrationResult;
import com.atomguard.migration.MigrationStep;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * v1.0.0 → v1.1.0 migration adımı.
 * Attack mode aksiyon ayarları eklendi.
 */
public class Migration_1_0_0_to_1_1_0 implements MigrationStep {

    @Override
    public String getFromVersion() { return "1.0.0"; }

    @Override
    public String getToVersion() { return "1.1.0"; }

    @Override
    public String getDescription() { return "v1.0.0 -> v1.1.0: Saldırı modu aksiyon ayarları eklendi"; }

    @Override
    public MigrationResult migrate(FileConfiguration config) {
        long start = System.currentTimeMillis();
        MigrationResult.Builder result = new MigrationResult.Builder("1.0.0", "1.1.0");

        addIfAbsent(config, "attack-mode.aksiyonlar.dogrulanmamis-ip-engelle", true, result);
        addIfAbsent(config, "attack-mode.aksiyonlar.siki-limitler", true, result);
        addIfAbsent(config, "attack-mode.aksiyonlar.siki-limit-carpani", 0.5, result);
        addIfAbsent(config, "attack-mode.aksiyonlar.otomatik-modul-etkinlestir", true, result);
        addIfAbsent(config, "attack-mode.aksiyonlar.sadece-beyaz-liste", false, result);
        addIfAbsent(config, "attack-mode.aksiyonlar.discord-bildirim", true, result);
        addIfAbsent(config, "redis.enabled", false, result);
        addIfAbsent(config, "redis.host", "localhost", result);
        addIfAbsent(config, "redis.port", 6379, result);
        addIfAbsent(config, "redis.password", "", result);
        addIfAbsent(config, "redis.timeout", 2000, result);

        return result.durationMs(System.currentTimeMillis() - start).success(true).build();
    }

    private void addIfAbsent(FileConfiguration config, String key, Object value, MigrationResult.Builder result) {
        if (!config.contains(key)) {
            config.set(key, value);
            result.addKey(key);
        }
    }
}
