package com.atomguard.velocity.migration;

import org.spongepowered.configurate.CommentedConfigurationNode;

/**
 * Velocity modülü için tek bir versiyon migration adımını temsil eder.
 *
 * @author AtomGuard Team
 * @version 2.0.0
 */
public interface VelocityMigrationStep {

    /**
     * Kaynak versiyon (örn: "1.1.0")
     */
    String getFromVersion();

    /**
     * Hedef versiyon (örn: "2.0.0")
     */
    String getToVersion();

    /**
     * Migration açıklaması
     */
    String getDescription();

    /**
     * Migration'ı çalıştırır.
     *
     * @param config Mevcut Configurate config node (doğrudan üzerinde değişiklik yapılır)
     * @throws Exception migration sırasında oluşabilecek hatalar
     */
    void migrate(CommentedConfigurationNode config) throws Exception;
}
