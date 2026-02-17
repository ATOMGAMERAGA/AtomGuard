package com.atomguard.module;

import com.atomguard.AtomGuard;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Hareket Güvenliği Modülü
 *
 * Geçersiz koordinatları ve aşırı hızlı hareketleri denetler.
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class MovementSecurityModule extends AbstractModule implements Listener {

    private double maxDistance;
    private boolean blockInvalidCoords;

    public MovementSecurityModule(@NotNull AtomGuard plugin) {
        super(plugin, "hareket-guvenligi", "Koordinat ve hız denetimi");
    }

    @Override

    public void onEnable() {
        super.onEnable();
        this.maxDistance = getConfigDouble("max-mesafe", 30000000.0);
        this.blockInvalidCoords = getConfigBoolean("gecersiz-koordinat-engelle", true);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isEnabled()) return;

        Location to = event.getTo();
        if (checkLocation(event.getPlayer(), to)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!isEnabled()) return;

        Location to = event.getTo();
        if (checkLocation(event.getPlayer(), to)) {
            event.setCancelled(true);
        }
    }

    private boolean checkLocation(Player player, Location loc) {
        if (loc == null) return false;

        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();

        // NaN veya Sonsuz kontrolü
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z) ||
            Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z)) {
            
            if (blockInvalidCoords) {
                incrementBlockedCount();
                logExploit(player.getName(), "Geçersiz koordinat (NaN/Inf) tespit edildi: " + x + ", " + y + ", " + z);
                return true;
            }
        }

        // Dünya sınırı kontrolü (30M varsayılan)
        if (Math.abs(x) > maxDistance || Math.abs(z) > maxDistance || Math.abs(y) > 4096) {
            incrementBlockedCount();
            logExploit(player.getName(), "Dünya sınırı dışı koordinat: " + x + ", " + y + ", " + z);
            return true;
        }

        return false;
    }
}
