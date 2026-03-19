package com.atomguard.module.antibot.check;

import com.atomguard.module.antibot.AntiBotModule;
import com.atomguard.module.antibot.PlayerProfile;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionRateCheck extends AbstractCheck {
    private final ConcurrentHashMap<String, Deque<Long>> perIpTimestamps = new ConcurrentHashMap<>();
    private final Deque<Long> globalTimestamps = new ConcurrentLinkedDeque<>();
    
    public ConnectionRateCheck(AntiBotModule module) {
        super(module, "baglanti-hizi");
    }

    @Override
    public int calculateThreatScore(PlayerProfile profile) {
        // Oyuncu zaten sunucuda — connection rate artık irrelevant
        if (profile.getFirstJoinTime() > 0) {
            return 0;
        }

        int score = 0;
        String ip = profile.getIpAddress();
        long now = System.currentTimeMillis();
        long windowMs = module.getConfigInt("checks.connection-rate.window-ms", 10000);

        // Bağlantı timestamp'ini sadece pre-login aşamasında (henüz join olmamış) ekle.
        // Periodic (in-game) değerlendirmede tekrar ekleme — false positive önleme.
        boolean isPreLogin = profile.getFirstJoinTime() == 0;

        // Global rate
        if (isPreLogin) globalTimestamps.addLast(now);
        cleanOldEntries(globalTimestamps, now, windowMs);
        int globalRate = globalTimestamps.size();

        // Per-IP rate
        Deque<Long> ipDeque = perIpTimestamps.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());
        if (isPreLogin) ipDeque.addLast(now);
        cleanOldEntries(ipDeque, now, windowMs);
        int ipRate = ipDeque.size();

        int globalThreshold = module.getConfigInt("checks.connection-rate.global-threshold", 30);
        int ipThreshold = module.getConfigInt("checks.connection-rate.per-ip-threshold", 5);

        if (globalRate > globalThreshold) {
            score += Math.min((globalRate - globalThreshold), 8);
        }

        if (ipRate > ipThreshold) {
            score += Math.min((ipRate - ipThreshold) * 5, 30);
        }

        return score;
    }

    private void cleanOldEntries(Deque<Long> deque, long now, long windowMs) {
        while (!deque.isEmpty() && (now - deque.peekFirst()) > windowMs) {
            deque.pollFirst();
        }
    }
}
