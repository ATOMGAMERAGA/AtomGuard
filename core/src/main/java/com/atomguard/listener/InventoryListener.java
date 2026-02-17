package com.atomguard.listener;

import com.atomguard.AtomGuard;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Envanter eventlerini dinleyen listener sınıfı
 * Envanter duplikasyon ve exploit'lerini önler
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class InventoryListener implements Listener {

    private final AtomGuard plugin;

    /**
     * InventoryListener constructor
     *
     * @param plugin Ana plugin instance
     */
    public InventoryListener(@NotNull AtomGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Envanter açıldığında
     *
     * @param event Envanter açılma eventi
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(@NotNull InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        // Bypass kontrolü
        if (player.hasPermission("atomguard.bypass")) {
            return;
        }

        // Debug log
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogManager().debug("Envanter açıldı: " + player.getName() + " - " + event.getInventory().getType());
        }

        // Modüller buraya entegre edilecek
        // Örnek: Envanter açıkken blok kırma kontrolü
    }

    /**
     * Envanter kapatıldığında
     *
     * @param event Envanter kapanma eventi
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        // Bypass kontrolü
        if (player.hasPermission("atomguard.bypass")) {
            return;
        }

        // Debug log
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogManager().debug("Envanter kapatıldı: " + player.getName());
        }

        // Modüller buraya entegre edilecek
    }

    /**
     * Envanter tıklandığında
     *
     * @param event Envanter tıklama eventi
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Bypass kontrolü
        if (player.hasPermission("atomguard.bypass")) {
            return;
        }

        // Debug log
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogManager().debug("Envanter tıklandı: " + player.getName() + " - Slot: " + event.getSlot());
        }

        // Modüller buraya entegre edilecek
        // Örnek: Geçersiz slot kontrolü, bundle duplikasyon, vb.
    }
}
