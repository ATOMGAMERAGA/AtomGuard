package com.atomguard.module;

import com.atomguard.AtomGuard;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Koordinat Normalizasyon Modülü
 *
 * Oyuncu çıkış yaparken koordinatlarını normalize eder.
 * Geçersiz koordinat exploit'lerini önler.
 *
 * Özellikler:
 * - Oyuncu quit kontrolü
 * - Koordinat validasyonu
 * - NaN ve Infinity kontrolü
 * - World boundary kontrolü
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class NormalizeCoordinatesModule extends AbstractModule implements Listener {

    // Minecraft world boundaries
    private static final double MAX_COORDINATE = 29999999.0;
    private static final double MIN_Y = -64.0;
    private static final double MAX_Y = 320.0;

    /**
     * NormalizeCoordinatesModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public NormalizeCoordinatesModule(@NotNull AtomGuard plugin) {
        super(plugin, "koordinat-normallestirme", "Koordinat normalizasyonu");
    }

    @Override

    public void onEnable() {
        super.onEnable();

        // Event listener kaydet
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        debug("Modül aktifleştirildi.");
    }

    @Override

    public void onDisable() {
        super.onDisable();

        // Event listener'ı kaldır
        PlayerQuitEvent.getHandlerList().unregister(this);

        debug("Modül devre dışı bırakıldı.");
    }

    /**
     * Oyuncu çıkış olayını dinler
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        Location loc = player.getLocation();

        debug(player.getName() + " çıkış yapıyor. Konum: " + locationToString(loc));

        // Koordinatları kontrol et
        if (!isLocationValid(loc)) {
            // Geçersiz koordinatlar, normalize et
            incrementBlockedCount();

            Location normalized = normalizeLocation(loc);

            logExploit(player.getName(),
                String.format("Geçersiz koordinatlar tespit edildi! Eski: %s, Yeni: %s",
                    locationToString(loc),
                    locationToString(normalized)));

            // Oyuncuyu normalize edilmiş konuma ışınla
            player.teleport(normalized);

            debug(player.getName() + " koordinatları normalize edildi");
        }
    }

    /**
     * Location'ın geçerli olup olmadığını kontrol eder
     */
    private boolean isLocationValid(@NotNull Location loc) {
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();

        // NaN kontrolü
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
            return false;
        }

        // Infinity kontrolü
        if (Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z)) {
            return false;
        }

        // World boundary kontrolü
        if (Math.abs(x) > MAX_COORDINATE || Math.abs(z) > MAX_COORDINATE) {
            return false;
        }

        // Y koordinatı kontrolü
        if (y < MIN_Y || y > MAX_Y) {
            return false;
        }

        return true;
    }

    /**
     * Location'ı normalize eder
     */
    @NotNull
    private Location normalizeLocation(@NotNull Location loc) {
        double x = normalizeCoordinate(loc.getX(), MAX_COORDINATE);
        double y = normalizeY(loc.getY());
        double z = normalizeCoordinate(loc.getZ(), MAX_COORDINATE);

        Location normalized = loc.clone();
        normalized.setX(x);
        normalized.setY(y);
        normalized.setZ(z);

        return normalized;
    }

    /**
     * Koordinatı normalize eder
     */
    private double normalizeCoordinate(double coord, double max) {
        // NaN veya Infinity ise 0'a ayarla
        if (Double.isNaN(coord) || Double.isInfinite(coord)) {
            return 0.0;
        }

        // Limit içine al
        if (coord > max) {
            return max;
        }
        if (coord < -max) {
            return -max;
        }

        return coord;
    }

    /**
     * Y koordinatını normalize eder
     */
    private double normalizeY(double y) {
        // NaN veya Infinity ise spawn Y'sine ayarla
        if (Double.isNaN(y) || Double.isInfinite(y)) {
            return 64.0; // Ortalama spawn height
        }

        // Limit içine al
        if (y > MAX_Y) {
            return MAX_Y;
        }
        if (y < MIN_Y) {
            return MIN_Y;
        }

        return y;
    }

    /**
     * Location'ı string'e çevirir
     */
    @NotNull
    private String locationToString(@NotNull Location loc) {
        return String.format("%.2f, %.2f, %.2f", loc.getX(), loc.getY(), loc.getZ());
    }

    /**
     * Oyuncunun koordinatlarını manuel olarak normalize eder
     */
    public void normalizePlayer(@NotNull Player player) {
        Location loc = player.getLocation();
        if (!isLocationValid(loc)) {
            Location normalized = normalizeLocation(loc);
            player.teleport(normalized);
            info(player.getName() + " koordinatları normalize edildi");
        }
    }

    /**
     * Modül istatistiklerini döndürür
     */
    public String getStatistics() {
        return String.format("Normalize edilen oyuncu: %d", getBlockedCount());
    }
}
