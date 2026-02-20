package com.atomguard.velocity.pipeline;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.data.PlayerBehaviorProfile;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class TrustScoreCheck implements ConnectionCheck {
    private final AtomGuardVelocity plugin;

    public TrustScoreCheck(AtomGuardVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String name() { return "trust-score"; }

    @Override
    public int priority() { return 15; } // Firewall (10) sonrası

    @Override
    public boolean isEnabled() {
        return plugin.getBehaviorManager() != null;
    }

    @Override
    public @NotNull CheckResult check(@NotNull ConnectionContext ctx) {
        PlayerBehaviorProfile profile = plugin.getBehaviorManager().getProfile(ctx.ip());
        int score = profile.calculateTrustScore();
        
        int threshold = plugin.getConfigManager().getInt("moduller.bot-koruma.guven-skoru-esik", 10);
        
        // Güven skoru eşiğin altındaysa, girişi reddet
        if (score < threshold) {
            return CheckResult.deny(
                plugin.getMessageManager().buildKickMessage("kick.trust-score", 
                    Map.of("score", String.valueOf(score), "threshold", String.valueOf(threshold))),
                name(),
                "low-trust-score"
            );
        }
        return CheckResult.allowed();
    }
}
