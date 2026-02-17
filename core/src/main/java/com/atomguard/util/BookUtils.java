package com.atomguard.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Kitap işlemleri için yardımcı sınıf
 * Kitap kontrolü, temizleme ve validasyon
 */
public class BookUtils {

    /**
     * Item'ın kitap olup olmadığını kontrol eder
     *
     * @param item Item
     * @return Kitap mı?
     */
    public static boolean isBook(@Nullable ItemStack item) {
        if (item == null) {
            return false;
        }

        Material type = item.getType();
        return type == Material.WRITTEN_BOOK ||
               type == Material.WRITABLE_BOOK ||
               type == Material.BOOK;
    }

    /**
     * Kitabın yazılmış kitap olup olmadığını kontrol eder
     *
     * @param item Item
     * @return Yazılmış kitap mı?
     */
    public static boolean isWrittenBook(@Nullable ItemStack item) {
        return item != null && item.getType() == Material.WRITTEN_BOOK;
    }

    /**
     * Kitabın sayfa sayısını döndürür
     *
     * @param item Kitap
     * @return Sayfa sayısı
     */
    public static int getPageCount(@Nullable ItemStack item) {
        if (!isBook(item) || !item.hasItemMeta()) {
            return 0;
        }

        if (!(item.getItemMeta() instanceof BookMeta meta)) {
            return 0;
        }
        return meta.getPageCount();
    }

    /**
     * Kitabın başlığını döndürür
     *
     * @param item Kitap
     * @return Başlık (yoksa null)
     */
    @Nullable
    public static String getTitle(@Nullable ItemStack item) {
        if (!isBook(item) || !item.hasItemMeta()) {
            return null;
        }

        if (!(item.getItemMeta() instanceof BookMeta meta)) {
            return null;
        }
        return meta.getTitle();
    }

    /**
     * Kitabın toplam boyutunu hesaplar (byte)
     *
     * @param item Kitap
     * @return Toplam boyut (byte)
     */
    public static int calculateBookSize(@Nullable ItemStack item) {
        if (!isBook(item) || !item.hasItemMeta()) {
            return 0;
        }

        if (!(item.getItemMeta() instanceof BookMeta meta)) {
            return 0;
        }
        int totalSize = 0;

        // Başlık
        if (meta.hasTitle() && meta.getTitle() != null) {
            totalSize += meta.getTitle().length() * 2; // Unicode için x2
        }

        // Yazar
        if (meta.hasAuthor() && meta.getAuthor() != null) {
            totalSize += meta.getAuthor().length() * 2;
        }

        // Sayfalar
        if (meta.hasPages()) {
            for (String page : meta.getPages()) {
                totalSize += page.length() * 2;
            }
        }

        return totalSize;
    }

    /**
     * Kitabın güvenli olup olmadığını kontrol eder
     *
     * @param item Kitap
     * @param maxTitle Maksimum başlık uzunluğu
     * @param maxPages Maksimum sayfa sayısı
     * @param maxPageSize Maksimum sayfa boyutu
     * @param maxTotalSize Maksimum toplam boyut
     * @return Güvenli mi?
     */
    public static boolean isBookSafe(@Nullable ItemStack item,
                                      int maxTitle,
                                      int maxPages,
                                      int maxPageSize,
                                      int maxTotalSize) {
        if (!isBook(item)) {
            return true; // Kitap değilse güvenli
        }

        if (!item.hasItemMeta()) {
            return true;
        }

        if (!(item.getItemMeta() instanceof BookMeta meta)) {
            return true;
        }

        // Başlık kontrolü
        if (meta.hasTitle() && meta.getTitle() != null && meta.getTitle().length() > maxTitle) {
            return false;
        }

        // Sayfa sayısı kontrolü
        if (meta.getPageCount() > maxPages) {
            return false;
        }

        // Sayfa boyutu kontrolü
        if (meta.hasPages()) {
            for (String page : meta.getPages()) {
                if (page.length() > maxPageSize) {
                    return false;
                }
            }
        }

        // Toplam boyut kontrolü
        int totalSize = calculateBookSize(item);
        if (totalSize > maxTotalSize) {
            return false;
        }

        return true;
    }

