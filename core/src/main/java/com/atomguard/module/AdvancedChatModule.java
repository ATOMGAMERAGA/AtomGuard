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
    private int maxTabRequests;
    private boolean filterUnicode;

    public AdvancedChatModule(@NotNull AtomGuard plugin) {
        super(plugin, "gelismis-sohbet", "Sohbet ve Tab-Complete güvenliği");
    }

    @Override

    public void onEnable() {
        super.onEnable();
        this.maxTabRequests = getConfigInt("max-tab-istegi-saniye", 5);
        this.filterUnicode = getConfigBoolean("unicode-filtre", true);
        
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, tabRequests::clear, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!isEnabled() || !filterUnicode) return;

        String message = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message());
        
        if (CRASH_PATTERN.matcher(message).find()) {
            event.setCancelled(true);
            incrementBlockedCount();
            debug("Unicode crash karakteri engellendi: " + event.getPlayer().getName());
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
