package com.atomguard.migration;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Tek bir versiyon migration adımını temsil eder.
 *
 * @author AtomGuard Team
 * @version 1.2.0
 */
public interface MigrationStep {

    /**
     * Kaynak versiyon (örn: "1.1.1")
     */
    String getFromVersion();

    /**
     * Hedef versiyon (örn: "1.2.0")
     */
    String getToVersion();

    /**
     * Migration açıklaması
     */
    String getDescription();

    /**
     * Migration'ı çalıştırır.
     *
     * @param config Mevcut config (doğrudan üzerinde değişiklik yapılır)
     * @return MigrationResult
     */
    MigrationResult migrate(FileConfiguration config);
}