    /**
     * Kitabı temizler ve güvenli hale getirir
     *
     * @param item Kitap
     * @param maxTitle Maksimum başlık uzunluğu
     * @param maxPages Maksimum sayfa sayısı
     * @param maxPageSize Maksimum sayfa boyutu
     * @return Temizlenmiş kitap
     */
    @NotNull
    public static ItemStack sanitizeBook(@NotNull ItemStack item,
                                          int maxTitle,
                                          int maxPages,
                                          int maxPageSize) {
        if (!isBook(item) || !item.hasItemMeta()) {
            return item;
        }

        if (!(item.getItemMeta() instanceof BookMeta meta)) {
            return item;
        }

        // Başlığı sınırla
        if (meta.hasTitle() && meta.getTitle() != null && meta.getTitle().length() > maxTitle) {
            meta.setTitle(meta.getTitle().substring(0, maxTitle));
        }

        // Sayfaları sınırla
        if (meta.hasPages()) {
            List<String> pages = new ArrayList<>(meta.getPages());
            List<String> sanitizedPages = new ArrayList<>();

            int pageCount = 0;
            for (String page : pages) {
                if (pageCount >= maxPages) {
                    break;
                }

                String sanitizedPage = page;
                if (page.length() > maxPageSize) {
                    sanitizedPage = page.substring(0, maxPageSize);
                }

                sanitizedPages.add(sanitizedPage);
                pageCount++;
            }

            meta.setPages(sanitizedPages);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Kitap sayfasının zararlı içerik içerip içermediğini kontrol eder
     *
     * @param page Sayfa içeriği
     * @return Zararlı mı?
     */
    public static boolean hasUnsafeContent(@NotNull String page) {
        // Null byte kontrolü
        if (page.contains("\0")) {
            return true;
        }

        // Aşırı uzun tekrarlayan karakterler (crash exploit)
        if (hasExcessiveRepeatingChars(page, 100)) {
            return true;
        }

        // Unicode replacement character kontrolü
        if (page.contains("\uFFFD")) {
            return true;
        }

        // JSON command injection kontrolü (basit kontrol)
        String lowerPage = page.toLowerCase();
        if (lowerPage.contains("clickevent") && lowerPage.contains("run_command")) {
            return true;
        }

        return false;
    }

    /**
     * Sayfa içeriğindeki zararlı JSON/Komut yapılarını temizler.
     * 
     * @param content Sayfa içeriği
     * @return Temizlenmiş içerik
     */
    public static String sanitizeContent(String content) {
        if (content == null || content.isEmpty()) return content;
        
        // 1. Kontrol karakterlerini temizle (Newline ve Tab hariç)
        StringBuilder sb = new StringBuilder();
        for (char c : content.toCharArray()) {
            if (c == '\n' || c == '\r' || c == '\t' || (c >= 32 && c <= 65533)) {
                sb.append(c);
            }
        }
        String cleaned = sb.toString();

        // 2. JSON Komutlarını temizle (Survival oyuncuları için basitleştir)
        // Eğer içerik bir JSON objesi gibi görünüyorsa:
        if (cleaned.contains("{") && cleaned.contains("}")) {
            cleaned = cleaned.replaceAll("(?i)\"clickEvent\"\\s*:\\s*\\{[^\\}]+\\}", "\"clickEvent\":{}");
            cleaned = cleaned.replaceAll("(?i)\"hoverEvent\"\\s*:\\s*\\{[^\\}]+\\}", "\"hoverEvent\":{}");
        }
        
        return cleaned;
    }

    /**
     * String'de aşırı tekrarlayan karakterler olup olmadığını kontrol eder
     *
     * @param str String
     * @param threshold Eşik değer
     * @return Aşırı tekrar var mı?
     */
    private static boolean hasExcessiveRepeatingChars(@NotNull String str, int threshold) {
        if (str.length() < threshold) {
            return false;
        }

        int repeatCount = 1;
        char lastChar = str.charAt(0);

        for (int i = 1; i < str.length(); i++) {
            char currentChar = str.charAt(i);
            if (currentChar == lastChar) {
                repeatCount++;
                if (repeatCount >= threshold) {
                    return true;
                }
            } else {
                repeatCount = 1;
                lastChar = currentChar;
            }
        }

        return false;
    }

    /**
     * Kitap meta verilerini temizler (başlık ve yazar hariç sayfa içeriği)
     *
     * @param item Kitap
     * @return Temizlenmiş kitap
     */
    @NotNull
    public static ItemStack clearBookContent(@NotNull ItemStack item) {
        if (!isBook(item) || !item.hasItemMeta()) {
            return item;
        }

        if (!(item.getItemMeta() instanceof BookMeta meta)) {
            return item;
        }
        meta.setPages(new ArrayList<>());
        item.setItemMeta(meta);

        return item;
    }

    /**
     * İki kitabın aynı içeriğe sahip olup olmadığını kontrol eder
     *
     * @param book1 Kitap 1
     * @param book2 Kitap 2
     * @return Aynı mı?
     */
    public static boolean areBooksSimilar(@Nullable ItemStack book1, @Nullable ItemStack book2) {
        if (!isBook(book1) || !isBook(book2)) {
            return false;
        }

        if (!book1.hasItemMeta() || !book2.hasItemMeta()) {
            return !book1.hasItemMeta() && !book2.hasItemMeta();
        }

        if (!(book1.getItemMeta() instanceof BookMeta meta1) || !(book2.getItemMeta() instanceof BookMeta meta2)) {
            return book1.getItemMeta().equals(book2.getItemMeta());
        }

        // Başlık karşılaştır
        if (!java.util.Objects.equals(meta1.getTitle(), meta2.getTitle())) {
            return false;
        }

        // Yazar karşılaştır
        if (!java.util.Objects.equals(meta1.getAuthor(), meta2.getAuthor())) {
            return false;
        }

        // Sayfa sayısı karşılaştır
        if (meta1.getPageCount() != meta2.getPageCount()) {
            return false;
        }

        // Sayfaları karşılaştır
        return meta1.getPages().equals(meta2.getPages());
    }
}
