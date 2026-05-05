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
 * Frame Crash Modülü
 *
 * Chunk başına item frame sayısını kontrol eder ve crash exploit'lerini önler.
 * EntitySpawnEvent ile frame spawn tracking yapar.
 *
 * Not: Armor stand limiti TAMAMEN KALDIRILDI (v2.2.9) — armor stand'in vanilla
 * crash exploit'i yoktur, harita-sanatı / detaylı yapılarda yanlış engelleme
 * yapıyordu ve "yere koyulmuyor / hep aynı yöne bakıyor" sorunlarına neden
 * oluyordu. Sadece map-NBT crash vektörü olan item frame'ler kontrol altında.
 *
 * @author AtomGuard Team
 * @version 2.2.9
 */
public class FrameCrashModule extends AbstractModule implements Listener {

    // Chunk başına item frame sayısını tutan map
    private final Map<ChunkKey, AtomicInteger> frameCounts;

    // Config cache
    private int maxFramesPerChunk;

    /**
     * FrameCrashModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public FrameCrashModule(@NotNull AtomGuard plugin) {
        super(plugin, "frame-crash", "Item frame crash kontrolü");
        this.frameCounts = new ConcurrentHashMap<>();
    }

    @Override

    public void onEnable() {
        super.onEnable();

        // Config değerlerini yükle
        loadConfig();

        // CR-05: Periyodik temizlik görevi (5 dakikada bir)
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::cleanup, 6000L, 6000L);

        debug("Modül aktifleştirildi. Max frame: " + maxFramesPerChunk);
    }

    @Override

    public void onDisable() {
        super.onDisable();

        frameCounts.clear();

        EntitySpawnEvent.getHandlerList().unregister(this);
        org.bukkit.event.entity.EntityDeathEvent.getHandlerList().unregister(this);
        org.bukkit.event.entity.EntityRemoveEvent.getHandlerList().unregister(this);
        org.bukkit.event.world.ChunkUnloadEvent.getHandlerList().unregister(this);

        debug("Modül devre dışı bırakıldı.");
    }

    // CR-05: Entity silinme takibi
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRemove(org.bukkit.event.entity.EntityRemoveEvent event) {
        handleEntityRemoval(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        handleEntityRemoval(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(org.bukkit.event.world.ChunkUnloadEvent event) {
        clearChunk(event.getChunk());
    }

    private void handleEntityRemoval(Entity entity) {
        if (!isEnabled()) return;

        EntityType type = entity.getType();
        if (type == EntityType.ITEM_FRAME || type == EntityType.GLOW_ITEM_FRAME) {
            // Use location-based key to avoid getChunk() during chunk unload (IllegalStateException)
            ChunkKey key = ChunkKey.fromLocation(entity.getLocation());
            if (frameCounts.containsKey(key)) {
                frameCounts.get(key).decrementAndGet();
            }
        }
    }

    /**
     * Config değerlerini yükler
     */
    private void loadConfig() {
        this.maxFramesPerChunk = getConfigInt("max-frames-per-chunk", 100);

        debug("Config yüklendi: maxFrames=" + maxFramesPerChunk);
    }

    /**
     * Entity spawn olayını dinler — sadece item frame kontrolü, armor stand DEĞIL
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (!isEnabled()) {
            return;
        }

        Entity entity = event.getEntity();
        EntityType type = entity.getType();

        // Sadece item frame'leri kontrol et — armor stand serbest
        if (type == EntityType.ITEM_FRAME || type == EntityType.GLOW_ITEM_FRAME) {
            handleFrameSpawn(event, entity);
        }
    }

    private void handleFrameSpawn(EntitySpawnEvent event, Entity entity) {
        Chunk chunk = entity.getLocation().getChunk();
        ChunkKey key = new ChunkKey(chunk);

        AtomicInteger count = frameCounts.computeIfAbsent(key, k -> new AtomicInteger(countEntitiesInChunk(chunk, EntityType.ITEM_FRAME, EntityType.GLOW_ITEM_FRAME)));

        if (count.incrementAndGet() > maxFramesPerChunk) {
            count.decrementAndGet();
            event.setCancelled(true);
            incrementBlockedCount();
            debug("Frame spawn engellendi (limit aşımı)");
        }
    }

    private int countEntitiesInChunk(@NotNull Chunk chunk, EntityType... types) {
        int count = 0;
        for (Entity entity : chunk.getEntities()) {
            for (EntityType type : types) {
                if (entity.getType() == type) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    /**
     * Chunk'ın frame sayısını döndürür
     */
    public int getFrameCount(@NotNull Chunk chunk) {
        ChunkKey key = new ChunkKey(chunk);
        AtomicInteger count = frameCounts.get(key);
        return count != null ? count.get() : 0;
    }

    /**
     * Chunk'ı temizler
     */
    public void clearChunk(@NotNull Chunk chunk) {
        ChunkKey key = new ChunkKey(chunk);
        frameCounts.remove(key);
        debug("Chunk temizlendi: " + key);
    }

    /**
     * Tüm kayıtları temizler
     */
    public void clearAll() {
        frameCounts.clear();
        debug("Tüm frame kayıtları temizlendi");
    }

    /**
     * Memory optimization - yüklü olmayan chunk kayıtlarını temizler
     */
    public void cleanup() {
        frameCounts.entrySet().removeIf(entry -> {
            ChunkKey key = entry.getKey();
            org.bukkit.World world = plugin.getServer().getWorld(key.worldName);
            return world == null || !world.isChunkLoaded(key.x, key.z);
        });
    }

    /**
     * Chunk anahtarı sınıfı
     */
    private static class ChunkKey {
        private final String worldName;
        private final int x;
        private final int z;
        private final int hashCode;

        public ChunkKey(@NotNull Chunk chunk) {
            this(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        }

        public ChunkKey(@NotNull String worldName, int chunkX, int chunkZ) {
            this.worldName = worldName;
            this.x = chunkX;
            this.z = chunkZ;
            this.hashCode = computeHashCode();
        }

        /**
         * Build a ChunkKey from a Location without calling getChunk() (safe during chunk unload).
         * Chunk coordinates are derived via bit-shift to avoid triggering a chunk load.
         */
        public static ChunkKey fromLocation(@NotNull org.bukkit.Location loc) {
            return new ChunkKey(loc.getWorld().getName(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        }

        private int computeHashCode() {
            return java.util.Objects.hash(worldName, x, z);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ChunkKey other)) return false;
            return this.x == other.x &&
                   this.z == other.z &&
                   this.worldName.equals(other.worldName);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            return String.format("%s[%d,%d]", worldName, x, z);
        }
    }

    /**
     * Modül istatistiklerini döndürür
     */
    public String getStatistics() {
        return String.format("Takip edilen chunk: %d, Engellenen frame: %d",
            frameCounts.size(),
            getBlockedCount());
    }
}
