package com.atomguard.migration.steps;

import com.atomguard.migration.MigrationResult;
import com.atomguard.migration.MigrationStep;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * v1.1.0 → v1.1.1 migration adımı.
 * Doğrulanmış önbellek ve istatistik güncellemeleri.
 */
public class Migration_1_1_0_to_1_1_1 implements MigrationStep {

    @Override
    public String getFromVersion() { return "1.1.0"; }

    @Override
    public String getToVersion() { return "1.1.1"; }

    @Override
    public String getDescription() { return "v1.1.0 -> v1.1.1: Güvenlik düzeltmeleri, geliştirilmiş önbellek ve istatistik ayarları"; }

    @Override
    public MigrationResult migrate(FileConfiguration config) {
        long start = System.currentTimeMillis();
        MigrationResult.Builder result = new MigrationResult.Builder("1.1.0", "1.1.1");

        addIfAbsent(config, "dogrulanmis-onbellek.aktif", true, result);
        addIfAbsent(config, "dogrulanmis-onbellek.sure-saat", 48, result);
        addIfAbsent(config, "dogrulanmis-onbellek.bot-kontrolu-atla", true, result);
        addIfAbsent(config, "dogrulanmis-onbellek.ip-kontrolu-atla", false, result);
        addIfAbsent(config, "istatistik.max-saldiri-gecmisi", 100, result);
        addIfAbsent(config, "web-panel.aktif", true, result);
        addIfAbsent(config, "web-panel.port", 8081, result);
        addIfAbsent(config, "web-panel.kimlik-dogrulama.aktif", true, result);
        addIfAbsent(config, "web-panel.kimlik-dogrulama.kullanici-adi", "admin", result);
        addIfAbsent(config, "web-panel.kimlik-dogrulama.sifre", "atomguard2024", result);
        addIfAbsent(config, "web-panel.max-olay-sayisi", 100, result);

        return result.durationMs(System.currentTimeMillis() - start).success(true).build();
    }

    private void addIfAbsent(FileConfiguration config, String key, Object value, MigrationResult.Builder result) {
        if (!config.contains(key)) {
            config.set(key, value);
            result.addKey(key);
        }
    }
}
