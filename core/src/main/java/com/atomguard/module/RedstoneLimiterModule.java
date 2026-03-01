package com.atomguard.module;

import com.atomguard.AtomGuard;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Redstone Limiter Modülü
 *
 * Chunk başına redstone güncellemelerini sınırlandırarak
 * redstone lag makinesi exploit'lerini önler.
 *
 * Algoritma:
 * - Her chunk için saniye başına güncelleme sayacı tutulur
 * - Sayaç limiti aşarsa o chunk'ta 5 saniye boyunca redstone iptal edilir
 * - Her saniye sayaçlar sıfırlanır
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class RedstoneLimiterModule extends AbstractModule implements Listener {

    /** Chunk başına güncelleme sayacı — key: (chunkX << 32) | (chunkZ & 0xFFFFFFFFL) */
    private final Map<Long, AtomicInteger> chunkUpdateCounts = new ConcurrentHashMap<>();

    /** Cooldown'daki chunk'lar (redstone geçici olarak devre dışı) */
    private final Set<Long> cooldownChunks = ConcurrentHashMap.newKeySet();

    private int resetTaskId = -1;

    // Config cache
    private int maxUpdatesPerSecond;
    private int cooldownSeconds;

    /**
     * RedstoneLimiterModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public RedstoneLimiterModule(@NotNull AtomGuard plugin) {
        super(plugin, "redstone-sinirlandirici", "Redstone güncelleme sınırlandırıcı");
    }

    @Override

    public void onEnable() {
        super.onEnable();
        loadConfig();

        // Her saniye sayaçları sıfırla (20 tick = 1 saniye)
        resetTaskId = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            chunkUpdateCounts.clear();
        }, 20L, 20L).getTaskId();

        debug("Redstone limiter başlatıldı. Max güncelleme/sn: " + maxUpdatesPerSecond +
                ", Cooldown: " + cooldownSeconds + "s");
    }

    @Override

    public void onDisable() {
        super.onDisable();
        HandlerList.unregisterAll(this);

        if (resetTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(resetTaskId);
            resetTaskId = -1;
        }

        chunkUpdateCounts.clear();
        cooldownChunks.clear();

        debug("Redstone limiter durduruldu.");
    }

    /**
     * Config değerlerini yükler
     */
    private void loadConfig() {
        this.maxUpdatesPerSecond = getConfigInt("max-guncelleme-saniye", 1000);
        this.cooldownSeconds = getConfigInt("bekleme-suresi-saniye", 3);
    }

    /**
     * Redstone güncelleme eventi
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockRedstone(@NotNull BlockRedstoneEvent event) {
        if (!isEnabled()) return;

        Chunk chunk = event.getBlock().getChunk();
        long chunkKey = chunkKey(chunk.getX(), chunk.getZ());

        // Cooldown'daki chunk'ta tüm redstone'u iptal et
        if (cooldownChunks.contains(chunkKey)) {
            event.setNewCurrent(event.getOldCurrent()); // Değişikliği iptal et
            return;
        }

        // Güncelleme sayacını artır
        AtomicInteger count = chunkUpdateCounts.computeIfAbsent(chunkKey, k -> new AtomicInteger(0));
        int newCount = count.incrementAndGet();

        // Limiti aştıysa cooldown başlat
        if (newCount > maxUpdatesPerSecond) {
            cooldownChunks.add(chunkKey);
            incrementBlockedCount();

            event.setNewCurrent(event.getOldCurrent()); // Bu güncellemeyi de iptal et

            info(String.format("Redstone limiti aşıldı! Chunk [%d, %d], Güncelleme: %d/s (limit: %d), " +
                    "%d saniye cooldown başlatıldı",
                    chunk.getX(), chunk.getZ(), newCount, maxUpdatesPerSecond, cooldownSeconds));

            // Cooldown süresi sonunda chunk'ı serbest bırak
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                cooldownChunks.remove(chunkKey);
                debug("Chunk cooldown sona erdi: [" + chunk.getX() + ", " + chunk.getZ() + "]");
            }, cooldownSeconds * 20L);
        }
    }

    /**
     * Chunk koordinatlarından benzersiz anahtar üretir
     *
     * @param chunkX Chunk X koordinatı
     * @param chunkZ Chunk Z koordinatı
     * @return Benzersiz long anahtar
     */
    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
