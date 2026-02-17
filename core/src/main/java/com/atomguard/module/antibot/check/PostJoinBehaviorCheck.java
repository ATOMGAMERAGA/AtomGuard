package com.atomguard.module.antibot.check;

import com.atomguard.module.antibot.AntiBotModule;
import com.atomguard.module.antibot.PlayerProfile;

public class PostJoinBehaviorCheck extends AbstractCheck {
    
    public PostJoinBehaviorCheck(AntiBotModule module) {
        super(module, "giris-sonrasi-davranis");
    }

    @Override
    public int calculateThreatScore(PlayerProfile profile) {
        int ticks = profile.getTicksSinceJoin();
        int analysisTime = module.getConfigInt("kontroller.giris-sonrasi-davranis.analiz-suresi-tick", 1200); // 600'den 1200'e çıkarıldı
        
        if (ticks < analysisTime) {
            return 0;
        }

        // FP-12: Sohbet eden oyuncuları bu kontrolden muaf tut
        if (profile.getFirstChatDelayMs() > 0) {
            return 0;
        }

        int score = 0;

        // 1. Chat delay (Already covered by exemption but kept for logic)
        long chatDelay = profile.getFirstChatDelayMs();
        if (chatDelay > 0 && chatDelay < 200) {
            score += 10;
        }

        // 2. Movement variety
        if (profile.getUniquePositionCount() < 3) {
            score += 5;
        }

        // 3. Interactions
        if (module.getAttackTracker().isUnderAttack()) {
            if (!profile.hasInteractedWithInventory()) score += 3; // 5'ten 3'e düşürüldü
            if (!profile.hasInteractedWithWorld()) score += 2; // 3'ten 2'e düşürüldü
        }

        return Math.min(score, 25);
    }
}
