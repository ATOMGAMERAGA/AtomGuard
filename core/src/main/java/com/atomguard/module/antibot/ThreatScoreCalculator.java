package com.atomguard.module.antibot;

import com.atomguard.module.antibot.check.*;
import com.atomguard.module.antibot.action.ActionType;
import java.util.*;

public class ThreatScoreCalculator {
    private final AntiBotModule module;
    private final List<AbstractCheck> checks = new ArrayList<>();

    public ThreatScoreCalculator(AntiBotModule module) {
        this.module = module;
        registerChecks();
    }

    private void registerChecks() {
        checks.add(new ConnectionRateCheck(module));
        checks.add(new PingHandshakeCheck(module));
        checks.add(new UsernamePatternCheck(module));
        checks.add(new ProtocolCheck(module));
        checks.add(new FirstJoinBehaviorCheck(module));
        checks.add(new GravityCheck(module));
        checks.add(new PacketTimingCheck(module));
        checks.add(new PostJoinBehaviorCheck(module));
    }

    public ThreatResult evaluate(PlayerProfile profile) {
        if (module.getWhitelistManager().isWhitelisted(profile.getUuid())) {
            return new ThreatResult(0, ActionType.ALLOW, Collections.emptyMap());
        }

        // FP-08: Doğrulanmış oyuncu cache'indeki oyuncular için kontrolü gevşet veya atla
        boolean isVerified = false;
        if (module.getPlugin().getVerifiedPlayerCache() != null && 
            profile.getUsername() != null && profile.getIpAddress() != null) {
            isVerified = module.getPlugin().getVerifiedPlayerCache().isVerified(profile.getUsername(), profile.getIpAddress());
        }

        int totalScore = 0;
        Map<String, Integer> breakdown = new LinkedHashMap<>();

        for (AbstractCheck check : checks) {
            if (!check.isEnabled()) continue;

            int score = check.calculateThreatScore(profile);
            
            // FP-08: Doğrulanmış oyuncular için saldırı modu çarpanını atla
            if (module.getAttackTracker().isUnderAttack() && !isVerified) {
                score = (int) (score * check.getAttackModeMultiplier());
            }

            breakdown.put(check.getName(), score);
            totalScore += score;
        }

        int oldScore = profile.getCurrentThreatScore();
        profile.setCurrentThreatScore(totalScore);
        profile.updateMaxThreatScore(totalScore);

        // Trigger Event if score changed significantly or reached threshold
        if (oldScore != totalScore && profile.getUuid() != null) {
            org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(profile.getUuid());
            if (player != null) {
                org.bukkit.Bukkit.getPluginManager().callEvent(
                    new com.atomguard.api.event.ThreatScoreChangedEvent(player, oldScore, totalScore, "AntiBot Analysis")
                );
            }
        }

        // Action thresholds
        ActionType action;
        
        // FP-08: Doğrulanmış oyuncular için saldırı modu çarpanını uygulama (multiplier = 1.0)
        double multiplier = (module.getAttackTracker().isUnderAttack() && !isVerified) ? 
                module.getConfigDouble("saldiri-modu.esik-carpani", 0.85) : 1.0; // 0.7'den 0.85'e çıkarıldı

        int allowThreshold = (int) (module.getConfigInt("skor-esikleri.izin-ver", 30) * multiplier);
        int delayThreshold = (int) (module.getConfigInt("skor-esikleri.geciktir", 60) * multiplier);
        int kickThreshold = (int) (module.getConfigInt("skor-esikleri.at", 80) * multiplier);

        if (totalScore < allowThreshold) {
            action = ActionType.ALLOW;
        } else if (totalScore < delayThreshold) {
            action = ActionType.DELAY;
        } else if (totalScore < kickThreshold) {
            action = ActionType.KICK;
        } else {
            action = ActionType.BLACKLIST;
        }

        return new ThreatResult(totalScore, action, breakdown);
    }

    public static class ThreatResult {
        private final int score;
        private final ActionType action;
        private final Map<String, Integer> breakdown;

        public ThreatResult(int score, ActionType action, Map<String, Integer> breakdown) {
            this.score = score;
            this.action = action;
            this.breakdown = breakdown;
        }

        public int getScore() { return score; }
        public ActionType getAction() { return action; }
        public Map<String, Integer> getBreakdown() { return breakdown; }
    }
}
