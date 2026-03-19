package com.atomguard.module.antibot.check;

import com.atomguard.module.antibot.AntiBotModule;
import com.atomguard.module.antibot.PlayerProfile;

/**
 * Paket zamanlama kontrolü. Konum paketlerinin frekansını ve varyansını analiz ederek bot davranışı tespit eder.
 *
 * Config: {@code moduller.anti-bot.kontroller.paket-zamanlama}
 *
 * @author AtomGuard Team
 * @version 2.0.0
 */
public class PacketTimingCheck extends AbstractCheck {
    
    public PacketTimingCheck(AntiBotModule module) {
        super(module, "paket-zamanlama");
    }

    @Override
    public int calculateThreatScore(PlayerProfile profile) {
        int score = 0;

        // 1. Position packet frequency
        double avgInterval = profile.getAveragePositionPacketInterval();
        int minInterval = module.getConfigInt("checks.packet-timing.min-interval-ms", 30);
        int maxInterval = module.getConfigInt("checks.packet-timing.max-interval-ms", 150);
        
        if (avgInterval > 0) {
            if (avgInterval < minInterval) score += 15;
            else if (avgInterval > maxInterval) score += 5;
        }

        // 2. Variance — çok düşük variance = perfect timer = muhtemelen bot
        double variance = profile.getPositionPacketVariance();
        if (variance >= 0 && profile.getPositionPacketCount() > 30) {
            if (variance < 5.0) {
                // Neredeyse sıfır varyans — insan olamaz
                score += 15;
            }
        }

        // 3. Keep-alive response
        long keepAliveMs = profile.getAverageKeepAliveResponseMs();
        int minKeepAlive = module.getConfigInt("checks.packet-timing.min-keepalive-ms", 2); // 5'ten 2'ye düşürüldü
        if (keepAliveMs > 0 && keepAliveMs < minKeepAlive) {
            score += 20;
        }

        return Math.min(score, 40);
    }
}
