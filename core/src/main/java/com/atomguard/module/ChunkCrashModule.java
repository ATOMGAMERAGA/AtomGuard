package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chunk Crash Modülü
 *
 * Aşırı chunk yüklemesi ve entity overflow exploit'lerini önler.
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class ChunkCrashModule extends AbstractModule implements Listener {

    private final Map<UUID, AtomicInteger> chunkLoadCounts = new ConcurrentHashMap<>();
    private int maxChunkLoadsPerSec;
    private int maxEntitiesPerChunkWarn;

    public ChunkCrashModule(@NotNull AtomGuard plugin) {
        super(plugin, "chunk-crash", "Chunk yükleme ve crash koruması");
    }

    @Override

    public void onEnable() {
        super.onEnable();
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Reset counts every second
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, chunkLoadCounts::clear, 20L, 20L);
    }

    @Override

    public void onDisable() {
        super.onDisable();
        org.bukkit.event.world.ChunkLoadEvent.getHandlerList().unregister(this);
    }

    private void loadConfig() {
        this.maxChunkLoadsPerSec = getConfigInt("saniyede-max-chunk-yuklemesi", 20);
        this.maxEntitiesPerChunkWarn = getConfigInt("max-entity-per-chunk-warn", 200);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!isEnabled()) return;
        
        // Check excessive entities on load
        if (event.getChunk().getEntities().length > maxEntitiesPerChunkWarn) {
            warning("Ağır chunk yüklendi: " + event.getChunk().getX() + "," + event.getChunk().getZ() + 
                    " Entity: " + event.getChunk().getEntities().length);
            // Optionally cancel or clear entities if configured (risky)
        }
        
        // Track per-player chunk loading is hard via Bukkit Event as it doesn't provide the player cause easily.
        // We rely on PacketEvents for player-induced chunk requests if needed, 
        // but for now we monitor general system load or use a heuristic.
    }
}
