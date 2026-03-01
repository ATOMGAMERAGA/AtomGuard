package com.atomguard.module;

import com.atomguard.AtomGuard;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gelişmiş Bundle Kilitleme Modülü
 *
 * Mevcut BundleDuplicationModule'ün üzerine ek koruma sağlar.
 * Slot kilitleme mekanizması ile race condition'ı tamamen kapatır.
 *
 * Exploit: Oyuncu, Bundle'a item koyma + yere atma işlemini
 * race condition ile sömürür.
 *
 * Çözüm:
 * 1. Bundle tespit edildiğinde slot'u kilitle
 * 2. Kilitli slot'a tıklama → iptal et
 * 3. Kilitli slot'tan drop → iptal et
 * 4. 2 tick sonra kilidi kaldır
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class BundleLockModule extends AbstractModule implements Listener {

    /** Oyuncu başına kilitli slot'lar — UUID → Set<slotIndex> */
    private final Map<UUID, Set<Integer>> lockedSlots = new ConcurrentHashMap<>();

    // Config cache
    private int lockDurationTicks;

    /**
     * BundleLockModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public BundleLockModule(@NotNull AtomGuard plugin) {
        super(plugin, "bundle-kilit", "Bundle slot kilitleme koruması");
    }

    @Override

    public void onEnable() {
        super.onEnable();
        loadConfig();

        debug("Bundle kilit modülü başlatıldı. Kilit süresi: " + lockDurationTicks + " tick");
    }

    @Override

    public void onDisable() {
        super.onDisable();
        HandlerList.unregisterAll(this);
        lockedSlots.clear();
        debug("Bundle kilit modülü durduruldu.");
    }

    /**
     * Config değerlerini yükler
     */
    private void loadConfig() {
        this.lockDurationTicks = getConfigInt("kilit-suresi-tick", 2);
    }

    /**
     * Envanter tıklamasında Bundle slot kilidi kontrol eder
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!isEnabled()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (player.hasPermission("atomguard.bypass")) return;

        UUID uuid = player.getUniqueId();
        int slot = event.getSlot();

        // Kilitli slot kontrolü — kilitliyse tıklamayı iptal et
        Set<Integer> playerLocks = lockedSlots.get(uuid);
        if (playerLocks != null && playerLocks.contains(slot)) {
            event.setCancelled(true);
            incrementBlockedCount();
            debug(player.getName() + " kilitli slot'a tıkladı: " + slot);
            return;
        }

        // Bundle etkileşimi tespit et
        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        boolean bundleInvolved = isBundle(clickedItem) || isBundle(cursorItem);
        if (bundleInvolved) {
            // Slot'u kilitle
            lockSlot(uuid, slot);
            debug(player.getName() + " bundle slot kilitledi: " + slot);
        }
    }

    /**
     * Item drop'ta kilitli slot kontrolü
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerDropItem(@NotNull PlayerDropItemEvent event) {
        if (!isEnabled()) return;

        Player player = event.getPlayer();
        if (player.hasPermission("atomguard.bypass")) return;

        UUID uuid = player.getUniqueId();

        // Herhangi bir kilitli slot varsa drop'u engelle
        Set<Integer> playerLocks = lockedSlots.get(uuid);
        if (playerLocks != null && !playerLocks.isEmpty()) {
            // Drop edilen item bundle ise engelle
            if (isBundle(event.getItemDrop().getItemStack())) {
                event.setCancelled(true);
                incrementBlockedCount();
                logExploit(player.getName(), "Kilitli slot'tan bundle drop girişimi engellendi");
            }
        }
    }

    /**
     * Oyuncu çıkışında kilitleri temizle
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        lockedSlots.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Slot'u belirli süre boyunca kilitler
     */
    private void lockSlot(@NotNull UUID uuid, int slot) {
        Set<Integer> playerLocks = lockedSlots.computeIfAbsent(uuid,
                k -> ConcurrentHashMap.newKeySet());
        playerLocks.add(slot);

        // Kilit süresinden sonra otomatik kaldır
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Set<Integer> locks = lockedSlots.get(uuid);
            if (locks != null) {
                locks.remove(slot);
                // Boş set'i temizle
                if (locks.isEmpty()) {
                    lockedSlots.remove(uuid);
                }
            }
        }, lockDurationTicks);
    }

    /**
     * Item'ın Bundle olup olmadığını kontrol eder
     */
    private boolean isBundle(ItemStack item) {
        return item != null && item.getType() == Material.BUNDLE;
    }
}
