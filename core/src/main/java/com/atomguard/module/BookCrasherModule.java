package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.atomguard.util.BookUtils;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEditBook;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.List;

/**
 * Kitap Crash Modülü
 *
 * Kitap paketlerini kontrol eder ve crash exploit'lerini önler.
 * BookUtils ile kitap validasyonu yapar.
 *
 * Özellikler:
 * - Maksimum başlık uzunluğu kontrolü
 * - Maksimum sayfa sayısı kontrolü
 * - Maksimum sayfa boyutu kontrolü
 * - Maksimum toplam kitap boyutu kontrolü
 * - WrapperPlayClientEditBook kullanımı
 * - Unicode ve JSON sanitasyonu
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class BookCrasherModule extends AbstractModule {

    private PacketListenerAbstract listener;

    // Config cache
    private int maxTitleLength;
    private int maxPageCount;
    private int maxPageSize;
    private int maxTotalBookSize;

    /**
     * BookCrasherModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public BookCrasherModule(@NotNull AtomGuard plugin) {
        super(plugin, "kitap-crash", "Kitap crash exploit kontrolü");
    }

    @Override

    public void onEnable() {
        super.onEnable();

        // Config değerlerini yükle
        loadConfig();

        // PacketEvents listener'ı oluştur ve kaydet
        listener = new PacketListenerAbstract(PacketListenerPriority.NORMAL) {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                handlePacketReceive(event);
            }
        };

        com.github.retrooper.packetevents.PacketEvents.getAPI()
            .getEventManager()
            .registerListener(listener);

        debug("Modül aktifleştirildi. Max sayfa: " + maxPageCount +
              ", Max boyut: " + maxTotalBookSize);
    }

    @Override

    public void onDisable() {
        super.onDisable();

        // PacketEvents listener'ı kaldır
        if (listener != null) {
            com.github.retrooper.packetevents.PacketEvents.getAPI()
                .getEventManager()
                .unregisterListener(listener);
        }

        debug("Modül devre dışı bırakıldı.");
    }

    /**
     * Config değerlerini yükler
     */
    private void loadConfig() {
        this.maxTitleLength = getConfigInt("max-baslik-uzunlugu", 32);
        this.maxPageCount = getConfigInt("max-sayfa-sayisi", 100);
        this.maxPageSize = getConfigInt("max-sayfa-boyutu", 256);
        this.maxTotalBookSize = getConfigInt("max-toplam-kitap-boyutu", 102400); // 100KB

        debug("Config yüklendi: maxTitle=" + maxTitleLength +
              ", maxPages=" + maxPageCount +
              ", maxPageSize=" + maxPageSize +
              ", maxTotal=" + maxTotalBookSize);
    }

    /**
     * Paket alındığında çağrılır
     */
    private void handlePacketReceive(PacketReceiveEvent event) {
        if (!isEnabled()) {
            return;
        }

        // EditBook paketini kontrol et
        if (event.getPacketType() != PacketType.Play.Client.EDIT_BOOK) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        try {
            WrapperPlayClientEditBook packet = new WrapperPlayClientEditBook(event);
            List<String> pages = packet.getPages();
            String title = packet.getTitle();

            // 1. Sayfa Sayısı Kontrolü
            if (pages.size() > maxPageCount) {
                cancelBook(event, player, "Çok fazla sayfa: " + pages.size());
                return;
            }

            // 2. Başlık Kontrolü
            if (title != null && title.length() > maxTitleLength) {
                cancelBook(event, player, "Çok uzun başlık: " + title.length());
                return;
            }

            // 3. İçerik ve Boyut Kontrolü
            int currentTotalSize = 0;
            for (String page : pages) {
                // CR-03: Byte-bazlı boyut kontrolü (Unicode exploit önleme)
                int byteSize = page.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if (byteSize > maxPageSize) {
                    cancelBook(event, player, "Çok uzun sayfa (Byte): " + byteSize);
                    return;
                }
                
                if (BookUtils.hasUnsafeContent(page)) {
                    cancelBook(event, player, "Zararlı sayfa içeriği tespit edildi!");
                    return;
                }

                // CR-03: JSON Derinlik ve Yapı Kontrolü
                if (page.trim().startsWith("{") || page.trim().startsWith("[")) {
                     // Basit JSON derinlik kontrolü (Regex ile yaklaşık)
                     int depth = 0;
                     int maxDepth = 0;
                     for (char c : page.toCharArray()) {
                         if (c == '{' || c == '[') {
                             depth++;
                             maxDepth = Math.max(maxDepth, depth);
                         } else if (c == '}' || c == ']') {
                             depth--;
                         }
                     }
                     if (maxDepth > 5) { // Config'den alınabilir: json-derinlik-limiti
                         cancelBook(event, player, "Çok derin JSON yapısı: " + maxDepth);
                         return;
                     }
                     
                     // Tehlikeli JSON anahtarları
                     if (page.contains("\"translate\"") || page.contains("\"nbt\"") || page.contains("\"selector\"")) {
                          cancelBook(event, player, "Yasaklı JSON komponenti");
                          return;
                     }
                }
                
                currentTotalSize += byteSize;
            }

            if (currentTotalSize > maxTotalBookSize) {
                cancelBook(event, player, "Çok büyük toplam kitap boyutu: " + currentTotalSize);
                return;
            }

            // 4. Survival oyuncuları için JSON sanitasyonu (Opsiyonel — eğer yetkisi yoksa)
            if (!player.isOp()) {
                // Not: PacketEvents'te paket verisini manipüle etmek için event.setCancelled(true) 
                // ve yeni paket göndermek veya packet.setPages() kullanmak gerekir.
                // Şimdilik sadece tehlikeliyse engelliyoruz.
            }

        } catch (Exception e) {
            error("EditBook paketi işlenirken hata: " + e.getMessage());
        }
    }

    private void cancelBook(PacketReceiveEvent event, Player player, String reason) {
        incrementBlockedCount();
        logExploit(player.getName(), "Zararlı Kitap: " + reason);
        event.setCancelled(true);
        player.closeInventory();
        player.sendMessage(plugin.getMessageManager().getMessage("engelleme.kitap-crash"));
    }

    /**
     * Kitabın güvenli olup olmadığını manuel kontrol eder
     */
    public boolean isBookSafe(@NotNull org.bukkit.inventory.ItemStack book) {
        return BookUtils.isBookSafe(book, maxTitleLength, maxPageCount, maxPageSize, maxTotalBookSize);
    }

    /**
     * Kitabı temizler ve güvenli hale getirir
     */
    @NotNull
    public org.bukkit.inventory.ItemStack sanitizeBook(@NotNull org.bukkit.inventory.ItemStack book) {
        return BookUtils.sanitizeBook(book, maxTitleLength, maxPageCount, maxPageSize);
    }

    /**
     * Modül istatistiklerini döndürür
     */
    public String getStatistics() {
        return String.format("Engellenen zararlı kitap: %d", getBlockedCount());
    }
}
