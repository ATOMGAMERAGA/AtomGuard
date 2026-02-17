package com.atomguard.util;

import com.atomguard.util.checks.AttributeCheck;
import com.atomguard.util.checks.EnchantmentCheck;
import com.atomguard.util.checks.FoodCheck;
import com.atomguard.util.checks.SkullCheck;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stateless item temizleme yardımcı sınıfı.
 * Tüm güvenlik check'lerini sırayla çalıştırır.
 *
 * Akış: ItemStack alındı → sanitize(item) → her check çalışır →
 *        değişiklik varsa logla → düzeltilmiş item'ı event'e geri yaz
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public final class ItemSanitizer {

    private ItemSanitizer() {
        // Utility sınıfı — instance oluşturulamaz
    }

    /**
     * Verilen item üzerinde tüm güvenlik check'lerini çalıştırır.
     *
     * @param item             Kontrol edilecek item (null veya AIR ise false döner)
     * @param enchantTolerance Büyü toleransı (0 = sadece vanilla max)
     * @param maxModifiers     Item başına maksimum attribute modifier sayısı
     * @param maxTextureBytes  Skull texture maksimum boyutu (byte)
     * @param maxNutrition     Maksimum besin değeri
     * @param maxFoodEffects   Maksimum yemek efekt sayısı
     * @return Sanitize sonucu (hangi check'lerin tetiklendiğini içerir)
     */
    @NotNull
    public static SanitizeResult sanitize(@Nullable ItemStack item,
                                          int enchantTolerance,
                                          int maxModifiers,
                                          int maxTextureBytes,
                                          int maxNutrition,
                                          int maxFoodEffects) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return SanitizeResult.CLEAN;
        }

        boolean enchantModified = false;
        boolean attributeModified = false;
        boolean skullModified = false;
        boolean foodModified = false;

        try {
            // 1. Büyü kontrolü
            enchantModified = EnchantmentCheck.sanitize(item, enchantTolerance);
        } catch (Exception e) {
            // Güvenli tarafta kal — check başarısız olursa atla
        }

        try {
            // 2. Attribute modifier kontrolü
            attributeModified = AttributeCheck.sanitize(item, maxModifiers);
        } catch (Exception e) {
            // Güvenli tarafta kal
        }

        try {
            // 3. Skull/head kontrolü (sadece kafa item'ları için)
            if (isSkullItem(item.getType())) {
                skullModified = SkullCheck.sanitize(item, maxTextureBytes);
            }
        } catch (Exception e) {
            // Güvenli tarafta kal
        }

        try {
            // 4. Food kontrolü
            foodModified = FoodCheck.sanitize(item, maxNutrition, maxFoodEffects);
        } catch (Exception e) {
            // Güvenli tarafta kal
        }

        if (enchantModified || attributeModified || skullModified || foodModified) {
            return new SanitizeResult(true, enchantModified, attributeModified, skullModified, foodModified);
        }

        return SanitizeResult.CLEAN;
    }

    /**
     * Item'ın skull/head türü olup olmadığını kontrol eder
     */
    private static boolean isSkullItem(@NotNull Material type) {
        return type == Material.PLAYER_HEAD
                || type == Material.PLAYER_WALL_HEAD
                || type == Material.SKELETON_SKULL
                || type == Material.SKELETON_WALL_SKULL
                || type == Material.ZOMBIE_HEAD
                || type == Material.ZOMBIE_WALL_HEAD
                || type == Material.CREEPER_HEAD
                || type == Material.CREEPER_WALL_HEAD
                || type == Material.DRAGON_HEAD
                || type == Material.DRAGON_WALL_HEAD
                || type == Material.PIGLIN_HEAD
                || type == Material.PIGLIN_WALL_HEAD;
    }

    /**
     * Sanitize işleminin sonucunu temsil eder.
     * Hangi check'lerin tetiklendiği bilgisini taşır.
     */
    public record SanitizeResult(
            boolean modified,
            boolean enchantmentFixed,
            boolean attributeFixed,
            boolean skullFixed,
            boolean foodFixed
    ) {
        /** Temiz item — hiçbir check tetiklenmedi */
        public static final SanitizeResult CLEAN = new SanitizeResult(false, false, false, false, false);

        /**
         * İnsan okunabilir özet oluşturur
         */
        @NotNull
        public String getSummary() {
            if (!modified) return "Temiz";
            StringBuilder sb = new StringBuilder();
            if (enchantmentFixed) sb.append("Büyü düzeltildi, ");
            if (attributeFixed) sb.append("Attribute düzeltildi, ");
            if (skullFixed) sb.append("Kafa düzeltildi, ");
            if (foodFixed) sb.append("Yiyecek düzeltildi, ");
            // Son virgül-boşluğu kaldır
            if (sb.length() > 2) sb.setLength(sb.length() - 2);
            return sb.toString();
        }
    }
}
