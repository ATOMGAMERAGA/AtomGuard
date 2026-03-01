package com.atomguard.module;

import com.atomguard.AtomGuard;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kum/Çakıl (Falling Block) Sınırlandırıcı Modülü
 *
 * Çok fazla düşen bloğun sunucuyu yormasını önler.
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class FallingBlockLimiterModule extends AbstractModule implements Listener {

    private final Map<Long, AtomicInteger> chunkCounts = new ConcurrentHashMap<>();
    private int maxPerChunk;

    public FallingBlockLimiterModule(@NotNull AtomGuard plugin) {
        super(plugin, "kum-cakil-sinirlandirici", "Düşen blok sınırlayıcı");
    }

    @Override

    public void onEnable() {
        super.onEnable();
        this.maxPerChunk = getConfigInt("max-dusen-blok-chunk", 64);

        // Periyodik temizlik
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, chunkCounts::clear, 200L, 200L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallingBlockSpawn(EntitySpawnEvent event) {
        if (!isEnabled() || event.getEntityType() != EntityType.FALLING_BLOCK) return;

        Chunk chunk = event.getLocation().getChunk();
        long key = (long) chunk.getX() << 32 | (chunk.getZ() & 0xFFFFFFFFL);

        AtomicInteger count = chunkCounts.computeIfAbsent(key, k -> {
            int existing = 0;
            for (Entity e : chunk.getEntities()) {
                if (e.getType() == EntityType.FALLING_BLOCK) existing++;
            }
            return new AtomicInteger(existing);
        });

        if (count.incrementAndGet() > maxPerChunk) {
            event.setCancelled(true);
            incrementBlockedCount();
            if (getBlockedCount() % 10 == 0) {
                debug("Aşırı düşen blok engellendi: " + chunk.getX() + "," + chunk.getZ());
            }
        }
    }
}
