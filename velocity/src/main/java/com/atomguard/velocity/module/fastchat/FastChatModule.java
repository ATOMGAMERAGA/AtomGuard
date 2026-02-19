package com.atomguard.velocity.module.fastchat;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class FastChatModule extends VelocityModule {

    private ChatRateLimiter rateLimiter;
    private DuplicateMessageDetector duplicateDetector;
    private ChatPatternAnalyzer patternAnalyzer;
    private ScheduledTask cleanupTask;

    public FastChatModule(AtomGuardVelocity plugin) {
        super(plugin, "hizli-sohbet-kontrol");
        this.patternAnalyzer = new ChatPatternAnalyzer();
    }

    @Override
    protected void onEnable() {
        double rate = getConfigDouble("mesaj-hizi.saniyede-max", 3.0);
        double burst = getConfigDouble("mesaj-hizi.burst-izin", 5.0);
        this.rateLimiter = new ChatRateLimiter(rate, burst);
        
        int historySize = getConfigInt("tekrar-mesaj.hatirlama-sayisi", 5);
        this.duplicateDetector = new DuplicateMessageDetector(historySize);

        this.cleanupTask = plugin.getProxyServer().getScheduler().buildTask(plugin, () -> {
            if (rateLimiter != null) rateLimiter.cleanup();
            if (duplicateDetector != null) duplicateDetector.cleanup();
        }).repeat(1, TimeUnit.MINUTES).schedule();
        
        plugin.getProxyServer().getEventManager().register(plugin, this);
        logger.info("FastChat module enabled.");
    }

    @Override
    protected void onDisable() {
        plugin.getProxyServer().getEventManager().unregisterListener(plugin, this);
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        logger.info("FastChat module disabled.");
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        if (!isEnabled()) return;
        
        Player player = event.getPlayer();
        String message = event.getMessage();
        String ip = player.getRemoteAddress().getAddress().getHostAddress();

        // 1. Rate Limit
        if (!rateLimiter.tryConsume(ip)) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                getConfigString("mesajlar.rate-limit", "<red>Çok hızlı mesaj gönderiyorsunuz!")));
            return;
        }

        // 2. Duplicate Check
        if (getConfigBoolean("tekrar-mesaj.aktif", true)) {
            if (duplicateDetector.isDuplicate(ip, message)) {
                event.setResult(PlayerChatEvent.ChatResult.denied());
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                    getConfigString("mesajlar.tekrar-mesaj", "<red>Aynı mesajı tekrar gönderemezsiniz!")));
                return;
            }
        }

        // 3. Pattern Analysis
        if (getConfigBoolean("pattern-analiz.aktif", true)) {
            // Caps
            int capsThreshold = getConfigInt("pattern-analiz.buyuk-harf-yuzde", 80);
            int minLength = getConfigInt("pattern-analiz.minimum-uzunluk", 5);
            if (patternAnalyzer.isCapsSpam(message, capsThreshold, minLength)) {
                event.setResult(PlayerChatEvent.ChatResult.denied());
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Lütfen büyük harf kullanmayın."));
                return;
            }

            // Repeated Chars
            int repeatThreshold = getConfigInt("pattern-analiz.tekrar-karakter-esik", 5);
            if (patternAnalyzer.hasRepeatedCharacters(message, repeatThreshold)) {
                event.setResult(PlayerChatEvent.ChatResult.denied());
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Lütfen harfleri tekrarlamayın."));
                return;
            }
            
            // Link Check
            if (getConfigBoolean("pattern-analiz.link-engel", false)) {
                 if (patternAnalyzer.containsLink(message)) {
                     event.setResult(PlayerChatEvent.ChatResult.denied());
                     player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Link paylaşımı yasaktır."));
                     return;
                 }
            }
        }
    }
}
