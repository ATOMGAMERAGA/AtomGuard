package com.atomguard.module;

import com.atomguard.AtomGuard;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Akıllı Lag Tespiti ve Önleme Modülü (Heuristik Analiz)
 *
 * Raporun 4.2.4 maddesi uyarınca:
 * - TickDuration takibi yapar (Watchdog).
 * - 50ms (20 TPS) altına düşüldüğünde ağır chunk'ları tespit eder.
 * - Lag yapan chunk'lardaki işlemleri (fizik, entity) geçici olarak dondurur.
 *
 * @author AtomGuard Team
 * @version 2.0.0
 */
public class SmartLagModule extends AbstractModule implements Listener {

    private volatile long lastTickTime;
    private final Set<Long> frozenChunks = ConcurrentHashMap.newKeySet(); // ChunkKey hash'leri
    private volatile boolean isFreezingActive = false;
    private int freezeDurationTicks = 100; // 5 saniye dondur
    private int watchdogTaskId = -1;
    private static final int MAX_FROZEN_CHUNKS = 10; // Aşırı freeze'i önler
    private static final double SAMPLE_RATIO = 0.20; // Chunk'ların %20'sini tara

    // Config
    private int msThreshold; // 50ms
    private int entityThreshold; // Chunk başına max entity (tespit için)
    private int tileEntityThreshold;

    public SmartLagModule(@NotNull AtomGuard plugin) {
        super(plugin, "smart-lag", "Heuristik lag analizi ve koruması");
    }

    @Override

    public void onEnable() {
        super.onEnable();
        loadConfig();

        lastTickTime = System.currentTimeMillis();

        // Watchdog görevi - Her tick çalışır
        watchdogTaskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tickWatchdog, 1L, 1L).getTaskId();
        
