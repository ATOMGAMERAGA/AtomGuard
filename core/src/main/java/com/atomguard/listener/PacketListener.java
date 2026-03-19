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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Central PacketEvents listener that multiplexes packet events to registered module handlers.
 *
 * <p>Instead of each module registering its own {@link PacketListenerAbstract}, this single
 * listener is registered with the PacketEvents event bus. Modules register per-packet-type
 * handlers (or global handlers) via {@code registerReceiveHandler()} and {@code registerSendHandler()},
 * and this class dispatches incoming/outgoing packets to the appropriate handlers.
 *
 * <p><b>Bypass cache (Netty-safe):</b> A Caffeine cache ({@code UUID -> Boolean}, 10-minute TTL)
 * stores bypass-permission results. The cache is populated from the main thread on
 * {@code PlayerJoinEvent} and evicted on {@code PlayerQuitEvent}. The {@code hasBypass(UUID)}
 * method is safe to call from Netty threads because it only reads the cache and never calls
 * {@code Player.hasPermission()}, which is not thread-safe.
 *
 * <p>Handler lists use {@link java.util.concurrent.CopyOnWriteArrayList} so iteration from
 * Netty threads does not require synchronization.
 *
 * @see com.atomguard.module.AbstractModule#registerReceiveHandler
 */
public class PacketListener extends PacketListenerAbstract {

    private final AtomGuard plugin;
    private final Map<PacketTypeCommon, List<Consumer<PacketReceiveEvent>>> receiveHandlers = new ConcurrentHashMap<>();
    private final Map<PacketTypeCommon, List<Consumer<PacketSendEvent>>> sendHandlers = new ConcurrentHashMap<>();

    // Global handlers — CopyOnWriteArrayList: Netty thread'inde synchronized blok gerekmez
    private final List<Consumer<PacketReceiveEvent>> globalReceiveHandlers = new CopyOnWriteArrayList<>();
    private final List<Consumer<PacketSendEvent>> globalSendHandlers = new CopyOnWriteArrayList<>();

    // PERF-02: Bypass permission cache (Caffeine, 10 min TTL)
    private final Cache<UUID, Boolean> bypassCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

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
            receiveHandlers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(handler);
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
            sendHandlers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(handler);
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
        // PERF-02: Bypass kontrolü — sadece cache'e bak, hasPermission() ÇAĞIRMA
        if (event.getUser() != null && event.getUser().getUUID() != null) {
            if (hasBypass(event.getUser().getUUID())) return;
        }

        // Heuristic Engine Entegrasyonu (Geleneksel legacy yapı korunuyor)
        handleLegacyIncoming(event);

        // Global Handlers — CopyOnWriteArrayList: synchronized blok gereksiz
        for (Consumer<PacketReceiveEvent> handler : globalReceiveHandlers) {
            if (event.isCancelled()) break;
            handler.accept(event);
        }

        // Merkezi Dağıtım (PERF-01) — CopyOnWriteArrayList: synchronized blok gereksiz
        List<Consumer<PacketReceiveEvent>> handlers = receiveHandlers.get(event.getPacketType());
        if (handlers != null) {
            for (Consumer<PacketReceiveEvent> handler : handlers) {
                if (event.isCancelled()) break;
                handler.accept(event);
            }
        }
    }

    @Override
    public void onPacketSend(@NotNull PacketSendEvent event) {
        // PERF-02: Bypass kontrolü
        if (event.getUser() != null && event.getUser().getUUID() != null) {
            if (hasBypass(event.getUser().getUUID())) return;
        }

        // Global Handlers
        for (Consumer<PacketSendEvent> handler : globalSendHandlers) {
            if (event.isCancelled()) break;
            handler.accept(event);
        }

        List<Consumer<PacketSendEvent>> handlers = sendHandlers.get(event.getPacketType());
        if (handlers != null) {
            for (Consumer<PacketSendEvent> handler : handlers) {
                if (event.isCancelled()) break;
                handler.accept(event);
            }
        }
    }

    /**
     * Bypass cache kontrolü — Netty thread'inde güvenli, hasPermission() ÇAĞIRMAZ.
     * Cache güncellemesi ana thread'deki checkAndCacheBypass() ile yapılır.
     */
    private boolean hasBypass(UUID uuid) {
        Boolean cached = bypassCache.getIfPresent(uuid);
        return cached != null && cached;
    }

    /**
     * PlayerJoinEvent'te ana thread'den çağır — hasPermission() burada güvenle kullanılabilir.
     */
    public void checkAndCacheBypass(@NotNull Player player) {
        bypassCache.put(player.getUniqueId(), player.hasPermission("atomguard.bypass"));
    }

    /**
     * PlayerQuitEvent'te cache'den temizle
     */
    public void removeBypassCache(@NotNull UUID uuid) {
        bypassCache.invalidate(uuid);
    }

    private void handleLegacyIncoming(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_ROTATION
                && event.getPacketType() != PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION
                && event.getPacketType() != PacketType.Play.Client.ANIMATION) {
            return;
        }

        Player player = null;
        if (event.getUser() != null && event.getUser().getUUID() != null) {
            player = plugin.getServer().getPlayer(event.getUser().getUUID());
        }
        if (player == null) return;

        // Auth grace period kontrolü — auth bekleyen oyuncuların heuristic analizini atla
        com.atomguard.module.OfflinePacketModule offlineModule = plugin.getModuleManager()
                .getModule(com.atomguard.module.OfflinePacketModule.class);
        if (offlineModule != null && offlineModule.isInGracePeriod(player.getUniqueId())) {
            return;
        }

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
