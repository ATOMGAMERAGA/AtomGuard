package com.atomguard.module;

import com.atomguard.AtomGuard;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Envanter Duplikasyon Modulu
 *
 * Envanter acikken blok kirma exploit'ini onler.
 * BlockBreakEvent + InventoryOpenEvent kombinasyonu ile duplikasyonu engeller.
 *
 * Ozellikler:
 * - Envanter acik tracking
 * - Blok kirma engelleme
 * - Timing kontrolu
 * - Duplikasyon exploit onleme
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class InventoryDuplicationModule extends AbstractModule implements Listener {

    // Envanteri acik olan oyuncular
    private final Map<UUID, Long> openInventories;

    /**
     * InventoryDuplicationModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public InventoryDuplicationModule(@NotNull AtomGuard plugin) {
        super(plugin, "envanter-duplikasyon", "Envanter duplikasyonu onleme");
        this.openInventories = new ConcurrentHashMap<>();
    }

    @Override

    public void onEnable() {
        super.onEnable();

        debug("Modul aktiflestirildi.");
    }

    @Override

    public void onDisable() {
        super.onDisable();

        // Map'i temizle
        openInventories.clear();

        // Event listener'i kaldir
        BlockBreakEvent.getHandlerList().unregister(this);
        InventoryOpenEvent.getHandlerList().unregister(this);
        InventoryCloseEvent.getHandlerList().unregister(this);
        PlayerQuitEvent.getHandlerList().unregister(this);

        debug("Modul devre disi birakildi.");
    }

    /**
     * Envanter acilma olayini dinler
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!isEnabled()) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        openInventories.put(uuid, System.currentTimeMillis());

        debug(player.getName() + " envanterini acti");
    }

    /**
     * Envanter kapatildiginda tracking'i temizle — false positive onleme
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        openInventories.remove(player.getUniqueId());
    }

    /**
     * Oyuncu cikisinda tracking'i temizle
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        openInventories.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Blok kirma olayini dinler
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Bypass kontrolu
        if (player.hasPermission("atomguard.bypass")) {
            return;
        }

        // Oyuncunun envanteri acik mi kontrol et
        Long openTime = openInventories.get(uuid);
        if (openTime != null) {
            long timeSinceOpen = System.currentTimeMillis() - openTime;

            // 100ms icinde blok kirma = supheli (50ms'den yukari, lag toleransli)
            if (timeSinceOpen < 100) {
                incrementBlockedCount();

                logExploit(player.getName(),
                    String.format("Envanter acikken blok kirma tespit edildi! Timing: %dms", timeSinceOpen));

                event.setCancelled(true);
                player.closeInventory();

                // Oyuncuya mesaj gonder
                player.sendMessage(plugin.getMessageManager()
                    .getMessage("envanter-acikken-blok-kirma"));

                debug(player.getName() + " icin blok kirma engellendi (envanter acik)");
            }
        }
    }

    /**
     * Oyuncunun envanterini kapatir
     */
    public void closeInventory(@NotNull UUID uuid) {
        openInventories.remove(uuid);
        debug("Envanter kapatildi: " + uuid);
    }

    /**
     * Tum envanter kayitlarini temizler
     */
    public void clearAll() {
        openInventories.clear();
        debug("Tum envanter kayitlari temizlendi");
    }

    /**
     * Oyuncunun envanterinin acik olup olmadigini kontrol eder
     */
    public boolean hasOpenInventory(@NotNull UUID uuid) {
        return openInventories.containsKey(uuid);
    }

    /**
     * Memory optimization - kullanilmayan kayitlari temizler
     */
    public void cleanup() {
        long currentTime = System.currentTimeMillis();
        long expiryTime = 10000; // 10 saniye

        openInventories.entrySet().removeIf(entry ->
            currentTime - entry.getValue() > expiryTime);
    }

    /**
     * Modul istatistiklerini dondurur
     */
    public String getStatistics() {
        return String.format("Acik envanter: %d, Engellenen duplikasyon: %d",
            openInventories.size(),
            getBlockedCount());
    }
}
