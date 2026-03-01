package com.atomguard.module;

import com.atomguard.AtomGuard;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Depolama Entity Kilidi Modülü (Donkey/Llama Sync)
 *
 * Mevcut MuleDuplicationModule farklı bir vektörü kapatır:
 * iki oyuncunun aynı anda bir entity'nin envanterini açması.
 *
 * Çözüm: Entity UUID bazlı kilit mekanizması.
 * - InventoryOpenEvent → entity envanter açılırsa kilitle
 * - InventoryCloseEvent → kilidi kaldır
 * - PlayerQuitEvent → oyuncunun tüm kilitlerini temizle
 * - 30 saniye timeout → otomatik kilit kaldırma
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class StorageEntityLockModule extends AbstractModule implements Listener {

    /** Kilitli entity UUID'leri */
    private final Set<UUID> accessedEntities = ConcurrentHashMap.newKeySet();

    /** Oyuncu → kilitlediği entity eşleşmesi (temizlik için) */
    private final Map<UUID, UUID> playerEntityMap = new ConcurrentHashMap<>();

    // Config cache
    private int timeoutSeconds;

    /**
     * StorageEntityLockModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public StorageEntityLockModule(@NotNull AtomGuard plugin) {
        super(plugin, "depolama-entity-kilit", "Depolama entity çift erişim kilidi");
    }

    @Override

    public void onEnable() {
        super.onEnable();
        loadConfig();

        debug("Depolama entity kilit modülü başlatıldı. Timeout: " + timeoutSeconds + " saniye");
    }

    @Override

    public void onDisable() {
        super.onDisable();
        HandlerList.unregisterAll(this);
        accessedEntities.clear();
        playerEntityMap.clear();
        debug("Depolama entity kilit modülü durduruldu.");
    }

    /**
     * Config değerlerini yükler
     */
    private void loadConfig() {
        this.timeoutSeconds = getConfigInt("zaman-asimi-saniye", 30);
    }

    /**
     * Envanter açılırken entity kilidi kontrol eder
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryOpen(@NotNull InventoryOpenEvent event) {
        if (!isEnabled()) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (player.hasPermission("atomguard.bypass")) return;

        // Sadece entity envanterlerini kontrol et
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Entity entity)) return;

        // Depolama entity'si mi kontrol et
        if (!isStorageEntity(event.getInventory().getType())) return;

        UUID entityUuid = entity.getUniqueId();
        UUID playerUuid = player.getUniqueId();

        // Zaten kilitli mi?
        if (accessedEntities.contains(entityUuid)) {
            // Başka biri bu entity'yi kullanıyor — erişimi engelle
            event.setCancelled(true);
            incrementBlockedCount();

            logExploit(player.getName(),
                    "Kilitli entity envanterine erişim girişimi engellendi: " + entityUuid);

            plugin.getMessageManager().sendPrefixedMessage(player, "engelleme.entity-kilit");
            return;
        }

        // Kilitle
        accessedEntities.add(entityUuid);
        playerEntityMap.put(playerUuid, entityUuid);

        debug(player.getName() + " entity kilitledi: " + entityUuid);

        // Timeout ile otomatik kilit kaldırma (takılma koruması)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (accessedEntities.remove(entityUuid)) {
                playerEntityMap.remove(playerUuid);
                debug("Entity kilidi timeout ile kaldırıldı: " + entityUuid);
            }
        }, timeoutSeconds * 20L); // saniye → tick
    }

    /**
     * Envanter kapatıldığında kilidi kaldırır
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (!isEnabled()) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        UUID playerUuid = player.getUniqueId();
        UUID entityUuid = playerEntityMap.remove(playerUuid);

        if (entityUuid != null) {
            accessedEntities.remove(entityUuid);
            debug(player.getName() + " entity kilidini kaldırdı: " + entityUuid);
        }
    }

    /**
     * Oyuncu çıkışında tüm kilitleri temizler
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        UUID entityUuid = playerEntityMap.remove(playerUuid);

        if (entityUuid != null) {
            accessedEntities.remove(entityUuid);
            debug(event.getPlayer().getName() + " çıkış yaptı, entity kilidi kaldırıldı: " + entityUuid);
        }
    }

    /**
     * Envanter türünün depolama entity'si olup olmadığını kontrol eder
     */
    private boolean isStorageEntity(@NotNull InventoryType type) {
        return type == InventoryType.CHEST // Donkey, Mule, Llama chest
                || type == InventoryType.HOPPER // Hopper minecart
                || type.name().contains("LLAMA"); // Llama envanteri
    }
}
