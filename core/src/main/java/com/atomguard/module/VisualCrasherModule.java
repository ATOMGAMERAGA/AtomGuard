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

    public VisualCrasherModule(@NotNull AtomGuard plugin) {
        super(plugin, "gorsel-crasher", "Havai fişek ve partikül koruması");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.maxFireworkEffects = getConfigInt("max-havai-fiseke-efekt", 15);
        this.maxParticlePackets = getConfigInt("max-partikul-paketi-saniye", 100);

        // PacketEvents listener for particles - Merkezi Listener üzerinden
        registerSendHandler(PacketType.Play.Server.PARTICLE, this::handleParticlePacket);
        
        debug("Görsel crasher koruması başlatıldı.");
    }

    private void handleParticlePacket(PacketSendEvent event) {
        if (!isEnabled()) return;
        if (event.getUser() == null || event.getUser().getUUID() == null) return;
        
        UUID uuid = event.getUser().getUUID();
        int count = particleCounts.computeIfAbsent(uuid, k -> new AtomicInteger(0)).incrementAndGet();
        
        if (count > maxParticlePackets) {
            event.setCancelled(true);
            incrementBlockedCount();
        }
    }

    @Override
    public void cleanup() {
        particleCounts.clear();
    }

    @Override
    public void onDisable() {
        super.onDisable();
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
