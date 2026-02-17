package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Görsel Crasher Koruması Modülü
 *
 * Havai fişek ve partikül exploit'lerini önler.
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class VisualCrasherModule extends AbstractModule implements Listener {

    private int maxFireworkEffects;
    private int maxParticlePackets;
    private final Map<UUID, AtomicInteger> particleCounts = new ConcurrentHashMap<>();
    private PacketListenerAbstract packetListener;

    public VisualCrasherModule(@NotNull AtomGuard plugin) {
        super(plugin, "gorsel-crasher", "Havai fişek ve partikül koruması");
    }

    @Override

    public void onEnable() {
        super.onEnable();
        this.maxFireworkEffects = getConfigInt("max-havai-fiseke-efekt", 15);
        this.maxParticlePackets = getConfigInt("max-partikul-paketi-saniye", 100);

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // PacketEvents listener for particles
        packetListener = new PacketListenerAbstract(PacketListenerPriority.NORMAL) {
            @Override
            public void onPacketSend(PacketSendEvent event) {
                if (event.getPacketType() == PacketType.Play.Server.PARTICLE) {
                    if (event.getUser() == null || event.getUser().getUUID() == null) return;
                    
                    UUID uuid = event.getUser().getUUID();
                    AtomicInteger count = particleCounts.computeIfAbsent(uuid, k -> new AtomicInteger(0));
                    
                    if (count.incrementAndGet() > maxParticlePackets) {
                        event.setCancelled(true);
                        incrementBlockedCount();
                    }
                }
            }
        };
        com.github.retrooper.packetevents.PacketEvents.getAPI().getEventManager().registerListener(packetListener);

        // CR-09: Race condition fix - Reset counts by clearing map safely or using expiration
        // For simplicity and performance, we just clear it, but we use a new map to avoid iteration issues if concurrent.
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, particleCounts::clear, 20L, 20L);
    }

    @Override

    public void onDisable() {
        super.onDisable();
        if (packetListener != null) {
            com.github.retrooper.packetevents.PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
        }
        particleCounts.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFireworkSpawn(EntitySpawnEvent event) {
        if (!isEnabled() || event.getEntityType() != EntityType.FIREWORK_ROCKET) return;

        Firework firework = (Firework) event.getEntity();
        FireworkMeta meta = firework.getFireworkMeta();

        if (meta.getEffectsSize() > maxFireworkEffects) {
            event.setCancelled(true);
            incrementBlockedCount();
            debug("Aşırı efektli havai fişek engellendi (" + meta.getEffectsSize() + " efekt)");
            return;
        }
        
        // CR-09: Detailed firework checks
        if (meta.getPower() > 5) {
            event.setCancelled(true);
            incrementBlockedCount();
            debug("Aşırı güçlü havai fişek: " + meta.getPower());
            return;
        }
        
        for (org.bukkit.FireworkEffect effect : meta.getEffects()) {
            if (effect.getColors().size() + effect.getFadeColors().size() > 20) {
                event.setCancelled(true);
                incrementBlockedCount();
                debug("Çok fazla renk içeren havai fişek efekti");
                return;
            }
        }
    }
}
