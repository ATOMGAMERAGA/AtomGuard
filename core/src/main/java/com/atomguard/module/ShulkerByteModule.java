package com.atomguard.module;

import com.atomguard.AtomGuard;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Shulker Box Byte Counter Modülü (Chunk Ban Koruma)
 *
 * Aşırı büyük shulker kutuları ile chunk ban exploit'ini önler.
 * Shulker kutusunun serileştirilmiş byte boyutunu kontrol eder.
 *
 * Tetikleyiciler:
 * - BlockPlaceEvent: Shulker yerleştirmeden önce boyut kontrolü
 * - InventoryCloseEvent: Shulker envanteri kapatılırken boyut kontrolü (smuggling önleme)
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class ShulkerByteModule extends AbstractModule implements Listener {

    // Config cache
    private int maxBytes;
    private boolean deleteOversized;

    /**
     * ShulkerByteModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public ShulkerByteModule(@NotNull AtomGuard plugin) {
        super(plugin, "shulker-bayt", "Shulker kutusu byte boyutu kontrolü");
    }

    @Override

    public void onEnable() {
        super.onEnable();
        loadConfig();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        debug("Shulker byte modülü başlatıldı. Max boyut: " + maxBytes + " byte");
    }

    @Override

    public void onDisable() {
        super.onDisable();
        HandlerList.unregisterAll(this);
        debug("Shulker byte modülü durduruldu.");
    }

    /**
     * Config değerlerini yükler
     */
    private void loadConfig() {
        this.maxBytes = getConfigInt("max-bayt", 204800); // 200KB varsayılan
        this.deleteOversized = getConfigBoolean("fazla-boyutu-sil", true);
    }

    /**
     * Shulker kutusu yerleştirme kontrolü
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(@NotNull BlockPlaceEvent event) {
        if (!isEnabled()) return;

        Player player = event.getPlayer();
        if (player.hasPermission("atomguard.bypass")) return;

        ItemStack item = event.getItemInHand();
        if (!isShulkerBox(item.getType())) return;

        int byteSize = calculateByteSize(item);
        if (byteSize > maxBytes) {
            // Yerleştirmeyi iptal et
            event.setCancelled(true);
            incrementBlockedCount();

            logExploit(player.getName(),
                    String.format("Aşırı büyük shulker kutusu! Boyut: %d byte (limit: %d)", byteSize, maxBytes));

            // Tuzak item'ı sil
            if (deleteOversized) {
                event.getItemInHand().setAmount(0);
                warning(player.getName() + " oyuncusundan aşırı büyük shulker silindi (" + byteSize + " byte)");
            }

            // Oyuncuya uyarı gönder
            plugin.getMessageManager().sendPrefixedMessage(player, "engelleme.shulker-bayt");

            // Admin bildirimi
            plugin.getMessageManager().notifyWithPermission(
                    "atomguard.notify",
                    "bildirim.admin-uyari",
                    Map.of("oyuncu", player.getName(), "exploit", "shulker-bayt"));
        }
    }

    /**
     * Shulker envanteri kapatıldığında boyut kontrolü (smuggling önleme)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (!isEnabled()) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (player.hasPermission("atomguard.bypass")) return;

        // Sadece Shulker Box envanteri için kontrol et
        if (event.getInventory().getType() != InventoryType.SHULKER_BOX) return;

        // Envanter içeriğinin toplam boyutunu kontrol et
        int totalBytes = 0;
        for (ItemStack item : event.getInventory().getContents()) {
            if (item != null) {
                totalBytes += calculateByteSize(item);
            }
        }

        if (totalBytes > maxBytes) {
            incrementBlockedCount();
            logExploit(player.getName(),
                    String.format("Shulker envanter smuggling tespiti! Toplam: %d byte (limit: %d)",
                            totalBytes, maxBytes));

            // Envanteri temizle — shulker kutusundaki tüm item'ları sil
            if (deleteOversized) {
                event.getInventory().clear();
                warning(player.getName() + " shulker envanteri temizlendi (smuggling, " + totalBytes + " byte)");
            }

            plugin.getMessageManager().sendPrefixedMessage(player, "engelleme.shulker-bayt");
        }
    }

    /**
     * ItemStack'in serileştirilmiş byte boyutunu hesaplar
     * Paper API: ItemStack.serializeAsBytes()
     */
    private int calculateByteSize(@NotNull ItemStack item) {
        try {
            byte[] bytes = item.serializeAsBytes();
            return bytes.length;
        } catch (Exception e) {
            // Seri hale getirme başarısız olursa yaklaşık hesaplama
            debug("serializeAsBytes hatası: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Material'in Shulker Box türü olup olmadığını kontrol eder
     */
    private boolean isShulkerBox(@NotNull Material material) {
        // Paper Tag API ile tüm shulker renklerini kapsa
        try {
            return Tag.SHULKER_BOXES.isTagged(material);
        } catch (Exception e) {
            // Fallback — shulker adını kontrol et
            return material.name().contains("SHULKER_BOX");
        }
    }
}
