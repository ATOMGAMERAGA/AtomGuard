package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.atomguard.util.PacketUtils;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Geçersiz Slot Modülü
 *
 * Geçersiz slot numaralarını kontrol eder ve crash exploit'lerini önler.
 * WrapperPlayClientClickWindow paketi ile slot validasyonu yapar.
 *
 * Özellikler:
 * - Negatif slot kontrolü
 * - Slot boundary validation
 * - Container boyutu kontrolü
 * - PacketUtils.isSlotValid() kullanımı
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class InvalidSlotModule extends AbstractModule {

    // Maksimum slot sayısı (Double Chest + Player Inventory)
    private static final int MAX_SLOT = 90;
    private static final int MIN_SLOT = -999; // -999 outside inventory için kullanılır

    /**
     * InvalidSlotModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public InvalidSlotModule(@NotNull AtomGuard plugin) {
        super(plugin, "gecersiz-slot", "Geçersiz slot numarası kontrolü");
    }

    @Override

    public void onEnable() {
        super.onEnable();

        // Merkezi listener kullan — sadece CLICK_WINDOW paketleri
        registerReceiveHandler(PacketType.Play.Client.CLICK_WINDOW, this::handlePacketReceive);

        debug("Modül aktifleştirildi.");
    }

    @Override

    public void onDisable() {
        super.onDisable();

        debug("Modül devre dışı bırakıldı.");
    }

    /**
     * Paket alındığında çağrılır
     */
    private void handlePacketReceive(PacketReceiveEvent event) {
        if (!isEnabled()) {
            return;
        }

        // ClickWindow paketini kontrol et
        if (event.getPacketType() != PacketType.Play.Client.CLICK_WINDOW) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        try {
            WrapperPlayClientClickWindow packet = new WrapperPlayClientClickWindow(event);
            int slot = packet.getSlot();
            int windowId = packet.getWindowId();

            debug(player.getName() + " ClickWindow: slot=" + slot + ", window=" + windowId);

            // Negatif slot kontrolü (özel durumlar hariç)
            if (slot < MIN_SLOT) {
                incrementBlockedCount();

                logExploit(player.getName(),
                    String.format("Geçersiz negatif slot: %d (Min: %d)", slot, MIN_SLOT));

                event.setCancelled(true);
                player.closeInventory();

                debug(player.getName() + " için paket engellendi (negatif slot)");
                return;
            }

            // Slot boundary kontrolü
            if (slot > MAX_SLOT && slot != -999) {
                incrementBlockedCount();

                logExploit(player.getName(),
                    String.format("Slot limiti aşıldı: %d (Max: %d)", slot, MAX_SLOT));

                event.setCancelled(true);
                player.closeInventory();

                debug(player.getName() + " için paket engellendi (slot aşımı)");
                return;
            }

            // PacketUtils ile ek validasyon
            if (slot >= 0 && !PacketUtils.isSlotValid(slot, MAX_SLOT)) {
                incrementBlockedCount();

                logExploit(player.getName(),
                    String.format("PacketUtils slot validasyonu başarısız: %d", slot));

                event.setCancelled(true);
                player.closeInventory();

                debug(player.getName() + " için paket engellendi (validasyon hatası)");
            }

        } catch (Exception e) {
            error("ClickWindow paketi işlenirken hata: " + e.getMessage());
        }
    }

    /**
     * Slot numarasının güvenli olup olmadığını kontrol eder
     */
    public boolean isSlotSafe(int slot) {
        return slot >= MIN_SLOT && (slot <= MAX_SLOT || slot == -999);
    }

    /**
     * Slot numarasını normalize eder
     */
    public int normalizeSlot(int slot) {
        if (slot < MIN_SLOT) {
            return MIN_SLOT;
        }
        if (slot > MAX_SLOT && slot != -999) {
            return MAX_SLOT;
        }
        return slot;
    }

    /**
     * Modül istatistiklerini döndürür
     */
    public String getStatistics() {
        return String.format("Engellenen geçersiz slot: %d", getBlockedCount());
    }
}
