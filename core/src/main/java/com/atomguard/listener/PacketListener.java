package com.atomguard.listener;

import com.atomguard.AtomGuard;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAnimation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPositionAndRotation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerRotation;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Merkezi Paket Yönlendiricisi (PERF-01)
 * 
 * Tüm modüllerin paket dinleyicilerini tek bir noktada toplar.
 * Her paket için sadece bir kez PacketEvents event bus tetiklenir, 
 * ardından ilgili modüllere dağıtılır.
 */
public class PacketListener extends PacketListenerAbstract {

    private final AtomGuard plugin;
    private final Map<PacketTypeCommon, List<Consumer<PacketReceiveEvent>>> receiveHandlers = new ConcurrentHashMap<>();
    private final Map<PacketTypeCommon, List<Consumer<PacketSendEvent>>> sendHandlers = new ConcurrentHashMap<>();
    
    // Global handlers
    private final List<Consumer<PacketReceiveEvent>> globalReceiveHandlers = Collections.synchronizedList(new ArrayList<>());
    private final List<Consumer<PacketSendEvent>> globalSendHandlers = Collections.synchronizedList(new ArrayList<>());

    // PERF-02: Bypass permission cache
    private final Map<UUID, Long> bypassCache = new ConcurrentHashMap<>();
    private static final long BYPASS_CACHE_DURATION = 30_000; // 30 saniye

    public PacketListener(@NotNull AtomGuard plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
    }

    /**
     * Bir modül için paket alma işleyicisi kaydeder
     */
    public void registerReceiveHandler(PacketTypeCommon type, Consumer<PacketReceiveEvent> handler) {
        if (type == null) {
            globalReceiveHandlers.add(handler);
        } else {
            receiveHandlers.computeIfAbsent(type, k -> Collections.synchronizedList(new ArrayList<>())).add(handler);
        }
    }

    /**
     * Bir modül için paket alma işleyicisi kaydını kaldırır
     */
    public void unregisterReceiveHandler(PacketTypeCommon type, Consumer<PacketReceiveEvent> handler) {
        if (type == null) {
            globalReceiveHandlers.remove(handler);
        } else {
            List<Consumer<PacketReceiveEvent>> handlers = receiveHandlers.get(type);
            if (handlers != null) {
                handlers.remove(handler);
            }
        }
    }

    /**
     * Bir modül için paket gönderme işleyicisi kaydeder
     */
    public void registerSendHandler(PacketTypeCommon type, Consumer<PacketSendEvent> handler) {
        if (type == null) {
            globalSendHandlers.add(handler);
        } else {
            sendHandlers.computeIfAbsent(type, k -> Collections.synchronizedList(new ArrayList<>())).add(handler);
        }
    }

    /**
     * Bir modül için paket gönderme işleyicisi kaydını kaldırır
     */
    public void unregisterSendHandler(PacketTypeCommon type, Consumer<PacketSendEvent> handler) {
        if (type == null) {
            globalSendHandlers.remove(handler);
        } else {
            List<Consumer<PacketSendEvent>> handlers = sendHandlers.get(type);
            if (handlers != null) {
                handlers.remove(handler);
            }
        }
    }

    @Override
    public void onPacketReceive(@NotNull PacketReceiveEvent event) {
        // PERF-02: Bypass kontrolü (Cache-based)
        if (event.getUser() != null && event.getUser().getUUID() != null) {
            if (hasBypass(event.getUser().getUUID())) return;
        }

        // Heuristic Engine Entegrasyonu (Geleneksel legacy yapı korunuyor)
        handleLegacyIncoming(event);

        // Global Handlers
        synchronized (globalReceiveHandlers) {
            for (Consumer<PacketReceiveEvent> handler : globalReceiveHandlers) {
                if (event.isCancelled()) break;
                handler.accept(event);
            }
        }

        // Merkezi Dağıtım (PERF-01)
        List<Consumer<PacketReceiveEvent>> handlers = receiveHandlers.get(event.getPacketType());
        if (handlers != null) {
            synchronized (handlers) {
                for (Consumer<PacketReceiveEvent> handler : handlers) {
                    if (event.isCancelled()) break;
                    handler.accept(event);
                }
            }
        }
    }

    @Override
    public void onPacketSend(@NotNull PacketSendEvent event) {
        // PERF-02: Bypass kontrolü (Cache-based)
        if (event.getUser() != null && event.getUser().getUUID() != null) {
            if (hasBypass(event.getUser().getUUID())) return;
        }

        // Global Handlers
        synchronized (globalSendHandlers) {
            for (Consumer<PacketSendEvent> handler : globalSendHandlers) {
                if (event.isCancelled()) break;
                handler.accept(event);
            }
        }

        List<Consumer<PacketSendEvent>> handlers = sendHandlers.get(event.getPacketType());
        if (handlers != null) {
            synchronized (handlers) {
                for (Consumer<PacketSendEvent> handler : handlers) {
                    if (event.isCancelled()) break;
                    handler.accept(event);
                }
            }
        }
    }

    /**
     * Permission cache check for bypass (PERF-02)
     */
    private boolean hasBypass(UUID uuid) {
        Long cachedUntil = bypassCache.get(uuid);
        if (cachedUntil != null && System.currentTimeMillis() < cachedUntil) {
            return true;
        }
        
        // Cache miss - asenkron kontrol yapıp cache'e ekle (ana thread'e gitmeden)
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null && player.hasPermission("atomguard.bypass")) {
            cacheBypassPermission(uuid, true);
            return true;
        }
        return false;
    }

    /**
     * PlayerJoinEvent'te veya hasBypass'ta cache'e ekle
     */
    public void cacheBypassPermission(UUID uuid, boolean hasBypass) {
        if (hasBypass) {
            bypassCache.put(uuid, System.currentTimeMillis() + BYPASS_CACHE_DURATION);
        } else {
            bypassCache.remove(uuid);
        }
    }

    private void handleLegacyIncoming(PacketReceiveEvent event) {
        Player player = null;
        if (event.getUser() != null && event.getUser().getUUID() != null) {
            player = plugin.getServer().getPlayer(event.getUser().getUUID());
        }
        if (player == null) return;

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_ROTATION) {
            WrapperPlayClientPlayerRotation wrapper = new WrapperPlayClientPlayerRotation(event);
            plugin.getHeuristicEngine().analyzeRotation(player, wrapper.getYaw(), wrapper.getPitch());
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            WrapperPlayClientPlayerPositionAndRotation wrapper = new WrapperPlayClientPlayerPositionAndRotation(event);
            plugin.getHeuristicEngine().analyzeRotation(player, wrapper.getYaw(), wrapper.getPitch());
        } else if (event.getPacketType() == PacketType.Play.Client.ANIMATION) {
            WrapperPlayClientAnimation wrapper = new WrapperPlayClientAnimation(event);
            if (wrapper.getHand() == com.github.retrooper.packetevents.protocol.player.InteractionHand.MAIN_HAND) {
                plugin.getHeuristicEngine().analyzeClick(player);
            }
        }
    }
}