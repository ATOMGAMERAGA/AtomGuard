package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Entity Etkileşim Crash Koruması
 * Geçersiz entity ID ve aşırı hızlı etkileşimleri önler.
 */
public class EntityInteractCrashModule extends AbstractModule {

    private final Map<UUID, AtomicInteger> interactCounts = new ConcurrentHashMap<>();
    private double maxDistance;
    private int maxInteractPerSec;
    private PacketListenerAbstract listener;

    public EntityInteractCrashModule(@NotNull AtomGuard plugin) {
        super(plugin, "entity-etkilesim-crash", "Entity etkileşim koruması");
    }

    @Override

    public void onEnable() {
        super.onEnable();
        this.maxDistance = getConfigDouble("max-etkilesim-mesafesi", 6.0);
        this.maxInteractPerSec = getConfigInt("saniyede-max-etkilesim", 20);

        listener = new PacketListenerAbstract(PacketListenerPriority.NORMAL) {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
                    handleInteract(event);
                }
            }
        };
        com.github.retrooper.packetevents.PacketEvents.getAPI().getEventManager().registerListener(listener);
        
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, interactCounts::clear, 20L, 20L);
    }

    private void handleInteract(PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        
        UUID uuid = player.getUniqueId();
        int count = interactCounts.computeIfAbsent(uuid, k -> new AtomicInteger(0)).incrementAndGet();
        
        if (count > maxInteractPerSec) {
            event.setCancelled(true);
            incrementBlockedCount();
            return;
        }

        WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
        // Entity ID'nin geçerli olup olmadığı sunucu tarafından kontrol edilir ancak 
        // aşırı büyük ID'ler ArrayIndexOutOfBounds tetikleyebilir.
        if (packet.getEntityId() < 0 || packet.getEntityId() > 2000000) {
            event.setCancelled(true);
            incrementBlockedCount();
            logExploit(player.getName(), "Geçersiz Entity ID Etkileşimi: " + packet.getEntityId());
        }
    }

    @Override

    public void onDisable() {
        super.onDisable();
        if (listener != null) com.github.retrooper.packetevents.PacketEvents.getAPI().getEventManager().unregisterListener(listener);
    }
}
