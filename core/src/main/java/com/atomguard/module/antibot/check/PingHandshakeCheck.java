package com.atomguard.module.antibot.check;

import com.atomguard.module.antibot.AntiBotModule;
import com.atomguard.module.antibot.PlayerProfile;

public class PingHandshakeCheck extends AbstractCheck {
    
    public PingHandshakeCheck(AntiBotModule module) {
        super(module, "ping-handshake");
    }

    @Override
    public int calculateThreatScore(PlayerProfile profile) {
        long now = System.currentTimeMillis();
        long lastPing = profile.getLastPingTime();
        
        if (lastPing == 0) {
            // FP-03: Ping yokluğunu tek başına şüpheli sayma — sadece diğer checklerle birlikte ağırlık kazansın.
            // Normal modda varsayılan 0, saldırı modunda varsayılan 5.
            return module.getAttackTracker().isUnderAttack() ? 
                    module.getConfigInt("kontroller.ping-handshake.ping-yok-skor-saldiri", 5) : 
                    module.getConfigInt("kontroller.ping-handshake.ping-yok-skor-normal", 0);
        }

        long timeSincePing = now - profile.getHandshakeTime();
        if (timeSincePing < 500) {
            return 10;
        }

        // Protocol version check could be added if we store it from the ping packet
        // For now, simple timing is enough as specified in the prompt
        
        return 0;
    }
}
