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
 * @version 1.0.0
 */
public class AdvancedChatModule extends AbstractModule implements Listener {

    private static final Pattern CRASH_PATTERN = Pattern.compile("[\u0590-\u05ff\u0600-\u06ff\u0750-\u077f\u08a0-\u08ff\u0fb0-\u0fff\u1100-\u11ff\u1200-\u137f\u2000-\u206f\u3130-\u318f\ua960-\ua97f\uac00-\ud7af\ud7b0-\ud7ff\ufe70-\ufeff]");
    
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
        super(plugin, "gelismis-sohbet", "Sohbet ve Tab-Complete güvenliği");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.maxTabRequests = getConfigInt("max-tab-istegi-saniye", 5);
        this.filterUnicode = getConfigBoolean("unicode-filtre", true);
        this.maxMessagesPerSecond = getConfigInt("hiz-limiti.saniyede-max", 3);
        this.capsThresholdPercent = getConfigInt("buyuk-harf-spam.esik-yuzde", 80);
        this.minCapsLength = getConfigInt("buyuk-harf-spam.minimum-uzunluk", 10);
        this.antiSpam = getConfigBoolean("ayni-mesaj.aktif", true);
        this.antiCaps = getConfigBoolean("buyuk-harf-spam.aktif", true);
        this.antiFlood = getConfigBoolean("hiz-limiti.aktif", true);

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
                if (sameCount >= getConfigInt("ayni-mesaj.hatirlama-sayisi", 3)) {
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
