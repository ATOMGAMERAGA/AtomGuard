package com.atomguard.module;

import com.atomguard.AtomGuard;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Piston Sınırlandırıcı Modülü
 *
 * Piston hareketlerini saniye başına sınırlar ve sıfır-tick makinelerini engeller.
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class PistonLimiterModule extends AbstractModule implements Listener {

    private final Map<Long, AtomicInteger> pistonUpdates = new ConcurrentHashMap<>();
    private int maxUpdates;

    public PistonLimiterModule(@NotNull AtomGuard plugin) {
        super(plugin, "piston-sinirlandirici", "Piston hızı sınırlayıcı");
    }

    @Override

    public void onEnable() {
        super.onEnable();
        this.maxUpdates = getConfigInt("max-piston-hareketi-saniye", 50);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, pistonUpdates::clear, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (checkPiston(event.getBlock().getChunk())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (checkPiston(event.getBlock().getChunk())) {
            event.setCancelled(true);
        }
    }

    private boolean checkPiston(Chunk chunk) {
        if (!isEnabled()) return false;

        long key = (long) chunk.getX() << 32 | (chunk.getZ() & 0xFFFFFFFFL);
        AtomicInteger count = pistonUpdates.computeIfAbsent(key, k -> new AtomicInteger(0));

        if (count.incrementAndGet() > maxUpdates) {
            incrementBlockedCount();
            return true;
        }
        return false;
    }
}
