package com.atomguard.module;

import com.atomguard.AtomGuard;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.TabCompleteEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Gelişmiş Sohbet ve Tab-Complete Modülü
 *
 * Unicode crash karakterlerini ve tab-complete spamini engeller.
 *
 * @author AtomGuard Team
 * @version 2.0.0
 */
public class AdvancedChatModule extends AbstractModule implements Listener {

    // Sadece gerçek crash vektörleri: Unicode yön kontrol karakterleri ve private use alan sonu
    // Arapça/İbranice/Korece/CJK gibi meşru dilleri engellemiyor
    private static final Pattern CRASH_PATTERN = Pattern.compile(
            "[\u2066-\u2069\u202a-\u202e\u200b-\u200f\ufff0-\uffff]");
    
    private final Map<UUID, AtomicInteger> tabRequests = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastChatTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMessage = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> sameMessageCount = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> floodCount = new ConcurrentHashMap<>();
    
    private int maxTabRequests;
    private int maxMessagesPerSecond;
    private int capsThresholdPercent;
    private int minCapsLength;
    private boolean filterUnicode;
    private boolean antiSpam;
    private boolean antiCaps;
    private boolean antiFlood;

    public AdvancedChatModule(@NotNull AtomGuard plugin) {
        super(plugin, "advanced-chat", "Sohbet ve Tab-Complete güvenliği");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.maxTabRequests = getConfigInt("max-tab-requests-per-second", 5);
        this.filterUnicode = getConfigBoolean("unicode-filter", true);
        this.maxMessagesPerSecond = getConfigInt("rate-limit.saniyede-max", 3);
        this.capsThresholdPercent = getConfigInt("caps-spam.esik-yuzde", 80);
        this.minCapsLength = getConfigInt("caps-spam.minimum-uzunluk", 10);
        this.antiSpam = getConfigBoolean("duplicate-message.aktif", true);
        this.antiCaps = getConfigBoolean("caps-spam.aktif", true);
        this.antiFlood = getConfigBoolean("rate-limit.aktif", true);

        // Clear maps periodically
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            tabRequests.clear();
            floodCount.clear();
        }, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(io.papermc.paper.event.player.AsyncChatEvent event) {
        if (!isEnabled()) return;
        
        org.bukkit.entity.Player player = event.getPlayer();
        String message = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message());

        // Unicode Check
        if (filterUnicode && CRASH_PATTERN.matcher(message).find()) {
            event.setCancelled(true);
            incrementBlockedCount();
            return;
        }

        // Rate Limit (Flood)
        if (antiFlood) {
            int count = floodCount.computeIfAbsent(player.getUniqueId(), k -> 0) + 1;
            floodCount.put(player.getUniqueId(), count);
            if (count > maxMessagesPerSecond) {
                event.setCancelled(true);
                incrementBlockedCount();
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<red>Çok hızlı yazıyorsunuz!"));
                return;
            }
        }

        // Duplicate Check
        if (antiSpam) {
            String last = lastMessage.get(player.getUniqueId());
            if (last != null && last.equalsIgnoreCase(message)) {
                int sameCount = sameMessageCount.computeIfAbsent(player.getUniqueId(), k -> 0) + 1;
                sameMessageCount.put(player.getUniqueId(), sameCount);
                if (sameCount >= getConfigInt("duplicate-message.hatirlama-sayisi", 3)) {
                    event.setCancelled(true);
                    incrementBlockedCount();
                    player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<red>Lütfen aynı mesajı tekrarlamayın."));
                    return;
                }
            } else {
                lastMessage.put(player.getUniqueId(), message);
                sameMessageCount.put(player.getUniqueId(), 1);
            }
        }

        // Caps Check
        if (antiCaps && message.length() >= minCapsLength) {
            long capsCount = message.chars().filter(Character::isUpperCase).count();
            double percent = (double) capsCount / message.length() * 100.0;
            if (percent >= capsThresholdPercent) {
                event.setCancelled(true);
                incrementBlockedCount();
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<red>Lütfen büyük harf kullanmayın."));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTabComplete(TabCompleteEvent event) {
        if (!isEnabled()) return;

        // Konsol vb. değilse kontrol et
        if (event.getSender() instanceof org.bukkit.entity.Player player) {
            AtomicInteger count = tabRequests.computeIfAbsent(player.getUniqueId(), k -> new AtomicInteger(0));
            if (count.incrementAndGet() > maxTabRequests) {
                event.setCancelled(true);
                incrementBlockedCount();
            }
        }
    }
}
