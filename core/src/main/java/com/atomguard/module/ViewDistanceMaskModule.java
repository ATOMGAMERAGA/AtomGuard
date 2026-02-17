package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * View Distance Paket Maskeleme Modülü (Anti-NoCom)
 *
 * NoCom Exploit: Saldırgan, sunucunun gönderdiği blok/ses paketlerinden
 * oyuncu koordinatlarını ve üs konumlarını keşfeder.
 *
 * Çözüm: Giden paketleri filtrele:
 * - BlockChange paketleri
 * - Paketin hedef koordinatları ile alıcının konumu karşılaştırılır
 * - distanceSquared > (viewDistance * 16)² ise paketi düşür
 *
 * Optimizasyon: Math.sqrt KULLANILMAZ — squared distance karşılaştırması
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class ViewDistanceMaskModule extends AbstractModule {

    /**
     * ViewDistanceMaskModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public ViewDistanceMaskModule(@NotNull AtomGuard plugin) {
        super(plugin, "gorunum-mesafesi-maskeleme", "View distance paket maskeleme (Anti-NoCom)");
    }

    @Override
    public void onEnable() {
        super.onEnable();

        registerSendHandler(PacketType.Play.Server.BLOCK_CHANGE, this::handleBlockChange);
        
        debug("View distance maskeleme modülü başlatıldı.");
    }

    @Override
    public void onDisable() {
        super.onDisable();
        debug("View distance maskeleme modülü durduruldu.");
    }

    /**
     * BlockChange paketini filtreler
     */
    private void handleBlockChange(@NotNull PacketSendEvent event) {
        if (!isEnabled()) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        try {
            WrapperPlayServerBlockChange packet = new WrapperPlayServerBlockChange(event);
            Vector3i blockPos = packet.getBlockPosition();

            if (isOutOfViewDistance(player, blockPos.getX(), blockPos.getZ())) {
                event.setCancelled(true);
                incrementBlockedCount();
            }
        } catch (Exception e) {
            // Parse hatası — pakete dokunma
        }
    }

    /**
     * Verilen koordinatların oyuncunun view distance'ı dışında olup olmadığını kontrol eder.
     * Optimizasyon: Math.sqrt kullanılmaz — squared distance karşılaştırması yapılır.
     *
     * @param player Hedef oyuncu
     * @param x      Paket X koordinatı (blok)
     * @param z      Paket Z koordinatı (blok)
     * @return true ise view distance dışında
     */
    private boolean isOutOfViewDistance(@NotNull Player player, int x, int z) {
        Location playerLoc = player.getLocation();

        // Oyuncunun view distance'ını al (chunk cinsinden) ve blok cinsine çevir
        int viewDistanceBlocks = player.getViewDistance() * 16;
        long maxDistanceSquared = (long) viewDistanceBlocks * viewDistanceBlocks;

        // Squared distance hesapla (XZ düzleminde — Y ihmal edilir)
        double dx = x - playerLoc.getX();
        double dz = z - playerLoc.getZ();
        double distanceSquared = dx * dx + dz * dz;

        return distanceSquared > maxDistanceSquared;
    }
}
