package com.atomguard.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * NBT işlemleri için yardımcı sınıf
 * Item NBT verilerini kontrol ve temizleme
 */
public class NBTUtils {

    /**
     * Item'ın NBT tag sayısını tahmin eder
     * Paper API kullanarak item meta verilerini sayar
     *
     * @param item Item
     * @return Tahmini NBT tag sayısı
     */
    public static int estimateNBTTagCount(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }

        ItemMeta meta = item.getItemMeta();
        int count = 0;

        // Display name
        if (meta.hasDisplayName()) {
            count++;
        }

        // Lore
        if (meta.hasLore()) {
            count += meta.getLore() != null ? meta.getLore().size() : 0;
        }

        // Enchantments
        if (meta.hasEnchants()) {
            count += meta.getEnchants().size();
        }

        // Custom model data
        if (meta.hasCustomModelData()) {
            count++;
        }

        // Attribute modifiers
        if (meta.hasAttributeModifiers()) {
            count += meta.getAttributeModifiers() != null
                     ? meta.getAttributeModifiers().size()
                     : 0;
        }

        // Unbreakable
        if (meta.isUnbreakable()) {
            count++;
        }

        // Item flags
        count += meta.getItemFlags().size();

        // PDC (Persistent Data Container) - Custom plugin data
        if (!meta.getPersistentDataContainer().isEmpty()) {
            count += meta.getPersistentDataContainer().getKeys().size();
        }

        return count;
    }

    /**
     * Item'ın NBT derinliğini tahmin eder
     * Nested yapıları kontrol eder
     *
     * @param item Item
     * @return Tahmini NBT derinliği
     */
    public static int estimateNBTDepth(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }

        int maxDepth = 1; // Base level

        ItemMeta meta = item.getItemMeta();

        // Lore nested depth
        if (meta.hasLore() && meta.getLore() != null) {
            maxDepth = Math.max(maxDepth, 2);
        }

        // Attribute modifiers nested
        if (meta.hasAttributeModifiers() && meta.getAttributeModifiers() != null) {
            maxDepth = Math.max(maxDepth, 2);
        }

        // PDC nested keys
        if (!meta.getPersistentDataContainer().isEmpty()) {
            maxDepth = Math.max(maxDepth, 2);
        }

        return maxDepth;
    }

    /**
     * Item'ın tahmini NBT boyutunu hesaplar (byte)
     * Yaklaşık bir değer döndürür
     *
     * @param item Item
     * @return Tahmini boyut (byte)
     */
    public static int estimateNBTSize(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }

        ItemMeta meta = item.getItemMeta();
        int totalSize = 0;

        // Display name
        if (meta.hasDisplayName()) {
            totalSize += meta.getDisplayName().length() * 2; // Unicode karakterler için x2
        }

        // Lore
        if (meta.hasLore() && meta.getLore() != null) {
            for (String line : meta.getLore()) {
                totalSize += line.length() * 2;
            }
        }

        // Enchantments (her enchant ~10-20 byte)
        if (meta.hasEnchants()) {
            totalSize += meta.getEnchants().size() * 15;
        }

        // Attribute modifiers (her attribute ~30-50 byte)
        if (meta.hasAttributeModifiers() && meta.getAttributeModifiers() != null) {
            totalSize += meta.getAttributeModifiers().size() * 40;
        }

        // Custom model data (~4 byte)
        if (meta.hasCustomModelData()) {
            totalSize += 4;
        }

        // PDC keys (her key ~20-100 byte arası değişebilir)
        if (!meta.getPersistentDataContainer().isEmpty()) {
            totalSize += meta.getPersistentDataContainer().getKeys().size() * 50;
        }

        return totalSize;
    }

    /**
     * Item'ın NBT verilerinin güvenli olup olmadığını kontrol eder
     *
     * @param item Item
     * @param maxTags Maksimum tag sayısı
     * @param maxDepth Maksimum derinlik
     * @param maxSize Maksimum boyut (byte)
     * @return Güvenli mi?
     */
    public static boolean isNBTSafe(@Nullable ItemStack item,
                                     int maxTags,
                                     int maxDepth,
                                     int maxSize) {
        if (item == null) {
            return true;
        }

        int tagCount = estimateNBTTagCount(item);
        int depth = estimateNBTDepth(item);
        int size = estimateNBTSize(item);

        return tagCount <= maxTags && depth <= maxDepth && size <= maxSize;
    }

    /**
     * Item'dan aşırı NBT verilerini temizler
     * Zararlı olabilecek verileri kaldırır
     *
     * @param item Item
     * @param maxLoreLines Maksimum lore satır sayısı
     * @return Temizlenmiş item
     */
    @NotNull
    public static ItemStack sanitizeNBT(@NotNull ItemStack item, int maxLoreLines) {
        if (!item.hasItemMeta()) {
            return item;
        }

        ItemMeta meta = item.getItemMeta();

        // Lore'u sınırla
        if (meta.hasLore() && meta.getLore() != null) {
            var lore = meta.getLore();
            if (lore.size() > maxLoreLines) {
                meta.setLore(lore.subList(0, maxLoreLines));
            }
        }

        // Display name'i sınırla
        if (meta.hasDisplayName()) {
            String displayName = meta.getDisplayName();
            if (displayName.length() > 256) {
                meta.setDisplayName(displayName.substring(0, 256));
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Enchantment seviyesinin güvenli olup olmadığını kontrol eder
     *
     * @param level Seviye
     * @param maxLevel Maksimum seviye
     * @return Güvenli mi?
     */
    public static boolean isEnchantmentLevelSafe(int level, int maxLevel) {
        return level >= 1 && level <= maxLevel;
    }

    /**
     * Item meta'nın boş olup olmadığını kontrol eder
     *
     * @param item Item
     * @return Boş mu?
     */
    public static boolean hasEmptyMeta(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return true;
        }

        ItemMeta meta = item.getItemMeta();

        return !meta.hasDisplayName() &&
               !meta.hasLore() &&
               !meta.hasEnchants() &&
               !meta.hasCustomModelData() &&
               !meta.hasAttributeModifiers() &&
               meta.getItemFlags().isEmpty() &&
               meta.getPersistentDataContainer().isEmpty();
    }
}
