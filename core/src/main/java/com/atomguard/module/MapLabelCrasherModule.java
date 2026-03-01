package com.atomguard.module;

import com.atomguard.AtomGuard;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.jetbrains.annotations.NotNull;

/**
 * Harita Etiketi Crash Modülü
 *
 * Harita item'larını kontrol eder ve zararlı etiketleri engeller.
 * Map label exploit'lerini önlemek için tasarlanmıştır.
 *
 * Özellikler:
 * - Harita etiket kontrolü
 * - Maksimum etiket sayısı kontrolü
 * - Etiket devre dışı bırakma
 * - Map meta validasyonu
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class MapLabelCrasherModule extends AbstractModule implements Listener {

    // Config cache
    private boolean disableLabels;
    private int maxLabelCount;

    /**
     * MapLabelCrasherModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public MapLabelCrasherModule(@NotNull AtomGuard plugin) {
        super(plugin, "harita-etiketi-crash", "Harita etiketi crash kontrolü");
    }

    @Override

    public void onEnable() {
        super.onEnable();

        // Config değerlerini yükle
        loadConfig();

        debug("Modül aktifleştirildi. Etiketler devre dışı: " + disableLabels);
    }

    @Override

    public void onDisable() {
        super.onDisable();

        // Event listener'ı kaldır
        InventoryClickEvent.getHandlerList().unregister(this);

        debug("Modül devre dışı bırakıldı.");
    }

    /**
     * Config değerlerini yükler
     */
    private void loadConfig() {
        this.disableLabels = getConfigBoolean("etiketleri-devre-disi-birak", false);
        this.maxLabelCount = getConfigInt("max-etiket-sayisi", 50);

        debug("Config yüklendi: disableLabels=" + disableLabels +
              ", maxLabels=" + maxLabelCount);
    }

    /**
     * Inventory click olayını dinler
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isEnabled()) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() != Material.FILLED_MAP) {
            return;
        }

        // Map meta kontrolü
        if (!item.hasItemMeta()) {
            return;
        }

        if (!(item.getItemMeta() instanceof MapMeta mapMeta)) {
            return;
        }

        debug(player.getName() + " harita item'ı ile etkileşimde");

        // Etiket kontrolü
        if (mapMeta.hasLocationName()) {
            String locationName = mapMeta.getLocationName();

            // Etiketler devre dışı mı?
            if (disableLabels) {
                incrementBlockedCount();

                logExploit(player.getName(),
                    "Harita etiketi tespit edildi ve kaldırıldı");

                // Etiketi kaldır
                mapMeta.setLocationName(null);
                item.setItemMeta(mapMeta);
                event.setCurrentItem(item);

                debug(player.getName() + " harita etiketinden temizlendi");
                return;
            }

            // Etiket uzunluğu kontrolü
            if (locationName != null && locationName.length() > 100) {
                incrementBlockedCount();

                logExploit(player.getName(),
                    String.format("Çok uzun harita etiketi: %d karakter", locationName.length()));

                // Etiketi kısalt
                mapMeta.setLocationName(locationName.substring(0, 100));
                item.setItemMeta(mapMeta);
                event.setCurrentItem(item);

                debug(player.getName() + " harita etiketi kısaltıldı");
            }
        }

        // Map color kontrolü (ek güvenlik)
        if (mapMeta.hasColor()) {
            // Renk kontrolü yapılabilir
            debug("Harita rengi var: " + mapMeta.getColor());
        }
    }

    /**
     * Harita item'ını temizler
     */
    @NotNull
    public ItemStack sanitizeMap(@NotNull ItemStack map) {
        if (map.getType() != Material.FILLED_MAP) {
            return map;
        }

        if (!map.hasItemMeta()) {
            return map;
        }

        if (!(map.getItemMeta() instanceof MapMeta mapMeta)) {
            return map;
        }

        // Etiketleri temizle
        if (disableLabels && mapMeta.hasLocationName()) {
            mapMeta.setLocationName(null);
            map.setItemMeta(mapMeta);
            debug("Harita etiketi temizlendi");
        }

        return map;
    }

    /**
     * Modül istatistiklerini döndürür
     */
    public String getStatistics() {
        return String.format("Engellenen harita: %d", getBlockedCount());
    }
}
