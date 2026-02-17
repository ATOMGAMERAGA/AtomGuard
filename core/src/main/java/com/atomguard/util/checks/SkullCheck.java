package com.atomguard.util.checks;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Skull / Player Head güvenlik kontrolü.
 * Aşırı büyük texture verileri ve geçersiz texture URL'lerini tespit eder.
 *
 * Data Component: PROFILE
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public final class SkullCheck {

    /** Tek geçerli texture host'u */
    private static final String VALID_TEXTURE_HOST = "textures.minecraft.net";

    private SkullCheck() {
        // Utility sınıfı — instance oluşturulamaz
    }

    /**
     * Skull item'ını kontrol eder.
     * Texture boyutu > maxTextureBytes ise SKELETON_SKULL ile değiştirir.
     * Texture URL'si textures.minecraft.net dışındaysa SKELETON_SKULL ile değiştirir.
     *
     * @param item            Kontrol edilecek item
     * @param maxTextureBytes Maksimum texture boyutu (byte, varsayılan 10240)
     * @return true ise item değiştirildi (sanitize edildi)
     */
    public static boolean sanitize(@NotNull ItemStack item, int maxTextureBytes) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof SkullMeta skullMeta)) return false;

        PlayerProfile profile = skullMeta.getPlayerProfile();
        if (profile == null) return false;

        for (ProfileProperty property : profile.getProperties()) {
            if (!"textures".equals(property.getName())) continue;

            String textureValue = property.getValue();
            if (textureValue == null) continue;

            // Boyut kontrolü — Base64 string uzunluğu byte boyutunu temsil eder
            byte[] textureBytes = textureValue.getBytes(StandardCharsets.UTF_8);
            if (textureBytes.length > maxTextureBytes) {
                // Aşırı büyük texture — SKELETON_SKULL ile değiştir
                item.setType(Material.SKELETON_SKULL);
                item.setItemMeta(null);
                return true;
            }

            // URL kontrolü — sadece textures.minecraft.net kabul edilir
            if (!isTextureUrlValid(textureValue)) {
                item.setType(Material.SKELETON_SKULL);
                item.setItemMeta(null);
                return true;
            }
        }

        return false;
    }

    /**
     * Base64 kodlanmış texture değerindeki URL'nin geçerli olup olmadığını kontrol eder.
     *
     * @param base64Value Base64 kodlanmış texture değeri
     * @return URL geçerli ise true
     */
    private static boolean isTextureUrlValid(@NotNull String base64Value) {
        try {
            String decoded = new String(Base64.getDecoder().decode(base64Value), StandardCharsets.UTF_8);
            // JSON içinde URL'yi ara — textures.minecraft.net olmalı
            // Basit string kontrolü yeterli, tam JSON parse gereksiz
            if (decoded.contains("url")) {
                return decoded.contains(VALID_TEXTURE_HOST);
            }
            // URL yoksa geçerli kabul et (profil bilgisi URL içermiyor olabilir)
            return true;
        } catch (IllegalArgumentException e) {
            // Geçersiz Base64 — güvensiz kabul et
            return false;
        }
    }
}
