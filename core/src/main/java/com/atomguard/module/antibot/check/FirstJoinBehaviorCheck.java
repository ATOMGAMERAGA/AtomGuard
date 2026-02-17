package com.atomguard.module.antibot.check;

import com.atomguard.module.antibot.AntiBotModule;
import com.atomguard.module.antibot.PlayerProfile;

public class FirstJoinBehaviorCheck extends AbstractCheck {
    
    public FirstJoinBehaviorCheck(AntiBotModule module) {
        super(module, "ilk-giris-davranis");
    }

    @Override
    public int calculateThreatScore(PlayerProfile profile) {
        int ticks = profile.getTicksSinceJoin();
        int analysisTime = module.getConfigInt("kontroller.ilk-giris-davranis.analiz-suresi-tick", 100);
        
        if (ticks > analysisTime) {
            return profile.getCachedFirstJoinScore();
        }

        int score = 0;

        // 1. Camera movement
        if (ticks >= 120) { // 60'tan 120'ye çıkarıldı
            if (profile.getUniqueYawValues() <= 1 && profile.getUniquePitchValues() <= 1) {
                score += 8;
            }
        }

        // 2. Position packet presence
        if (ticks >= 80) { // 40'tan 80'e çıkarıldı
            if (!profile.hasSentPositionPacket()) {
                score += 8; // 15'ten 8'e düşürüldü
            }
        }

        // 3. Timing of first movement
        long delay = profile.getFirstMovementDelayMs();
        if (delay > 0 && delay < 20) { // 50'den 20'ye düşürüldü
            score += 5; // 10'dan 5'e düşürüldü
        }

        profile.setCachedFirstJoinScore(score);
        return Math.min(score, 30);
    }
}
