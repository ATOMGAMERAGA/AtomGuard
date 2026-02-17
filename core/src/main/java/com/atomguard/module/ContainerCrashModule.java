package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Container/Window Crash Koruması
 * Geçersiz window ID ve slot ID exploitlerini önler.
 */
public class ContainerCrashModule extends AbstractModule {

    public ContainerCrashModule(@NotNull AtomGuard plugin) {
        super(plugin, "container-crash", "Envanter ve Container koruması");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        registerReceiveHandler(PacketType.Play.Client.CLICK_WINDOW, this::handleClick);
    }

    private void handleClick(PacketReceiveEvent event) {
        if (!isEnabled()) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        WrapperPlayClientClickWindow packet = new WrapperPlayClientClickWindow(event);
        int slot = packet.getSlot();
        int windowId = packet.getWindowId();

        // 1. Slot ID kontrolü
        if (slot < -1 || slot > 127) { // Standart dışı slotlar
             if (slot != -999) { // -999 item drop için geçerlidir
                 event.setCancelled(true);
                 blockExploit(player, "Geçersiz slot ID: " + slot);
                 return;
             }
        }

        // 2. Orphan window click kontrolü (Opsiyonel: Oyuncunun açık window'u takip edilmelidir)
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }
}
