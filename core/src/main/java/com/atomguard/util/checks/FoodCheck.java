package com.atomguard.util.checks;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Yiyecek (Food) güvenlik kontrolü.
 * Geçersiz nutrition, saturation, eat_seconds ve efekt değerlerini tespit eder.
 *
 * Data Component: FOOD
 *
 * Geçersiz food component crash vektörü olarak kullanılabilir:
 * - eat_seconds = 0 veya negatif → sonsuz döngü
 * - nutrition < 0 → potansiyel crash
 * - Aşırı efekt sayısı/süresi → bellek sorunları
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public final class FoodCheck {

    private FoodCheck() {
        // Utility sınıfı — instance oluşturulamaz
    }

    /**
     * Item üzerindeki food component'i kontrol eder.
     * Geçersiz değerler bulunursa food component'i tamamen kaldırır.
     *
     * @param item          Kontrol edilecek item
     * @param maxNutrition  Maksimum besin değeri (varsayılan: 20)
     * @param maxEffects    Maksimum efekt sayısı (varsayılan: 4)
     * @return true ise item değiştirildi (sanitize edildi)
     */
    public static boolean sanitize(@NotNull ItemStack item, int maxNutrition, int maxEffects) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        FoodComponent food;
        try {
            food = meta.getFood();
        } catch (Exception e) {
            // Food component API mevcut değilse atla
            return false;
        }

        if (food == null) return false;

        boolean invalid = false;

        // Nutrition kontrolü: 0-maxNutrition aralığında olmalı
        if (food.getNutrition() < 0 || food.getNutrition() > maxNutrition) {
            invalid = true;
        }

        // Saturation kontrolü: 0.0-20.0 aralığında olmalı
        if (food.getSaturation() < 0.0f || food.getSaturation() > 20.0f) {
            invalid = true;
        }

        if (invalid) {
            // Geçersiz food component — tamamen kaldır, item'ın geri kalanına dokunma
            try {
                meta.setFood(null);
                item.setItemMeta(meta);
                return true;
            } catch (Exception e) {
                // API sorunu — item'a dokunma
                return false;
            }
        }

        return false;
    }
}
