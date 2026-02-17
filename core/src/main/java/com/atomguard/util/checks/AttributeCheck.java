package com.atomguard.util.checks;

import com.google.common.collect.Multimap;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Attribute Modifier güvenlik kontrolü.
 * Aşırı yüksek değerleri ve fazla modifier sayısını tespit eder.
 *
 * Data Component: ATTRIBUTE_MODIFIERS
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public final class AttributeCheck {

    private AttributeCheck() {
        // Utility sınıfı — instance oluşturulamaz
    }

    /**
     * Attribute key'ine göre maksimum izin verilen değeri döndürür.
     * Hem eski (generic.xxx) hem yeni (xxx) format desteklenir.
     *
     * @param attribute Kontrol edilecek attribute
     * @return Maksimum izin verilen değer
     */
    private static double getMaxValue(@NotNull Attribute attribute) {
        String key = attribute.getKey().getKey();
        return switch (key) {
            case "generic.attack_damage", "attack_damage" -> 20.0;
            case "generic.movement_speed", "movement_speed" -> 0.5;
            case "generic.max_health", "max_health" -> 2048.0;
            case "generic.armor", "armor" -> 30.0;
            case "generic.knockback_resistance", "knockback_resistance" -> 1.0;
            default -> Double.MAX_VALUE; // Bilinmeyen attribute'lar sınırsız
        };
    }

    /**
     * Item üzerindeki attribute modifier'ları kontrol eder.
     * Maksimum değerleri aşan veya toplam sayıyı aşan modifier'ları kaldırır.
     *
     * @param item          Kontrol edilecek item
     * @param maxModifiers  Item başına maksimum modifier sayısı
     * @return true ise item değiştirildi (sanitize edildi)
     */
    public static boolean sanitize(@NotNull ItemStack item, int maxModifiers) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasAttributeModifiers()) return false;

        Multimap<Attribute, AttributeModifier> modifiers = meta.getAttributeModifiers();
        if (modifiers == null || modifiers.isEmpty()) return false;

        boolean modified = false;
        int totalCount = 0;

        // Kaldırılması gereken modifier'ları topla (ConcurrentModificationException önleme)
        List<Map.Entry<Attribute, AttributeModifier>> toRemove = new ArrayList<>();

        for (Map.Entry<Attribute, AttributeModifier> entry : modifiers.entries()) {
            Attribute attribute = entry.getKey();
            AttributeModifier modifier = entry.getValue();
            totalCount++;

            // Değer kontrolü — max değeri aşan modifier'ları kaldır
            double maxValue = getMaxValue(attribute);
            if (Math.abs(modifier.getAmount()) > maxValue) {
                toRemove.add(entry);
                continue;
            }

            // Toplam modifier sayısı kontrolü
            if (totalCount > maxModifiers) {
                toRemove.add(entry);
            }
        }

        // Geçersiz modifier'ları kaldır
        for (Map.Entry<Attribute, AttributeModifier> entry : toRemove) {
            meta.removeAttributeModifier(entry.getKey(), entry.getValue());
            modified = true;
        }

        if (modified) {
            item.setItemMeta(meta);
        }

        return modified;
    }
}
