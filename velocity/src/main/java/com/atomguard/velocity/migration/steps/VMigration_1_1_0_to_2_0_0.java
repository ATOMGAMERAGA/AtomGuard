package com.atomguard.velocity.migration.steps;

import com.atomguard.velocity.migration.VelocityMigrationStep;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;

/**
 * v1.1.0 -> v2.0.0 Velocity migration adimi.
 * Yeni sistemler:
 * - Bedrock destek modülü (bedrock-destek)
 *
 * @author AtomGuard Team
 * @version 2.0.0
 */
public class VMigration_1_1_0_to_2_0_0 implements VelocityMigrationStep {

    @Override
    public String getFromVersion() { return "1.1.0"; }

    @Override
    public String getToVersion() { return "2.0.0"; }

    @Override
    public String getDescription() {
        return "v1.1.0 -> v2.0.0: Bedrock destek modulu eklendi";
    }

    @Override
    public void migrate(CommentedConfigurationNode config) throws Exception {
        // --- bedrock-destek modül yapılandırması ---
        setIfAbsent(config, "moduller.bedrock-destek.aktif", false);
        setIfAbsent(config, "moduller.bedrock-destek.prefix", ".");
        setIfAbsent(config, "moduller.bedrock-destek.xbox-bonus", 5);
        setIfAbsent(config, "moduller.bedrock-destek.rate-limit-carpan", 1.5);

        // --- Config versiyonunu güncelle ---
        config.node("config-versiyon").set("2.0.0");
    }

    private void setIfAbsent(CommentedConfigurationNode root, String path, Object value) throws ConfigurateException {
        String[] parts = path.split("\\.");
        CommentedConfigurationNode node = root.node((Object[]) parts);
        if (node.virtual()) {
            node.set(value);
        }
    }
}
