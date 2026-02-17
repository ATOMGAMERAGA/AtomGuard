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

    public PacketListener(@NotNull AtomGuard plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
    }

    /**
     * Bir modül için paket alma işleyicisi kaydeder
     */
    public void registerReceiveHandler(PacketTypeCommon type, Consumer<PacketReceiveEvent> handler) {
        receiveHandlers.computeIfAbsent(type, k -> Collections.synchronizedList(new ArrayList<>())).add(handler);
    }

    /**
     * Bir modül için paket gönderme işleyicisi kaydeder
     */
    public void registerSendHandler(PacketTypeCommon type, Consumer<PacketSendEvent> handler) {
        sendHandlers.computeIfAbsent(type, k -> Collections.synchronizedList(new ArrayList<>())).add(handler);
    }

    @Override
    public void onPacketReceive(@NotNull PacketReceiveEvent event) {
        // Bypass kontrolü
        if (event.getUser() != null && event.getUser().getUUID() != null) {
            Player player = plugin.getServer().getPlayer(event.getUser().getUUID());
            if (player != null && player.hasPermission("atomguard.bypass")) return;
        }

        // Heuristic Engine Entegrasyonu (Geleneksel legacy yapı korunuyor)
        handleLegacyIncoming(event);

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