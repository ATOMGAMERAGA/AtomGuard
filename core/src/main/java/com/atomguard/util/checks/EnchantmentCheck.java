package com.atomguard.util.checks;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Büyü (Enchantment) güvenlik kontrolü.
 * Vanilla sınırlarını aşan büyü seviyelerini tespit eder ve düzeltir.
 *
 * Data Component: ENCHANTMENTS, STORED_ENCHANTMENTS
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public final class EnchantmentCheck {

    private EnchantmentCheck() {
        // Utility sınıfı — instance oluşturulamaz
    }

    /**
     * Item üzerindeki büyüleri kontrol eder ve vanilla max + tolerans üzerindeki
     * seviyeleri vanilla max'a sıfırlar.
     *
     * @param item      Kontrol edilecek item
     * @param tolerance Tolerans değeri (0 = sadece vanilla max, 1 = max+1'e kadar izin ver)
     * @return true ise item değiştirildi (sanitize edildi)
     */
    public static boolean sanitize(@NotNull ItemStack item, int tolerance) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        boolean modified = false;

        // Normal enchantment'ları kontrol et
        Map<Enchantment, Integer> enchants = meta.getEnchants();
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            Enchantment enchantment = entry.getKey();
            int level = entry.getValue();
            int maxLevel = enchantment.getMaxLevel();

            if (level > maxLevel + tolerance) {
                meta.removeEnchant(enchantment);
                meta.addEnchant(enchantment, maxLevel, true);
                modified = true;
            }
        }

        // Enchanted Book ise stored enchantment'ları da kontrol et
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            Map<Enchantment, Integer> storedEnchants = storageMeta.getStoredEnchants();
            for (Map.Entry<Enchantment, Integer> entry : storedEnchants.entrySet()) {
                Enchantment enchantment = entry.getKey();
                int level = entry.getValue();
                int maxLevel = enchantment.getMaxLevel();

                if (level > maxLevel + tolerance) {
                    storageMeta.removeStoredEnchant(enchantment);
                    storageMeta.addStoredEnchant(enchantment, maxLevel, true);
                    modified = true;
                }
            }
        }

        if (modified) {
            item.setItemMeta(meta);
        }

        return modified;
    }
}
