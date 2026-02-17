package com.atomguard.module;

import com.atomguard.AtomGuard;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Patlama Sınırlandırıcı Modülü
 *
 * Saniye başına patlama sayısını ve blok hasarını sınırlar.
 * Kristal patlamalarını görmezden gelir.
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class ExplosionLimiterModule extends AbstractModule implements Listener {

    private final AtomicInteger explosionCount = new AtomicInteger(0);
    private int maxPerSecond;
    private int maxBlockDamage;
    private int taskId = -1;

    public ExplosionLimiterModule(@NotNull AtomGuard plugin) {
        super(plugin, "patlama-sinirlandirici", "Patlama hızı ve hasar sınırlayıcı");
    }

    @Override

    public void onEnable() {
        super.onEnable();
        this.maxPerSecond = getConfigInt("max-patlama-saniye", 10);
        this.maxBlockDamage = getConfigInt("max-blok-hasari", 1000);

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        taskId = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, 
            () -> explosionCount.set(0), 20L, 20L).getTaskId();
    }

    @Override

    public void onDisable() {
        super.onDisable();
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!isEnabled()) return;

        // Kristal patlamalarına karışma (User request)
        if (event.getEntityType() == EntityType.END_CRYSTAL) return;

        if (explosionCount.incrementAndGet() > maxPerSecond) {
            event.setCancelled(true);
            incrementBlockedCount();
            return;
        }

        if (event.blockList().size() > maxBlockDamage) {
            // Blok hasarını sınırla, tamamen iptal etmek yerine listeyi temizle
            int toRemove = event.blockList().size() - maxBlockDamage;
            for (int i = 0; i < toRemove; i++) {
                event.blockList().remove(event.blockList().size() - 1);
            }
            incrementBlockedCount();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!isEnabled()) return;

        if (explosionCount.incrementAndGet() > maxPerSecond) {
            event.setCancelled(true);
            incrementBlockedCount();
            return;
        }

        if (event.blockList().size() > maxBlockDamage) {
            int toRemove = event.blockList().size() - maxBlockDamage;
            for (int i = 0; i < toRemove; i++) {
                event.blockList().remove(event.blockList().size() - 1);
            }
            incrementBlockedCount();
        }
    }
}
