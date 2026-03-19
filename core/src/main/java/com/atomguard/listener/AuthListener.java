package com.atomguard.listener;

import com.atomguard.AtomGuard;
import com.atomguard.module.OfflinePacketModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic Auth Listener — herhangi bir login plugini ile çalışır.
 *
 * AuthMe, nLogin, OpeNLogin, LoginSecurity, JPremium vb. hepsi desteklenir.
 * Auth komutlarını tespit ederek OfflinePacketModule grace period'unu yönetir.
 * Yapılandırma: modules.offline-packet.auth-commands
 *
 * @author AtomGuard Team
 * @version 2.0.5
 */
public class AuthListener implements Listener {

    private final AtomGuard plugin;
    /** Auth beklenen oyuncular — UUID → pending */
    private final Map<UUID, Boolean> pendingAuth = new ConcurrentHashMap<>();

    public AuthListener(@NotNull AtomGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Oyuncu komut girdiğinde kontrol eder.
     * Auth komutuysa grace period'u yeniler.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(@NotNull PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().split(" ")[0].toLowerCase();

        OfflinePacketModule offlineModule = plugin.getModuleManager()
                .getModule(OfflinePacketModule.class);
        if (offlineModule == null) return;

        Set<String> authCmds = offlineModule.getAuthCommands();
        if (authCmds.contains(command)) {
            offlineModule.onAuthCommand(player.getUniqueId());
            pendingAuth.put(player.getUniqueId(), true);
        }
    }

    /**
     * Oyuncu ayrıldığında pending auth verisini temizler.
     */
    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        pendingAuth.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Oyuncunun auth bekleyip beklemediğini döndürür.
     *
     * @param uuid Oyuncu UUID'si
     * @return Auth bekliyorsa true
     */
    public boolean isPendingAuth(@NotNull UUID uuid) {
        return pendingAuth.getOrDefault(uuid, false);
    }

    /**
     * Oyuncunun auth işlemini tamamladığını işaretler.
     * Başarılı login sonrası login plugin'i tarafından çağrılabilir.
     *
     * @param uuid Oyuncu UUID'si
     */
    public void markAuthenticated(@NotNull UUID uuid) {
        pendingAuth.remove(uuid);
        OfflinePacketModule offlineModule = plugin.getModuleManager()
                .getModule(OfflinePacketModule.class);
        if (offlineModule != null) {
            offlineModule.onAuthComplete(uuid);
        }
    }
}
