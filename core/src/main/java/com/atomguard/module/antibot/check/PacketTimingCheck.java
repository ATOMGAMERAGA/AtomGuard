package com.atomguard.module.antibot.check;

import com.atomguard.module.antibot.AntiBotModule;
import com.atomguard.module.antibot.PlayerProfile;

public class PacketTimingCheck extends AbstractCheck {
    
    public PacketTimingCheck(AntiBotModule module) {
        super(module, "paket-zamanlama");
    }

    @Override
    public int calculateThreatScore(PlayerProfile profile) {
        int score = 0;

        // 1. Position packet frequency
        double avgInterval = profile.getAveragePositionPacketInterval();
        int minInterval = module.getConfigInt("kontroller.paket-zamanlama.min-interval-ms", 30);
        int maxInterval = module.getConfigInt("kontroller.paket-zamanlama.max-interval-ms", 150);
        
        if (avgInterval > 0) {
            if (avgInterval < minInterval) score += 15;
            else if (avgInterval > maxInterval) score += 5;
        }

        // 2. Variance
        double variance = profile.getPositionPacketVariance();
        if (variance >= 0 && variance < 0.1 && profile.getPositionPacketCount() > 50) {
            score += 10; // 15'ten 10'a düşürüldü, eşikler sıkılaştırıldı (1.0 -> 0.1, 20 -> 50)
        }

        // 3. Keep-alive response
        long keepAliveMs = profile.getAverageKeepAliveResponseMs();
        int minKeepAlive = module.getConfigInt("kontroller.paket-zamanlama.min-keepalive-ms", 2); // 5'ten 2'ye düşürüldü
        if (keepAliveMs > 0 && keepAliveMs < minKeepAlive) {
            score += 20;
        }

        return Math.min(score, 40);
    }
}
