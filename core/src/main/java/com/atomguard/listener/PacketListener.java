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
import java.util.concurrent.CopyOnWriteArrayList;
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

    // Global handlers — CopyOnWriteArrayList: Netty thread'inde synchronized blok gerekmez
    private final List<Consumer<PacketReceiveEvent>> globalReceiveHandlers = new CopyOnWriteArrayList<>();
    private final List<Consumer<PacketSendEvent>> globalSendHandlers = new CopyOnWriteArrayList<>();

    // PERF-02: Bypass permission cache
    // null  → henüz kontrol edilmemiş
    // -1L   → kontrol edildi, bypass yok
    // >0    → bypass var, cachedUntil zaman damgası
    private final Map<UUID, Long> bypassCache = new ConcurrentHashMap<>();
    private static final long BYPASS_CACHE_DURATION = 30_000; // 30 saniye
    private static final long NO_BYPASS_SENTINEL = -1L;

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
        Long cached = bypassCache.get(uuid);
        if (cached == null) return false;              // Henüz kontrol edilmemiş
        if (cached == NO_BYPASS_SENTINEL) return false; // Kontrol edildi, bypass yok
        if (System.currentTimeMillis() < cached) return true; // Bypass var, süresi dolmamış
        // Cache süresi dolmuş — temizle, bir sonraki join'de yeniden kontrol edilecek
        bypassCache.remove(uuid);
        return false;
    }

    /**
     * PlayerJoinEvent'te ana thread'den çağır — hasPermission() burada güvenle kullanılabilir.
     */
    public void checkAndCacheBypass(@NotNull Player player) {
        if (player.hasPermission("atomguard.bypass")) {
            bypassCache.put(player.getUniqueId(), System.currentTimeMillis() + BYPASS_CACHE_DURATION);
        } else {
            bypassCache.put(player.getUniqueId(), NO_BYPASS_SENTINEL);
        }
    }

    /**
     * PlayerQuitEvent'te cache'den temizle
     */
    public void removeBypassCache(@NotNull UUID uuid) {
        bypassCache.remove(uuid);
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
