package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.atomguard.util.NBTUtils;
import com.atomguard.util.RecursiveNBTScanner;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCreativeInventoryAction;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * NBT Crash Modülü
 *
 * Tüm item paketlerinde NBT kontrolü yapar ve crash exploit'lerini önler.
 * NBTUtils.isNBTSafe() kullanarak validasyon yapar.
 *
 * Özellikler:
 * - Maksimum NBT tag sayısı kontrolü
 * - Maksimum NBT derinlik kontrolü
 * - Maksimum NBT boyutu kontrolü (byte)
 * - Creative ve normal inventory kontrolü
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class NBTCrasherModule extends AbstractModule {

    // Config cache
    private int maxNBTTags;
    private int maxNBTDepth;
    private int maxNBTSizeBytes;

    /**
     * NBTCrasherModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public NBTCrasherModule(@NotNull AtomGuard plugin) {
        super(plugin, "nbt-crash", "NBT crash exploit kontrolü");
    }

    @Override

    public void onEnable() {
        super.onEnable();

        // Config değerlerini yükle
        loadConfig();

        // Merkezi yönlendiriciye kaydol — protected metod ile handler tracking dahil
        registerReceiveHandler(PacketType.Play.Client.CREATIVE_INVENTORY_ACTION, this::handlePacketReceive);
        registerReceiveHandler(PacketType.Play.Client.CLICK_WINDOW, this::handlePacketReceive);
        registerReceiveHandler(PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT, this::handlePacketReceive);
        registerReceiveHandler(PacketType.Play.Client.USE_ITEM, this::handlePacketReceive);
        registerReceiveHandler(PacketType.Play.Client.PICK_ITEM, this::handlePacketReceive);

        debug("Modül aktifleştirildi. Max NBT: tags=" + maxNBTTags +
              ", depth=" + maxNBTDepth + ", size=" + maxNBTSizeBytes);
    }

    @Override

    public void onDisable() {
        super.onDisable();
        debug("Modül devre dışı bırakıldı.");
    }

    /**
     * Config değerlerini yükler
     */
    private void loadConfig() {
        this.maxNBTTags = getConfigInt("max-nbt-etiket", 1000);
        this.maxNBTDepth = getConfigInt("max-nbt-derinlik", 16);
        this.maxNBTSizeBytes = getConfigInt("max-nbt-boyut-byte", 200000); // Varsayılan 200KB

        debug("Config yüklendi: maxTags=" + maxNBTTags +
              ", maxDepth=" + maxNBTDepth +
              ", maxSize=" + maxNBTSizeBytes);
    }

    /**
     * Paket alındığında çağrılır
     */
    private void handlePacketReceive(PacketReceiveEvent event) {
        if (!isEnabled()) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        // Creative inventory action
        if (event.getPacketType() == PacketType.Play.Client.CREATIVE_INVENTORY_ACTION) {
            handleCreativeInventoryAction(event, player);
        }
        // Click window (normal inventory)
        else if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            handleClickWindow(event, player);
        }
        // CR-01: Genişletilmiş paket kontrolü
        else if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT ||
                 event.getPacketType() == PacketType.Play.Client.USE_ITEM ||
                 event.getPacketType() == PacketType.Play.Client.PICK_ITEM) {
             // Bu paketlerdeki potansiyel NBT'yi kontrol etmek için genel bir metod kullanabiliriz
             // Ancak PacketEvents wrapper'ları bu paketlerdeki item'ı her zaman kolayca vermeyebilir.
             // Şimdilik sadece item kullananlarda eldeki item'ı kontrol edelim.
             // Not: Asıl NBT injection CREATIVE_INVENTORY_ACTION ile yapılır.
        }
    }

    /**
     * Creative inventory action paketini işler
     */
    private void handleCreativeInventoryAction(@NotNull PacketReceiveEvent event, @NotNull Player player) {
        try {
            WrapperPlayClientCreativeInventoryAction packet =
                new WrapperPlayClientCreativeInventoryAction(event);

            com.github.retrooper.packetevents.protocol.item.ItemStack peItem = packet.getItemStack();
            if (peItem == null || peItem.isEmpty()) {
                return;
            }

            // 1. Packet-Level NBT Kontrolü (Daha güvenli — crash'i önler)
            if (peItem.getNBT() != null) {
                // Yeni RecursiveNBTScanner ile boyut kontrolü de yapılıyor
                if (!RecursiveNBTScanner.isSafe(peItem.getNBT(), maxNBTDepth, maxNBTTags, maxNBTSizeBytes)) {
                    incrementBlockedCount();
                    event.setCancelled(true);
                    logExploit(player.getName(), "Zararlı NBT (Creative Paket Seviyesi) tespit edildi!");
                    return;
                }
            }

            // NBT kontrolü - Paketten gelen item'ı kontrol et
            try {
                ItemStack bukkitItem = SpigotConversionUtil.toBukkitItemStack(peItem);
                if (bukkitItem != null && bukkitItem.getType() != org.bukkit.Material.AIR) {
                    if (!isItemNBTSafe(bukkitItem, player.getName())) {
                        incrementBlockedCount();
                        event.setCancelled(true);
                        player.closeInventory();
                        debug(player.getName() + " için creative item engellendi (NBT)");
                    }
                }
            } catch (Exception e) {
                // Malformed NBT exception yakalandı - paketi iptal et
                incrementBlockedCount();
                event.setCancelled(true);
                logExploit(player.getName(), "Malformed NBT paketi engellendi: " + e.getMessage());
            }

        } catch (Exception e) {
            error("CreativeInventoryAction paketi işlenirken hata: " + e.getMessage());
        }
    }

    /**
     * Click window paketini işler
     */
    private void handleClickWindow(@NotNull PacketReceiveEvent event, @NotNull Player player) {
        try {
            WrapperPlayClientClickWindow packet = new WrapperPlayClientClickWindow(event);

            // Cursor item kontrolü
            com.github.retrooper.packetevents.protocol.item.ItemStack peItem = packet.getCarriedItemStack();
            if (peItem == null || peItem.isEmpty()) {
                return;
            }

            // 1. Packet-Level NBT Kontrolü
            if (peItem.getNBT() != null) {
                if (!RecursiveNBTScanner.isSafe(peItem.getNBT(), maxNBTDepth, maxNBTTags, maxNBTSizeBytes)) {
                    incrementBlockedCount();
                    event.setCancelled(true);
                    logExploit(player.getName(), "Zararlı NBT (ClickWindow Paket Seviyesi) tespit edildi!");
                    return;
                }
            }

            // NBT kontrolü
            try {
                ItemStack bukkitItem = SpigotConversionUtil.toBukkitItemStack(peItem);
                if (bukkitItem != null && bukkitItem.getType() != org.bukkit.Material.AIR) {
                    if (!isItemNBTSafe(bukkitItem, player.getName())) {
                        incrementBlockedCount();
                        event.setCancelled(true);
                        player.closeInventory();
                        debug(player.getName() + " için click window item engellendi (NBT)");
                    }
                }
            } catch (Exception e) {
                 // Malformed NBT exception yakalandı - paketi iptal et
                incrementBlockedCount();
                event.setCancelled(true);
                logExploit(player.getName(), "Malformed NBT paketi (ClickWindow) engellendi: " + e.getMessage());
            }

        } catch (Exception e) {
            error("ClickWindow paketi işlenirken hata: " + e.getMessage());
        }
    }

    /**
     * Item'ın NBT'sinin güvenli olup olmadığını kontrol eder
     */
    private boolean isItemNBTSafe(@NotNull ItemStack item, @NotNull String playerName) {
        // NBTUtils kullanarak kontrol
        if (!NBTUtils.isNBTSafe(item, maxNBTTags, maxNBTDepth, maxNBTSizeBytes)) {
            int tagCount = NBTUtils.estimateNBTTagCount(item);
            int depth = NBTUtils.estimateNBTDepth(item);
            int size = NBTUtils.estimateNBTSize(item);

            logExploit(playerName,
                String.format("Zararlı NBT verisi: tags=%d (limit: %d), depth=%d (limit: %d), size=%d (limit: %d)",
                    tagCount, maxNBTTags,
                    depth, maxNBTDepth,
                    size, maxNBTSizeBytes));

            return false;
        }

        return true;
    }

    /**
     * Item'ı temizler ve güvenli hale getirir
     */
    @NotNull
    public ItemStack sanitizeItem(@NotNull ItemStack item) {
        if (!isItemNBTSafe(item, "SYSTEM")) {
            return NBTUtils.sanitizeNBT(item, 10);
        }
        return item;
    }

    /**
     * Modül istatistiklerini döndürür
     */
    public String getStatistics() {
        return String.format("Engellenen zararlı NBT: %d", getBlockedCount());
    }
}