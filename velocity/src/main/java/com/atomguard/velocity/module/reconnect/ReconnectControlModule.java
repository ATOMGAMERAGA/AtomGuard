package com.atomguard.velocity.module.reconnect;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;

import com.velocitypowered.api.scheduler.ScheduledTask;

import java.util.concurrent.TimeUnit;

public class ReconnectControlModule extends VelocityModule {

    private final ReconnectTracker tracker;
    private ScheduledTask cleanupTask;

    public ReconnectControlModule(AtomGuardVelocity plugin) {
        super(plugin, "yeniden-baglanti-kontrol");
        this.tracker = new ReconnectTracker();
    }

    @Override
    public void onConfigReload() {
        onDisable();
        onEnable();
        logger.info("Reconnect Control yapılandırması dinamik olarak yenilendi.");
    }

    @Override
    public void onEnable() {
        cleanupTask = plugin.getProxyServer().getScheduler()
                .buildTask(plugin, tracker::cleanup)
                .repeat(1, TimeUnit.MINUTES)
                .schedule();
        plugin.getProxyServer().getEventManager().register(plugin, this);
        logger.info("Reconnect Control module enabled.");
    }

    @Override
    public void onDisable() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        plugin.getProxyServer().getEventManager().unregisterListener(plugin, this);
        tracker.clear();
        logger.info("Reconnect Control module disabled.");
    }

    public void cleanup() {
        if (tracker != null) tracker.cleanup();
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        if (!isEnabled()) return;

        String ip = event.getConnection().getRemoteAddress().getAddress().getHostAddress();
        
        // 1. Cooldown Check
        if (getConfigBoolean("hizli-yeniden-baglanti.aktif", true)) {
            long cooldown = getConfigLong("hizli-yeniden-baglanti.cooldown-saniye", 10) * 1000;
            long timeLeft = tracker.getCooldownRemaining(ip, cooldown);
            
            if (timeLeft > 0) {
                String msg = getConfigString("aksiyon-mesaji.delay", "<gray>Bağlantı kurulmadan önce {sure} saniye bekleniyor...")
                        .replace("{sure}", String.valueOf(timeLeft / 1000));
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                        plugin.getMessageManager().parse(msg)
                ));
                return;
            }
        }

        // 2. Crash Loop Detection
        if (getConfigBoolean("crash-dongu-tespit.aktif", true)) {
             int threshold = getConfigInt("crash-dongu-tespit.esik-sayisi", 3);
             long window = getConfigLong("crash-dongu-tespit.pencere-saniye", 30) * 1000;
             
             if (tracker.isCrashLoop(ip, threshold, window)) {
                 String action = getConfigString("crash-dongu-tespit.aksiyon", "challenge");
                 if ("kick".equals(action)) {
                     event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                             plugin.getMessageManager().parse(getConfigString("aksiyon-mesaji.kick", "<red>Too many reconnections."))
                     ));
                     return;
                 } else if ("challenge".equals(action)) {
                     // Trigger captcha if available
                     if (plugin.getAntiBotModule() != null) {
                         // Force captcha (not easily possible via this API, but we can mark IP as suspicious)
                         // For now, kick is safer if captcha api is not exposed for direct trigger
                         event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                                 plugin.getMessageManager().parse(getConfigString("aksiyon-mesaji.kick", "<red>Too many reconnections."))
                         ));
                         return;
                     }
                 }
             }
        }
        
        tracker.recordConnect(ip);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (!isEnabled()) return;
        String ip = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();
        long sessionDuration = System.currentTimeMillis() - tracker.getLastConnectTime(ip);
        
        tracker.recordDisconnect(ip);
        
        // Short session check (Join-Quit loop)
        if (getConfigBoolean("kisa-oturum-tespit.aktif", true)) {
            long minSession = getConfigLong("kisa-oturum-tespit.min-oturum-saniye", 3) * 1000;
            if (sessionDuration < minSession) {
                tracker.recordShortSession(ip);
            }
        }
    }
}