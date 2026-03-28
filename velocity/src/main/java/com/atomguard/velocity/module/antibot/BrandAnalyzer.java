package com.atomguard.velocity.module.antibot;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Minecraft client brand analizi - bot/exploit client tespiti.
 */
public class BrandAnalyzer {

    private static final Set<String> LEGITIMATE_BRANDS = Set.of(
        "vanilla", "fabric", "forge", "quilt", "neoforge",
        "optifine", "lunar client", "badlion client", "feather client",
        "pvplounge", "labymod", "essential", "multimc",
        // Yaygın mod/client'lar — false positive önleme
        "sodium", "iris", "liteclient", "liteloader",
        "fml,forge", "fml", "indium", "starlight",
        "tlauncher", "shiginima", "pojav",     // Cracked launcher'lar (offline-mode sunucular)
        "prism", "prismlauncher",              // Prism Launcher
        "cosmic", "cosmicclient",              // Cosmic Client
        "atlauncher", "gdlauncher",            // Launcher'lar
        "curseforge", "modrinth",              // Launcher platformları
        "technic", "ftb", "polymc", "hmcl",    // Mod pack launcher'lar
        "betacraft", "legacy",                 // Legacy sürümler
        "impact", "aristois",                  // Diğer modded client'lar
        "paper", "purpur", "folia"             // Bazen server-side brand olarak gelir
        // Not: "meteor" ve "wurst" kaldırıldı — bunlar hack client'ları.
        // Sunucu ihtiyacına göre "allowed-brands" config listesine eklenebilir.
    );

    private static final Set<String> SUSPICIOUS_KEYWORDS = Set.of(
        "bot", "crash", "exploit", "hack", "cheat", "flood",
        "ddos", "spam", "bruteforce", "scanner"
    );

    private final List<String> customBlockedBrands;
    /** Config'den okunan ek izinli brand'ler */
    private final List<String> additionalAllowedBrands;
    private final boolean allowUnknown;

    public BrandAnalyzer(List<String> customBlockedBrands, boolean allowUnknown) {
        this(customBlockedBrands, List.of(), allowUnknown);
    }

    public BrandAnalyzer(List<String> customBlockedBrands, List<String> additionalAllowedBrands, boolean allowUnknown) {
        this.customBlockedBrands = customBlockedBrands;
        this.additionalAllowedBrands = additionalAllowedBrands != null ? additionalAllowedBrands : List.of();
        this.allowUnknown = allowUnknown;
    }

    public BrandCheckResult check(String brand) {
        // Boş brand skoru düşürüldü: 30 → 10
        // Vanilla client bazen brand göndermez veya gecikmeli gönderir; bu tek başına bot işareti değil.
        if (brand == null || brand.isBlank())
            return new BrandCheckResult(BrandStatus.SUSPICIOUS, "Boş client brand", 10);

        String lower = brand.toLowerCase(Locale.ROOT);

        for (String blocked : customBlockedBrands) {
            if (lower.contains(blocked.toLowerCase(Locale.ROOT)))
                return new BrandCheckResult(BrandStatus.BLOCKED, "Yasaklı brand: " + brand, 100);
        }

        for (String keyword : SUSPICIOUS_KEYWORDS) {
            if (lower.contains(keyword))
                return new BrandCheckResult(BrandStatus.BLOCKED, "Şüpheli anahtar kelime: " + keyword, 90);
        }

        if (brand.length() > 64)
            return new BrandCheckResult(BrandStatus.SUSPICIOUS, "Aşırı uzun brand", 40);

        for (String legit : LEGITIMATE_BRANDS) {
            if (lower.contains(legit))
                return new BrandCheckResult(BrandStatus.CLEAN, "ok", 0);
        }

        for (String allowed : additionalAllowedBrands) {
            if (lower.contains(allowed.toLowerCase(Locale.ROOT)))
                return new BrandCheckResult(BrandStatus.CLEAN, "ok", 0);
        }

        if (!allowUnknown)
            return new BrandCheckResult(BrandStatus.SUSPICIOUS, "Bilinmeyen brand: " + brand, 20);

        return new BrandCheckResult(BrandStatus.CLEAN, "ok", 0);
    }

    public enum BrandStatus { CLEAN, SUSPICIOUS, BLOCKED }
    public record BrandCheckResult(BrandStatus status, String reason, int scoreContribution) {}
}
