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

        // 3. Interactions — FP-07: Saldırı modunda bile etkileşim skorları minimumda tutuldu
        if (module.getAttackTracker().isUnderAttack()) {
            if (!profile.hasInteractedWithInventory()) score += 1; // 3'ten 1'e düşürüldü
            if (!profile.hasInteractedWithWorld()) score += 1; // 2'den 1'e düşürüldü
        }

        return Math.min(score, 25);
    }
}
