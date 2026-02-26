package com.atomguard.module.antibot.action;

import com.atomguard.module.antibot.AntiBotModule;
import com.atomguard.module.antibot.PlayerProfile;
import com.atomguard.module.antibot.ThreatScoreCalculator;
import org.bukkit.Bukkit;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class ActionExecutor {
    private final AntiBotModule module;

    public ActionExecutor(AntiBotModule module) {
        this.module = module;
    }

    public void executeInitial(AsyncPlayerPreLoginEvent event, PlayerProfile profile, ThreatScoreCalculator.ThreatResult result) {
        switch (result.getAction()) {
            case DELAY -> {
                // Bağlantı kuyruğu — Thread.sleep kaldırıldı (FP-16: login timeout'a neden oluyordu)
                // Yüksek risk eşiğine ulaşmayan bağlantılar sessizce geçirilir
            }
            case KICK -> {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, 
                    module.getPlugin().getMessageManager().getMessage("antibot.kick-mesaji"));
                module.logExploit(profile.getUsername(), "Kicked for high threat score: " + result.getScore());
            }
            case BLACKLIST -> {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, 
                    module.getPlugin().getMessageManager().getMessage("antibot.kara-liste-mesaji"));
                module.getBlacklistManager().blacklist(profile.getIpAddress(), 
                    module.getConfigInt("kara-liste.varsayilan-sure-dk", 60) * 60000L, 
                    "High threat score: " + result.getScore());
                module.logExploit(profile.getUsername(), "Blacklisted for very high threat score: " + result.getScore());
            }
        }
    }

    public void executePeriodic(org.bukkit.entity.Player player, PlayerProfile profile, ThreatScoreCalculator.ThreatResult result) {
        if (result.getAction() == ActionType.KICK || result.getAction() == ActionType.BLACKLIST) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (result.getAction() == ActionType.BLACKLIST) {
                        module.getBlacklistManager().blacklist(profile.getIpAddress(), 
                            module.getConfigInt("kara-liste.varsayilan-sure-dk", 60) * 60000L, 
                            "High threat score during play: " + result.getScore());
                        player.kick(module.getPlugin().getMessageManager().getMessage("antibot.kara-liste-mesaji"));
                    } else {
                        player.kick(module.getPlugin().getMessageManager().getMessage("antibot.kick-mesaji"));
                    }
                }
            }.runTask(module.getPlugin());
            module.logExploit(player.getName(), "Removed during play for high threat score: " + result.getScore() + " (" + result.getAction() + ")");
        }
    }
}
