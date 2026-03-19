package com.atomguard.module.antibot.check;

import com.atomguard.module.antibot.AntiBotModule;
import com.atomguard.module.antibot.PlayerProfile;

/**
 * Giriş sonrası davranış kontrolü. Oyuncunun sunucuya katıldıktan sonraki hareketlerini analiz ederek bot tespiti yapar.
 *
 * Config: {@code moduller.anti-bot.kontroller.giris-sonrasi-davranis}
 *
 * @author AtomGuard Team
 * @version 2.0.0
 */
public class PostJoinBehaviorCheck extends AbstractCheck {
    
    public PostJoinBehaviorCheck(AntiBotModule module) {
        super(module, "giris-sonrasi-davranis");
    }

    @Override
    public int calculateThreatScore(PlayerProfile profile) {
        int ticks = profile.getTicksSinceJoin();
        // Tick düzeltmesi sonrası: 600 tick = 600 saniye = 10 dakika
        // Bu check sadece uzun süreli AFK botları tespit etmeli
        int analysisTime = module.getConfigInt("checks.post-join-behavior.analysis-ticks", 600);

        if (ticks < analysisTime) {
            return 0;
        }

        int score = 0;

        boolean hasChat = profile.getFirstChatDelayMs() > 0;
        boolean hasMovement = profile.getUniquePositionCount() >= 3;
        boolean hasInteraction = profile.hasInteractedWithInventory() || profile.hasInteractedWithWorld();

        // Herhangi bir etkileşim varsa → muhtemelen gerçek oyuncu
        if (hasChat || hasInteraction) {
            return 0;
        }

        // Hiç hareket yok + hiç etkileşim yok = şüpheli (ama AFK de olabilir)
        if (!hasMovement) {
            score += 5;
        }

        // Saldırı modunda biraz daha sıkı
        if (module.getAttackTracker().isUnderAttack() && !hasMovement && !hasChat) {
            score += 3;
        }

        return Math.min(score, 10);
    }
}
