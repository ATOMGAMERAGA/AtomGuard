package com.atomguard.module;

import com.atomguard.AtomGuard;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Katır Duplikasyon Modülü
 *
 * Yüklenmemiş entity envanteri açma exploit'ini önler.
 * EntityInteractEvent + chunk loaded kontrolü ile duplikasyonu engeller.
 *
 * Özellikler:
 * - Chunk yüklenme kontrolü
 * - Entity validasyon
 * - Mule/Donkey/Horse inventory kontrolü
 * - Duplikasyon exploit önleme
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class MuleDuplicationModule extends AbstractModule implements Listener {

    /**
     * MuleDuplicationModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public MuleDuplicationModule(@NotNull AtomGuard plugin) {
        super(plugin, "katir-duplikasyon", "Katır duplikasyonu önleme");
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
        PlayerInteractEntityEvent.getHandlerList().unregister(this);

        debug("Modül devre dışı bırakıldı.");
    }

    /**
     * Entity etkileşimi olayını dinler
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        // Sadece donkey, mule ve llama için kontrol et
        EntityType type = entity.getType();
        if (type != EntityType.MULE &&
            type != EntityType.DONKEY &&
            type != EntityType.LLAMA &&
            type != EntityType.HORSE) {
            return;
        }

        debug(player.getName() + " " + type + " ile etkileşimde");

        // Entity'nin chunk'ının yüklenip yüklenmediğini kontrol et
        if (!entity.getChunk().isLoaded()) {
            incrementBlockedCount();

            logExploit(player.getName(),
                String.format("Yüklenmemiş chunk'taki %s ile etkileşim! Chunk: [%d,%d]",
                    type,
                    entity.getChunk().getX(),
                    entity.getChunk().getZ()));

            event.setCancelled(true);

            // Oyuncuya mesaj gönder
            player.sendMessage(plugin.getMessageManager()
                .getMessage("entity-yuklenmemis"));

            debug(player.getName() + " için etkileşim engellendi (chunk yüklenmemiş)");
            return;
        }

        // Entity'nin valid olup olmadığını kontrol et
        if (!entity.isValid()) {
            incrementBlockedCount();

            logExploit(player.getName(),
                String.format("Geçersiz %s ile etkileşim!", type));

            event.setCancelled(true);
            debug(player.getName() + " için etkileşim engellendi (geçersiz entity)");
            return;
        }

        // Entity'nin dead olup olmadığını kontrol et
        if (entity.isDead()) {
            incrementBlockedCount();

            logExploit(player.getName(),
                String.format("Ölü %s ile etkileşim!", type));

            event.setCancelled(true);
            debug(player.getName() + " için etkileşim engellendi (ölü entity)");
            return;
        }
    }

    /**
     * Entity'nin güvenli olup olmadığını kontrol eder
     */
    public boolean isEntitySafe(@NotNull Entity entity) {
        // Valid mi?
        if (!entity.isValid()) {
            return false;
        }

        // Dead mi?
        if (entity.isDead()) {
            return false;
        }

        // Chunk yüklü mü?
        if (!entity.getChunk().isLoaded()) {
            return false;
        }

        return true;
    }

    /**
     * Modül istatistiklerini döndürür
     */
    public String getStatistics() {
        return String.format("Engellenen duplikasyon: %d", getBlockedCount());
    }
}
