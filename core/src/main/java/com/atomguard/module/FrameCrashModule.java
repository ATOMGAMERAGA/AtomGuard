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
 * Özellikler:
 * - Chunk başına maksimum frame sayısı kontrolü
 * - Item frame ve glow item frame kontrolü
 * - Spawn engelleme
 * - Memory efficient tracking
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class FrameCrashModule extends AbstractModule implements Listener {

    // Chunk başına frame ve armor stand sayısını tutan map
    private final Map<ChunkKey, AtomicInteger> frameCounts;
    private final Map<ChunkKey, AtomicInteger> armorStandCounts;

    // Config cache
    private int maxFramesPerChunk;
    private int maxArmorStandsPerChunk;

    /**
     * FrameCrashModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public FrameCrashModule(@NotNull AtomGuard plugin) {
        super(plugin, "frame-crash", "Item frame ve Armor stand crash kontrolü");
        this.frameCounts = new ConcurrentHashMap<>();
        this.armorStandCounts = new ConcurrentHashMap<>();
    }

    @Override

    public void onEnable() {
        super.onEnable();

        // Config değerlerini yükle
        loadConfig();

        // Event listener kaydet
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        // CR-05: Periyodik temizlik görevi (5 dakikada bir)
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::cleanup, 6000L, 6000L);

        debug("Modül aktifleştirildi. Max frame: " + maxFramesPerChunk + ", Max armor stand: " + maxArmorStandsPerChunk);
    }

    @Override

    public void onDisable() {
        super.onDisable();

        // Map'leri temizle
        frameCounts.clear();
        armorStandCounts.clear();

        // Event listener'ı kaldır
        EntitySpawnEvent.getHandlerList().unregister(this);
        // Remove event handlers
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
            ChunkKey key = new ChunkKey(entity.getLocation().getChunk());
            if (frameCounts.containsKey(key)) {
                frameCounts.get(key).decrementAndGet();
            }
        } else if (type == EntityType.ARMOR_STAND) {
            ChunkKey key = new ChunkKey(entity.getLocation().getChunk());
            if (armorStandCounts.containsKey(key)) {
                armorStandCounts.get(key).decrementAndGet();
            }
        }
    }

    /**
     * Config değerlerini yükler
     */
    private void loadConfig() {
        this.maxFramesPerChunk = getConfigInt("chunk-basina-max-frame", 100);
        this.maxArmorStandsPerChunk = getConfigInt("chunk-basina-max-armor-stand", 50);

        debug("Config yüklendi: maxFrames=" + maxFramesPerChunk + ", maxArmorStands=" + maxArmorStandsPerChunk);
    }

    /**
     * Entity spawn olayını dinler
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (!isEnabled()) {
            return;
        }

        Entity entity = event.getEntity();
        EntityType type = entity.getType();

        // Item frame'leri kontrol et
        if (type == EntityType.ITEM_FRAME || type == EntityType.GLOW_ITEM_FRAME) {
            handleFrameSpawn(event, entity);
        } 
        // Armor stand'leri kontrol et
        else if (type == EntityType.ARMOR_STAND) {
            handleArmorStandSpawn(event, entity);
        }
    }

    private void handleFrameSpawn(EntitySpawnEvent event, Entity entity) {
        Chunk chunk = entity.getLocation().getChunk();
        ChunkKey key = new ChunkKey(chunk);

        AtomicInteger count = frameCounts.computeIfAbsent(key, k -> new AtomicInteger(countEntitiesInChunk(chunk, EntityType.ITEM_FRAME, EntityType.GLOW_ITEM_FRAME)));

        if (count.incrementAndGet() > maxFramesPerChunk) {
            event.setCancelled(true);
            incrementBlockedCount();
            debug("Frame spawn engellendi (limit aşımı)");
        }
    }

    private void handleArmorStandSpawn(EntitySpawnEvent event, Entity entity) {
        Chunk chunk = entity.getLocation().getChunk();
        ChunkKey key = new ChunkKey(chunk);

        AtomicInteger count = armorStandCounts.computeIfAbsent(key, k -> new AtomicInteger(countEntitiesInChunk(chunk, EntityType.ARMOR_STAND)));

        if (count.incrementAndGet() > maxArmorStandsPerChunk) {
            event.setCancelled(true);
            incrementBlockedCount();
            debug("Armor stand spawn engellendi (limit aşımı)");
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
            // Dünya yoksa veya chunk yüklü değilse kaldır
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
            this.worldName = chunk.getWorld().getName();
            this.x = chunk.getX();
            this.z = chunk.getZ();
            this.hashCode = computeHashCode();
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
