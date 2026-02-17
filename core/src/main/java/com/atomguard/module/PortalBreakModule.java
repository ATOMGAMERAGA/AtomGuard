package com.atomguard.module;

import com.atomguard.AtomGuard;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Portal Kırma Modülü
 *
 * Mantar ve su kovası ile portal kırma exploit'ini önler.
 * Portal blok koruma sistemi.
 *
 * Özellikler:
 * - Mantar ile portal kırma engelleme
 * - Su kovası ile portal kırma engelleme
 * - Portal blok kontrolü
 * - Exploit önleme
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class PortalBreakModule extends AbstractModule implements Listener {

    // Config cache
    private boolean blockMushroom;
    private boolean blockWaterBucket;

    /**
     * PortalBreakModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public PortalBreakModule(@NotNull AtomGuard plugin) {
        super(plugin, "portal-kirma", "Portal kırma exploit önleme");
    }

    @Override

    public void onEnable() {
        super.onEnable();

        // Config değerlerini yükle
        loadConfig();

        // Event listener kaydet
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        debug("Modül aktifleştirildi. Mantar: " + blockMushroom + ", Su kovası: " + blockWaterBucket);
    }

    @Override

    public void onDisable() {
        super.onDisable();

        // Event listener'ı kaldır
        BlockBreakEvent.getHandlerList().unregister(this);

        debug("Modül devre dışı bırakıldı.");
    }

    /**
     * Config değerlerini yükler
     */
    private void loadConfig() {
        this.blockMushroom = getConfigBoolean("mantar-engelle", true);
        this.blockWaterBucket = getConfigBoolean("su-kovasi-engelle", true);

        debug("Config yüklendi: mushroom=" + blockMushroom + ", waterBucket=" + blockWaterBucket);
    }

    /**
     * Blok kırma olayını dinler
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Portal bloğu mu kontrol et
        if (!isPortalBlock(block)) {
            return;
        }

        debug(player.getName() + " portal bloğu kırıyor: " + block.getType());

        ItemStack item = player.getInventory().getItemInMainHand();
        Material itemType = item.getType();

        // Mantar kontrolü
        if (blockMushroom && isMushroom(itemType)) {
            incrementBlockedCount();

            logExploit(player.getName(),
                String.format("Mantar ile portal kırma tespit edildi! Blok: %s, Item: %s",
                    block.getType(), itemType));

            event.setCancelled(true);

            // Oyuncuya mesaj gönder
            player.sendMessage(plugin.getMessageManager()
                .getMessage("portal-kirma-engellendi"));

            debug(player.getName() + " için portal kırma engellendi (mantar)");
            return;
        }

        // Su kovası kontrolü
        if (blockWaterBucket && itemType == Material.WATER_BUCKET) {
            incrementBlockedCount();

            logExploit(player.getName(),
                String.format("Su kovası ile portal kırma tespit edildi! Blok: %s",
                    block.getType()));

            event.setCancelled(true);

            // Oyuncuya mesaj gönder
            player.sendMessage(plugin.getMessageManager()
                .getMessage("portal-kirma-engellendi"));

            debug(player.getName() + " için portal kırma engellendi (su kovası)");
        }
    }

    /**
     * Bloğun portal bloğu olup olmadığını kontrol eder
     */
    private boolean isPortalBlock(@NotNull Block block) {
        Material type = block.getType();
        return type == Material.NETHER_PORTAL ||
               type == Material.END_PORTAL ||
               type == Material.END_PORTAL_FRAME;
    }

    /**
     * Item'ın mantar olup olmadığını kontrol eder
     */
    private boolean isMushroom(@NotNull Material material) {
        return material == Material.RED_MUSHROOM ||
               material == Material.BROWN_MUSHROOM ||
               material == Material.RED_MUSHROOM_BLOCK ||
               material == Material.BROWN_MUSHROOM_BLOCK ||
               material == Material.MUSHROOM_STEM;
    }

    /**
     * Modül istatistiklerini döndürür
     */
    public String getStatistics() {
        return String.format("Engellenen portal kırma: %d", getBlockedCount());
    }
}
