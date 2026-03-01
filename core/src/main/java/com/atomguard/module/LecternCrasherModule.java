package com.atomguard.module;

import com.atomguard.AtomGuard;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Kürsü Crash Modülü
 *
 * Lectern (kürsü) etkileşimlerini kontrol eder ve crash exploit'lerini önler.
 * Geçersiz slot etkileşimlerini ve zararlı item yerleştirmelerini engeller.
 *
 * Özellikler:
 * - Geçersiz slot kontrolü
 * - Lectern inventory kontrolü
 * - Slot boundary validation
 * - Crash exploit önleme
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class LecternCrasherModule extends AbstractModule implements Listener {

    // Lectern inventory boyutu
    private static final int LECTERN_SIZE = 1;

    /**
     * LecternCrasherModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public LecternCrasherModule(@NotNull AtomGuard plugin) {
        super(plugin, "kursu-crash", "Kürsü crash exploit kontrolü");
    }

    @Override

    public void onEnable() {
        super.onEnable();

        debug("Modül aktifleştirildi.");
    }

    @Override

    public void onDisable() {
        super.onDisable();

        // Event listener'ı kaldır
        InventoryClickEvent.getHandlerList().unregister(this);

        debug("Modül devre dışı bırakıldı.");
    }

    /**
     * Inventory click olayını dinler
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isEnabled()) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();

        // Lectern mi kontrol et
        if (!(holder instanceof Lectern)) {
            return;
        }

        int slot = event.getSlot();
        int rawSlot = event.getRawSlot();

        debug(player.getName() + " lectern'e tıkladı. Slot: " + slot + ", Raw: " + rawSlot);

        // Bypass kontrolü
        if (player.hasPermission("atomguard.bypass")) {
            return;
        }

        // Dış tıklama (-999) normal davranış — engelleme
        if (rawSlot == -999 || slot == -999) {
            return;
        }

        // Oyuncunun kendi envanterine tıklaması lectern sorununa yol açmaz
        if (event.getClickedInventory() != inventory) {
            return;
        }

        // Geçersiz slot kontrolü — sadece lectern envanterindeki slotlar
        if (slot < 0) {
            incrementBlockedCount();

            logExploit(player.getName(),
                String.format("Negatif lectern slot: %d", slot));

            event.setCancelled(true);
            player.closeInventory();

            debug(player.getName() + " için lectern etkileşimi engellendi (negatif slot)");
            return;
        }

        // Slot boundary kontrolü — lectern sadece 1 slot'a sahip
        if (slot >= LECTERN_SIZE) {
            incrementBlockedCount();

            logExploit(player.getName(),
                String.format("Geçersiz lectern slot: %d (Max: %d)", slot, LECTERN_SIZE));

            event.setCancelled(true);
            player.closeInventory();

            debug(player.getName() + " için lectern etkileşimi engellendi (slot aşımı)");
        }
    }

    /**
     * Lectern'in güvenli olup olmadığını kontrol eder
     */
    public boolean isLecternSafe(@NotNull Block block) {
        if (block.getType() != Material.LECTERN) {
            return true;
        }

        if (!(block.getState() instanceof Lectern lectern)) {
            return true;
        }

        // Lectern'deki kitabı kontrol et
        Inventory inventory = lectern.getInventory();
        if (inventory.isEmpty()) {
            return true;
        }

        // Inventory boyutu kontrolü
        return inventory.getSize() == LECTERN_SIZE;
    }

    /**
     * Modül istatistiklerini döndürür
     */
    public String getStatistics() {
        return String.format("Engellenen lectern etkileşimi: %d", getBlockedCount());
    }
}
