package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.atomguard.util.ItemSanitizer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Item Sanitizer Modülü (1.21 Data Components uyumlu)
 *
 * Her item etkileşiminde güvenlik check'leri çalıştırır:
 * - Enchantment cap (büyü seviyesi sınırı)
 * - Attribute modifier kontrolü
 * - Skull/Head texture kontrolü
 * - Food safety kontrolü
 *
 * Tetikleyici event'ler:
 * InventoryClickEvent, PlayerInteractEvent, EntityPickupItemEvent,
 * BlockPlaceEvent, PlayerDropItemEvent, InventoryMoveItemEvent (hopper koruması)
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class ItemSanitizerModule extends AbstractModule implements Listener {

    // Config cache
    private int enchantTolerance;
    private int maxAttributeModifiers;
    private int maxSkullTextureBytes;
    private int maxNutrition;
    private int maxFoodEffects;

    /**
     * ItemSanitizerModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public ItemSanitizerModule(@NotNull AtomGuard plugin) {
        super(plugin, "item-temizleyici", "Item güvenlik temizleyicisi");
    }

    @Override

    public void onEnable() {
        super.onEnable();
        loadConfig();

        debug("Item sanitizer başlatıldı. Büyü toleransı: " + enchantTolerance +
                ", Max modifier: " + maxAttributeModifiers);
    }

    @Override

    public void onDisable() {
        super.onDisable();
        HandlerList.unregisterAll(this);
        debug("Item sanitizer durduruldu.");
    }

    /**
     * Config değerlerini yükler
     */
    private void loadConfig() {
        this.enchantTolerance = getConfigInt("buyu-toleransi", 5);
        this.maxAttributeModifiers = getConfigInt("max-attribute-modifier", 12);
        this.maxSkullTextureBytes = getConfigInt("max-kafa-texture-bayt", 10240);
        this.maxNutrition = getConfigInt("max-besin-degeri", 20);
        this.maxFoodEffects = getConfigInt("max-yemek-efekt", 4);
    }

    // ═══════════════════════════════════════
    // Event Handler'lar
    // ═══════════════════════════════════════

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!isEnabled()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (player.hasPermission("atomguard.bypass")) return;

        // Tıklanan item ve cursor item'ı kontrol et
        sanitizeAndLog(event.getCurrentItem(), player);
        sanitizeAndLog(event.getCursor(), player);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        if (!isEnabled()) return;

        Player player = event.getPlayer();
        if (player.hasPermission("atomguard.bypass")) return;

        sanitizeAndLog(event.getItem(), player);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityPickupItem(@NotNull EntityPickupItemEvent event) {
        if (!isEnabled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.hasPermission("atomguard.bypass")) return;

        ItemStack item = event.getItem().getItemStack();
        ItemSanitizer.SanitizeResult result = runSanitize(item);
        if (result.modified()) {
            event.getItem().setItemStack(item);
            logSanitize(player, result);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(@NotNull BlockPlaceEvent event) {
        if (!isEnabled()) return;

        Player player = event.getPlayer();
        if (player.hasPermission("atomguard.bypass")) return;

        ItemStack item = event.getItemInHand();
        ItemSanitizer.SanitizeResult result = runSanitize(item);
        if (result.modified()) {
            // Geçersiz item ile blok yerleştirmeyi engelle
            event.setCancelled(true);
            logSanitize(player, result);
            plugin.getMessageManager().sendPrefixedMessage(player, "engelleme.item-temizlendi");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerDropItem(@NotNull PlayerDropItemEvent event) {
        if (!isEnabled()) return;

        Player player = event.getPlayer();
        if (player.hasPermission("atomguard.bypass")) return;

        ItemStack item = event.getItemDrop().getItemStack();
        ItemSanitizer.SanitizeResult result = runSanitize(item);
        if (result.modified()) {
            event.getItemDrop().setItemStack(item);
            logSanitize(player, result);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryMoveItem(@NotNull InventoryMoveItemEvent event) {
        if (!isEnabled()) return;

        // Hopper koruması — oyuncu olmadan da çalışmalı
        ItemStack item = event.getItem();
        ItemSanitizer.SanitizeResult result = runSanitize(item);
        if (result.modified()) {
            event.setItem(item);
            incrementBlockedCount();
            info("Hopper ile taşınan geçersiz item temizlendi: " + result.getSummary());
        }
    }

    // ═══════════════════════════════════════
    // Yardımcı Metodlar
    // ═══════════════════════════════════════

    /**
     * Item üzerinde sanitize çalıştırır
     */
    @NotNull
    private ItemSanitizer.SanitizeResult runSanitize(@Nullable ItemStack item) {
        if (item == null) return ItemSanitizer.SanitizeResult.CLEAN;
        return ItemSanitizer.sanitize(item, enchantTolerance, maxAttributeModifiers,
                maxSkullTextureBytes, maxNutrition, maxFoodEffects);
    }

    /**
     * Item'ı kontrol eder, gerekirse düzeltir ve loglar
     */
    private void sanitizeAndLog(@Nullable ItemStack item, @NotNull Player player) {
        ItemSanitizer.SanitizeResult result = runSanitize(item);
        if (result.modified()) {
            logSanitize(player, result);
            plugin.getMessageManager().sendPrefixedMessage(player, "engelleme.item-temizlendi");
        }
    }

    /**
     * Sanitize sonucunu loglar
     */
    private void logSanitize(@NotNull Player player, @NotNull ItemSanitizer.SanitizeResult result) {
        incrementBlockedCount();
        logExploit(player.getName(), "Item temizlendi: " + result.getSummary());

        // Admin bildirimi
        plugin.getMessageManager().notifyWithPermission(
                "atomguard.notify",
                "bildirim.admin-uyari",
                Map.of("oyuncu", player.getName(), "exploit", "item-temizleyici"));
    }
}