        debug("Akıllı Lag Modülü başlatıldı. Eşik: " + msThreshold + "ms");
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (watchdogTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(watchdogTaskId);
            watchdogTaskId = -1;
        }
        // HandlerList.unregisterAll(this) — super.onDisable() zaten çağırıyor, gereksiz tekrar kaldırıldı
        frozenChunks.clear();
    }

    private void loadConfig() {
        // Config'e varsayılan değerleri eklemek gerekebilir, şimdilik hardcoded/default
        this.msThreshold = getConfigInt("lag-threshold-ms", 50);
        this.entityThreshold = getConfigInt("entity-threshold-per-chunk", 50);
        this.tileEntityThreshold = getConfigInt("tile-entity-threshold-per-chunk", 30);
    }

    private void tickWatchdog() {
        if (!isEnabled()) return;

        long currentTime = System.currentTimeMillis();
        long delta = currentTime - lastTickTime;
        lastTickTime = currentTime;

        // Eğer bir önceki tick 50ms'den uzun sürdüyse (veya toleransla 60ms)
        if (delta > msThreshold + 10) {
            // Lag tespiti!
            handleLagSpike(delta);
        } else {
            // TPS toparladıysa dondurmayı kaldır
            if (isFreezingActive && delta < 45) {
                if (!frozenChunks.isEmpty()) {
                    debug("TPS normalleşti, chunk kilitleri kaldırılıyor...");
                    unfreezeAll();
                    frozenChunks.clear();
                    isFreezingActive = false;
                }
            }
        }
    }

    private void handleLagSpike(long deltaMs) {
        if (isFreezingActive) return; // Zaten işlem yapıyoruz

        // Sadece çok ciddi lag durumunda (örneğin >100ms veya sürekli 50ms üstü)
        // Çok agresif olmamak için basit bir rate limit
        if (deltaMs < 80) return; // 80ms altındaki spike'ları yut

        debug("Lag Spike Tespiti: " + deltaMs + "ms! Ağır chunk'lar aranıyor...");
        
        isFreezingActive = true;
        int found = 0;

        for (World world : Bukkit.getWorlds()) {
            Chunk[] loadedChunks = world.getLoadedChunks();

            // %20 örnekleme — tüm chunk'ları taramak kendisi lag yapar
            List<Chunk> sample = new ArrayList<>(List.of(loadedChunks));
            Collections.shuffle(sample);
            int sampleSize = Math.max(1, (int) (sample.size() * SAMPLE_RATIO));
            sample = sample.subList(0, sampleSize);

            for (Chunk chunk : sample) {
                if (found >= MAX_FROZEN_CHUNKS) break; // Maksimum sınıra ulaşıldı

                int entities = chunk.getEntities().length;
                int tiles = chunk.getTileEntities().length;

                if (entities > entityThreshold || tiles > tileEntityThreshold) {
                    frozenChunks.add(Chunk.getChunkKey(chunk.getX(), chunk.getZ()));
                    found++;

                    // Chunk'taki mobları dondur (AI kapat)
                    for (Entity e : chunk.getEntities()) {
                        if (e instanceof Mob mob) {
                            mob.setAware(false);
                        } else if (e instanceof Item || e instanceof Projectile) {
                            if (entities > entityThreshold * 2) {
                                e.remove();
                            }
                        }
                    }
                }
            }
            if (found >= MAX_FROZEN_CHUNKS) break;
        }

        if (found > 0) {
            logExploit("Sistem", String.format("Lag koruması aktif! %d chunk %d tick boyunca donduruldu. (Gecikme: %dms)", 
                    found, freezeDurationTicks, deltaMs));
            
            // Belirli süre sonra kilidi kesin kaldır (Watchdog kaldırmazsa)
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                unfreezeAll();
                frozenChunks.clear();
                isFreezingActive = false;
            }, freezeDurationTicks);
        } else {
            isFreezingActive = false; // Suçlu chunk bulunamadı
        }
    }

    private void unfreezeAll() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                long key = Chunk.getChunkKey(chunk.getX(), chunk.getZ());
                if (frozenChunks.contains(key)) {
                    for (Entity e : chunk.getEntities()) {
                        if (e instanceof Mob mob) {
                            mob.setAware(true);
                        }
                    }
                }
            }
        }
    }

    // --- Listeners: Dondurulmuş chunk'lardaki işlemleri engelle ---

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        if (!isFreezingActive) return;
        Chunk chunk = event.getEntity().getChunk();
        long key = Chunk.getChunkKey(chunk.getX(), chunk.getZ());
        
        if (frozenChunks.contains(key)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRedstone(BlockRedstoneEvent event) {
        if (!isFreezingActive) return;
        Chunk chunk = event.getBlock().getChunk();
        long key = Chunk.getChunkKey(chunk.getX(), chunk.getZ());

        if (frozenChunks.contains(key)) {
            // Dispenser/dropper sinyallerini engelleme — normal kullanım kırılır
            org.bukkit.Material type = event.getBlock().getType();
            if (type == org.bukkit.Material.DISPENSER || type == org.bukkit.Material.DROPPER) {
                return;
            }
            event.setNewCurrent(0); // Redstone'u kapat
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (!isFreezingActive) return;
        Chunk chunk = event.getBlock().getChunk();
        long key = Chunk.getChunkKey(chunk.getX(), chunk.getZ());

        if (frozenChunks.contains(key)) {
            // Su ve lav fiziğini engelleme — kovadan yerleştirilen sıvı akmaz
            org.bukkit.Material type = event.getBlock().getType();
            if (type == org.bukkit.Material.WATER || type == org.bukkit.Material.LAVA) {
                return;
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        if (!isFreezingActive) return;
        Chunk chunk = event.getEntity().getChunk();
        long key = Chunk.getChunkKey(chunk.getX(), chunk.getZ());

        if (frozenChunks.contains(key)) {
            org.bukkit.entity.Entity entity = event.getEntity();
            // Oyuncular, dropper'dan düşen item'lar ve dispenser'dan fırlatılan
            // mermiler hiçbir zaman engellenmez
            if (entity instanceof Player
                    || entity instanceof Item
                    || entity instanceof Projectile
                    || entity instanceof org.bukkit.entity.ArmorStand
                    || entity instanceof org.bukkit.entity.ItemFrame) {
                return;
            }
            event.setCancelled(true);
        }
    }
}
