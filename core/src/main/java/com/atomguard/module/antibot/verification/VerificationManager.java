package com.atomguard.module.antibot.verification;

import com.atomguard.module.antibot.AntiBotModule;
import com.atomguard.module.antibot.PlayerProfile;
import com.atomguard.module.antibot.ThreatScoreCalculator;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Doğrulama yöneticisi. Şüpheli oyuncular için tehdit skoru hesaplaması başlatır ve sonuca göre aksiyon alır.
 *
 * Config: {@code moduller.anti-bot}
 *
 * @author AtomGuard Team
 * @version 2.0.0
 */
public class VerificationManager {
    private final AntiBotModule module;
    private final Map<UUID, BukkitTask> verificationTasks = new ConcurrentHashMap<>();

    public VerificationManager(AntiBotModule module) {
        this.module = module;
    }

    public void startVerification(Player player, PlayerProfile profile) {
        if (module.getWhitelistManager().isWhitelisted(player.getUniqueId())) return;
        
        if (module.getPlugin().getVerifiedPlayerCache() != null && 
            module.getPlugin().getVerifiedPlayerCache().isVerified(player.getName(), profile.getIpAddress())) {
            if (module.getPlugin().getVerifiedPlayerCache().shouldSkipBotCheck()) {
                return;
            }
        }

        BukkitTask task = new BukkitRunnable() {
            private int cycleCounter = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    stopVerification(profile);
                    return;
                }

                profile.tick();
                cycleCounter++;

                // Threat evaluation — her 2 cycle'da bir (2 saniyede bir)
                if (cycleCounter % 2 == 0) {
                    ThreatScoreCalculator.ThreatResult result = module.getThreatScoreCalculator().evaluate(profile);
                    module.getActionExecutor().executePeriodic(player, profile, result);
                }

                // Whitelist evaluation — her 10 cycle'da bir (10 saniyede bir)
                if (cycleCounter % 10 == 0 && module.getConfigBoolean("whitelist.auto-verify", true)) {
                    module.getWhitelistManager().evaluateForWhitelist(profile);
                }
            }
        }.runTaskTimerAsynchronously(module.getPlugin(), 20L, 20L);
        
        verificationTasks.put(player.getUniqueId(), task);
    }

    public void stopVerification(PlayerProfile profile) {
        if (profile.getUuid() == null) return;
        BukkitTask task = verificationTasks.remove(profile.getUuid());
        if (task != null) {
            task.cancel();
        }
    }
}
