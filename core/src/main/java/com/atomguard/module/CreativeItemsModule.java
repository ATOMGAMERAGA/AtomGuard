package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.atomguard.util.NBTUtils;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Creative Item Modülü
 *
 * Creative modda item kontrolü yapar ve zararlı item'ları engeller.
 * Creative exploit'lerini önlemek için tasarlanmıştır.
 *
 * Özellikler:
 * - Item blacklist kontrolü
 * - Maksimum NBT boyutu kontrolü
 * - Özel veri temizleme
 * - Maksimum büyü seviyesi kontrolü
 * - GameMode değişikliği tracking
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class CreativeItemsModule extends AbstractModule implements Listener {

    // Blacklist'teki item'lar
    private Set<Material> blacklistedItems;

    // Config cache
    private int maxNBTSize;
    private boolean stripCustomData;
    private int maxEnchantmentLevel;

    /**
     * CreativeItemsModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public CreativeItemsModule(@NotNull AtomGuard plugin) {
        super(plugin, "creative-item", "Creative mode item kontrolü");
        this.blacklistedItems = new HashSet<>();
    }

    @Override

    public void onEnable() {
        super.onEnable();

        // Config değerlerini yükle
        loadConfig();

        debug("Modül aktifleştirildi. Blacklist sayısı: " + blacklistedItems.size());
    }

    @Override

    public void onDisable() {
        super.onDisable();

        // Blacklist'i temizle
        blacklistedItems.clear();

        // Event listener'ı kaldır
        InventoryClickEvent.getHandlerList().unregister(this);
        PlayerGameModeChangeEvent.getHandlerList().unregister(this);

        debug("Modül devre dışı bırakıldı.");
    }

    /**
     * Config değerlerini yükler
     */
    private void loadConfig() {
        blacklistedItems.clear();

        // Blacklist'i yükle
        List<String> itemList = plugin.getConfigManager()
            .getConfig()
            .getStringList("moduller." + name + ".item-kara-liste");

        if (itemList == null || itemList.isEmpty()) {
            itemList = getDefaultBlacklistedItems();
        }

        for (String itemName : itemList) {
            try {
                Material material = Material.valueOf(itemName.toUpperCase());
                blacklistedItems.add(material);
                debug("Blacklist'e eklendi: " + material);
            } catch (IllegalArgumentException e) {
                error("Geçersiz item: " + itemName);
            }
        }

        this.maxNBTSize = getConfigInt("max-nbt-boyutu-creative", 10000);
        this.stripCustomData = getConfigBoolean("ozel-veriyi-soy", true);
        this.maxEnchantmentLevel = getConfigInt("max-buyu-seviyesi", 10);

        debug("Config yüklendi: maxNBT=" + maxNBTSize +
              ", stripData=" + stripCustomData +
              ", maxEnchant=" + maxEnchantmentLevel);
    }

    /**
     * Varsayılan blacklist'teki item'ları döndürür
     */
    @NotNull
    private List<String> getDefaultBlacklistedItems() {
        List<String> defaults = new ArrayList<>();
        defaults.add("COMMAND_BLOCK");
        defaults.add("CHAIN_COMMAND_BLOCK");
        defaults.add("REPEATING_COMMAND_BLOCK");
        defaults.add("COMMAND_BLOCK_MINECART");
        defaults.add("STRUCTURE_BLOCK");
        defaults.add("STRUCTURE_VOID");
        defaults.add("JIGSAW");
        defaults.add("BARRIER");
        defaults.add("BEDROCK");
        defaults.add("SPAWNER");
        return defaults;
    }

    /**
     * Inventory click olayını dinler
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isEnabled()) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Sadece creative mode'da kontrol et
        if (player.getGameMode() != GameMode.CREATIVE) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        // Blacklist kontrolü
        if (blacklistedItems.contains(item.getType())) {
            incrementBlockedCount();

            logExploit(player.getName(),
                String.format("Blacklist'teki item kullanımı: %s", item.getType()));

            event.setCancelled(true);
            player.sendMessage(plugin.getMessageManager().getMessage("item-engellendi"));

            debug(player.getName() + " için item engellendi (blacklist)");
            return;
        }

        // NBT boyutu kontrolü
        int nbtSize = NBTUtils.estimateNBTSize(item);
        if (nbtSize > maxNBTSize) {
            incrementBlockedCount();

            logExploit(player.getName(),
                String.format("Çok büyük NBT verisi: %d bytes (Limit: %d)", nbtSize, maxNBTSize));

            if (stripCustomData) {
                // Özel veriyi temizle
                ItemStack sanitized = NBTUtils.sanitizeNBT(item, 10);
                event.setCurrentItem(sanitized);
                debug(player.getName() + " için item temizlendi (NBT aşımı)");
            } else {
                event.setCancelled(true);
                debug(player.getName() + " için item engellendi (NBT aşımı)");
            }
            return;
        }

        // Enchantment seviyesi kontrolü
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasEnchants()) {
                for (var entry : meta.getEnchants().entrySet()) {
                    Enchantment enchant = entry.getKey();
                    int level = entry.getValue();

                    if (level > maxEnchantmentLevel) {
                        incrementBlockedCount();

                        logExploit(player.getName(),
                            String.format("Aşırı büyü seviyesi: %s %d (Limit: %d)",
                                enchant.getKey().getKey(), level, maxEnchantmentLevel));

                        // Büyü seviyesini düşür
                        meta.removeEnchant(enchant);
                        meta.addEnchant(enchant, Math.min(level, maxEnchantmentLevel), true);
                        item.setItemMeta(meta);
                        event.setCurrentItem(item);

                        debug(player.getName() + " için büyü seviyesi düşürüldü");
                    }
                }
            }
        }
    }

    /**
     * GameMode değişikliği olayını dinler
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (!isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        GameMode newMode = event.getNewGameMode();

        // Creative'den başka moda geçiş
        if (player.getGameMode() == GameMode.CREATIVE && newMode != GameMode.CREATIVE) {
            debug(player.getName() + " creative moddan çıkıyor, envanter temizleniyor");

            // Envanteri kontrol et ve temizle
            cleanPlayerInventory(player);
        }
    }

    /**
     * Oyuncunun envanterini temizler
     */
    private void cleanPlayerInventory(@NotNull Player player) {
        int cleanedCount = 0;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            // Blacklist kontrolü
            if (blacklistedItems.contains(item.getType())) {
                player.getInventory().remove(item);
                cleanedCount++;
                debug("Blacklist item kaldırıldı: " + item.getType());
                continue;
            }

            // NBT kontrolü
            if (stripCustomData) {
                int nbtSize = NBTUtils.estimateNBTSize(item);
                if (nbtSize > maxNBTSize) {
                    ItemStack sanitized = NBTUtils.sanitizeNBT(item, 10);
                    player.getInventory().remove(item);
                    player.getInventory().addItem(sanitized);
                    cleanedCount++;
                    debug("Item temizlendi (NBT aşımı)");
                }
            }
        }

        if (cleanedCount > 0) {
            info(player.getName() + " envanterinden " + cleanedCount + " item temizlendi");
            java.util.Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("count", String.valueOf(cleanedCount));
            player.sendMessage(plugin.getMessageManager()
                .getMessage("envanter-temizlendi", placeholders));
        }
    }

    /**
     * Modül istatistiklerini döndürür
     */
    public String getStatistics() {
        return String.format("Blacklist sayısı: %d, Engellenen item: %d",
            blacklistedItems.size(),
            getBlockedCount());
    }

    /**
     * Blacklist'e item ekler
     */
    public void addBlacklistedItem(@NotNull Material material) {
        blacklistedItems.add(material);
        debug("Blacklist'e eklendi: " + material);
    }

    /**
     * Blacklist'ten item kaldırır
     */
    public void removeBlacklistedItem(@NotNull Material material) {
        blacklistedItems.remove(material);
        debug("Blacklist'ten kaldırıldı: " + material);
    }
}
