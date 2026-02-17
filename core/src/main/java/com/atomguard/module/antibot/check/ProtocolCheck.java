package com.atomguard.module.antibot.check;

import com.atomguard.module.antibot.AntiBotModule;
import com.atomguard.module.antibot.PlayerProfile;
import java.util.Set;

public class ProtocolCheck extends AbstractCheck {
    
    private static final Set<String> LEGITIMATE_BRANDS = Set.of(
        "vanilla", "fabric", "forge", "lunarclient",
        "optifine", "quilt", "iris", "sodium",
        "feather", "labymod", "badlion", "pvplounge"
    );

    public ProtocolCheck(AntiBotModule module) {
        super(module, "protokol");
    }

    @Override
    public int calculateThreatScore(PlayerProfile profile) {
        int score = 0;
        int ticks = profile.getTicksSinceJoin();

        // 1. Client Settings check
        int settingsWait = module.getConfigInt("kontroller.protokol.client-settings-bekleme-tick", 100);
        if (!profile.hasSentClientSettings() && ticks > settingsWait) {
            score += 15;
        }

        // 2. Client Brand check
        int brandWait = module.getConfigInt("kontroller.protokol.client-brand-bekleme-tick", 60);
        String brand = profile.getClientBrand();
        if (brand == null && ticks > brandWait) {
            score += 10;
        } else if (brand != null) {
            if (brand.length() > 64) score += 15;
            else if (brand.isEmpty()) score += 10;
        }

        // 3. Hostname check
        String hostname = profile.getHandshakeHostname();
        if (hostname != null && (hostname.isEmpty() || hostname.length() > 255)) {
            score += 10;
        }

        return Math.min(score, 40);
    }
}
