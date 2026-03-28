package com.atomguard.limbo;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

/**
 * Limbo sunucusunda oyuncuların etkileşimini kısıtlar.
 *
 * <p>Doğrulama süresince oyuncular:
 * <ul>
 *   <li>Chat gönderemez
 *   <li>Komut çalıştıramaz
 *   <li>Eşya toplayamaz/bırakamaz
 *   <li>Blok/eşya ile etkileşemez
 * </ul>
 */
@SuppressWarnings("deprecation")
public class SecurityListener implements Listener {

    private final AtomGuardLimbo plugin;

    public SecurityListener(AtomGuardLimbo plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onChat(PlayerChatEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        // Yalnızca /login ve /register geçebilir (AuthMe gibi auth plugin varsa)
        String cmd = event.getMessage().toLowerCase();
        if (!cmd.startsWith("/login") && !cmd.startsWith("/register")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPickup(PlayerPickupItemEvent event) {
        event.setCancelled(true);
    }
}
