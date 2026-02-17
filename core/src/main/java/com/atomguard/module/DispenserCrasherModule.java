package com.atomguard.module;

import com.atomguard.AtomGuard;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Dispenser Crash Modülü
 *
 * Dispenser'dan çıkan item'ların geçersiz pozisyonlara yerleştirilmesini önler.
 * Crash exploit'lerini engeller.
 *
 * Özellikler:
 * - Geçersiz pozisyon kontrolü
 * - Chunk boundary kontrolü
 * - World koordinat validasyonu
 * - Dispenser event handling
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class DispenserCrasherModule extends AbstractModule implements Listener {

    // Maksimum dispense mesafesi
    private static final int MAX_DISPENSE_DISTANCE = 10;

    /**
     * DispenserCrasherModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public DispenserCrasherModule(@NotNull AtomGuard plugin) {
        super(plugin, "dispenser-crash", "Dispenser crash exploit kontrolü");
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
        BlockDispenseEvent.getHandlerList().unregister(this);

        debug("Modül devre dışı bırakıldı.");
    }

    /**
     * Dispenser olayını dinler
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent event) {
        if (!isEnabled()) {
            return;
        }

        Block block = event.getBlock();
        // ... (existing code for BlockDispenseEvent) ...
        Material dispensed = event.getItem().getType();

        // Sadece dispenser ve dropper'ları kontrol et
        if (block.getType() != Material.DISPENSER && block.getType() != Material.DROPPER) {
            return;
        }

        Location dispenserLoc = block.getLocation();
        Location targetLoc = dispenserLoc.clone().add(event.getVelocity());

        debug("Dispenser aktivasyonu: " + dispensed + " @ " + dispenserLoc);

        // Geçersiz pozisyon kontrolü
        if (!isLocationValid(targetLoc)) {
            incrementBlockedCount();

            logExploit("SYSTEM",
                String.format("Geçersiz dispenser hedef pozisyonu: %s -> %s",
                    locationToString(dispenserLoc),
                    locationToString(targetLoc)));

            event.setCancelled(true);
            debug("Dispenser olayı engellendi (geçersiz pozisyon)");
            return;
        }

        // Mesafe kontrolü
        double distance = dispenserLoc.distance(targetLoc);
        if (distance > MAX_DISPENSE_DISTANCE) {
            incrementBlockedCount();

            logExploit("SYSTEM",
                String.format("Çok uzak dispenser hedefi: %.2f blok (Max: %d)",
                    distance, MAX_DISPENSE_DISTANCE));

            event.setCancelled(true);
            debug("Dispenser olayı engellendi (mesafe aşımı)");
            return;
        }

        // Chunk boundary kontrolü
        if (!isSameChunk(dispenserLoc, targetLoc)) {
            debug("Dispenser hedefi farklı chunk'ta (normal)");
        }
    }

    // CR-10: Add listener for InventoryMoveItemEvent to check for NBT inside dispensers
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(org.bukkit.event.inventory.InventoryMoveItemEvent event) {
        if (!isEnabled()) return;
        
        org.bukkit.inventory.ItemStack item = event.getItem();
        // Check if destination is dispenser/dropper
        if (event.getDestination().getType() == org.bukkit.event.inventory.InventoryType.DISPENSER ||
            event.getDestination().getType() == org.bukkit.event.inventory.InventoryType.DROPPER) {
            
            // Check NBT using NBTUtils/NBTCrasherModule logic if possible
            if (com.atomguard.util.NBTUtils.estimateNBTSize(item) > 200000) { // Limit from config ideally
                event.setCancelled(true);
                incrementBlockedCount();
                logExploit("SYSTEM", "Dispenser'a aşırı büyük NBT item girişi engellendi");
            }
            
            // Check Shulker Boxes
            if (item.getType().name().contains("SHULKER_BOX")) {
                // Perform deep check or block moving shulkers into dispensers if desired
                // For now, size check above handles most NBT bombs
            }
        }
    }

    /**
     * Location'ın geçerli olup olmadığını kontrol eder
     */
    private boolean isLocationValid(@NotNull Location loc) {
        // World kontrolü
        if (loc.getWorld() == null) {
            return false;
        }

        // Koordinat kontrolü
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();

        // Y koordinatı kontrolü
        if (y < -64 || y > 320) { // Minecraft 1.18+ build limits
            return false;
        }

        // X ve Z koordinat kontrolü (world border)
        if (Math.abs(x) > 30000000 || Math.abs(z) > 30000000) {
            return false;
        }

        // NaN ve Infinity kontrolü
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
            return false;
        }

        if (Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z)) {
            return false;
        }

        return true;
    }

    /**
     * İki location'ın aynı chunk'ta olup olmadığını kontrol eder
     */
    private boolean isSameChunk(@NotNull Location loc1, @NotNull Location loc2) {
        if (!loc1.getWorld().equals(loc2.getWorld())) {
            return false;
        }

        int chunk1X = loc1.getBlockX() >> 4;
        int chunk1Z = loc1.getBlockZ() >> 4;
        int chunk2X = loc2.getBlockX() >> 4;
        int chunk2Z = loc2.getBlockZ() >> 4;

        return chunk1X == chunk2X && chunk1Z == chunk2Z;
    }

    /**
     * Location'ı string'e çevirir
     */
    @NotNull
    private String locationToString(@NotNull Location loc) {
        return String.format("%.2f, %.2f, %.2f", loc.getX(), loc.getY(), loc.getZ());
    }

    /**
     * Modül istatistiklerini döndürür
     */
    public String getStatistics() {
        return String.format("Engellenen dispenser olayı: %d", getBlockedCount());
    }
}
