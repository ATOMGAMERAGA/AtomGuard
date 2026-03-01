package com.atomguard.module;

import com.atomguard.AtomGuard;
import com.atomguard.data.ChunkBookTracker;
import com.atomguard.util.BookUtils;
import org.bukkit.Chunk;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Çok Fazla Kitap Modülü
 *
 * Chunk başına düşen kitap sayısını kontrol eder ve limiti aşan kitapları engeller.
 * Bu modül, kitap exploit'lerini ve chunk-based dupe'ları önlemek için tasarlanmıştır.
 *
 * Özellikler:
 * - Chunk başına maksimum kitap sayısı kontrolü
 * - Sayfa uzunluğu ve toplam boyut kontrolü
 * - Kitap spawn tracking
 * - Zararlı kitap içeriklerinin engellenmesi
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class TooManyBooksModule extends AbstractModule implements Listener {

    private ChunkBookTracker bookTracker;

    // Config cache
    private int maxBooksPerChunk;
    private int maxPageLength;
    private int maxTotalSize;
    private String action;

    /**
     * TooManyBooksModule constructor
     *
     * @param plugin Ana plugin instance
     */
    public TooManyBooksModule(@NotNull AtomGuard plugin) {
        super(plugin, "cok-fazla-kitap", "Chunk başına kitap sayısını kontrol eder");
    }

    @Override

    public void onEnable() {
        super.onEnable();

        // Config değerlerini yükle
        loadConfig();

        // Book tracker başlat
        this.bookTracker = new ChunkBookTracker();

        debug("Modül aktifleştirildi. Max kitap/chunk: " + maxBooksPerChunk);
    }

    @Override

    public void onDisable() {
        super.onDisable();

        // Tracker'ı temizle
        if (bookTracker != null) {
            bookTracker.clearAll();
        }

        // Event listener'ı kaldır
        ItemSpawnEvent.getHandlerList().unregister(this);
        ChunkLoadEvent.getHandlerList().unregister(this);

        debug("Modül devre dışı bırakıldı.");
    }

    /**
     * Config değerlerini yükler
     */
    private void loadConfig() {
        this.maxBooksPerChunk = getConfigInt("chunk-basina-max-kitap", 100);
        this.maxPageLength = getConfigInt("max-sayfa-uzunlugu", 256);
        this.maxTotalSize = getConfigInt("max-toplam-boyut", 102400); // 100KB
        this.action = getConfigString("eylem", "cancel");

        debug("Config yüklendi: maxBooks=" + maxBooksPerChunk +
              ", maxPageLength=" + maxPageLength +
              ", maxTotalSize=" + maxTotalSize);
    }

    /**
     * Item spawn olayını dinler
     * Kitap spawn'larını kontrol eder
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!isEnabled()) {
            return;
        }

        Item item = event.getEntity();
        ItemStack itemStack = item.getItemStack();

        // Kitap mı kontrol et
        if (!BookUtils.isBook(itemStack)) {
            return;
        }

        Chunk chunk = item.getLocation().getChunk();

        // Chunk'taki kitap sayısını kontrol et
        if (bookTracker.isOverLimit(chunk, maxBooksPerChunk)) {
            // Limit aşıldı
            incrementBlockedCount();
            logExploit("SYSTEM",
                String.format("Chunk [%d,%d] kitap limiti aşıldı! Mevcut: %d, Limit: %d",
                    chunk.getX(), chunk.getZ(),
                    bookTracker.getBookCount(chunk),
                    maxBooksPerChunk));

            if ("cancel".equalsIgnoreCase(action)) {
                event.setCancelled(true);
                debug("Kitap spawn'ı iptal edildi (limit aşımı)");
            } else if ("remove".equalsIgnoreCase(action)) {
                event.getEntity().remove();
                debug("Kitap kaldırıldı (limit aşımı)");
            }
            return;
        }

        // Kitap içeriğini kontrol et
        if (!isBookSafe(itemStack)) {
            incrementBlockedCount();
            logExploit("SYSTEM",
                String.format("Zararlı kitap içeriği tespit edildi! Chunk: [%d,%d]",
                    chunk.getX(), chunk.getZ()));

            event.setCancelled(true);
            debug("Kitap spawn'ı iptal edildi (zararlı içerik)");
            return;
        }

        // Kitap sayısını artır
        int newCount = bookTracker.addBook(chunk);
        debug("Kitap eklendi. Chunk: [" + chunk.getX() + "," + chunk.getZ() + "], Toplam: " + newCount);
    }

    /**
     * Chunk yüklenme olayını dinler
     * Chunk'taki mevcut kitapları sayar
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!isEnabled()) {
            return;
        }

        Chunk chunk = event.getChunk();

        // Chunk'taki tüm item entity'leri say
        int bookCount = 0;
        for (org.bukkit.entity.Entity entity : chunk.getEntities()) {
            if (entity instanceof Item item) {
                if (BookUtils.isBook(item.getItemStack())) {
                    bookCount++;
                }
            }
        }

        // Tracker'ı güncelle
        if (bookCount > 0) {
            // Önce temizle, sonra doğru sayıyı ekle
            bookTracker.clearChunk(chunk);
            for (int i = 0; i < bookCount; i++) {
                bookTracker.addBook(chunk);
            }

            debug("Chunk yüklendi: [" + chunk.getX() + "," + chunk.getZ() + "], Kitap sayısı: " + bookCount);

            // Limit kontrolü
            if (bookCount >= maxBooksPerChunk) {
                warning("Chunk [" + chunk.getX() + "," + chunk.getZ() + "] kitap limiti aşıldı! Sayı: " + bookCount);
            }
        }
    }

    /**
     * Kitabın güvenli olup olmadığını kontrol eder
     *
     * @param book Kitap item
     * @return Güvenli ise true
     */
    private boolean isBookSafe(@NotNull ItemStack book) {
        // BookUtils kullanarak güvenlik kontrolü
        if (!BookUtils.isBookSafe(book, 64, 100, maxPageLength, maxTotalSize)) {
            return false;
        }

        // Toplam boyut kontrolü
        int bookSize = BookUtils.calculateBookSize(book);
        if (bookSize > maxTotalSize) {
            debug("Kitap çok büyük: " + bookSize + " bytes (limit: " + maxTotalSize + ")");
            return false;
        }

        return true;
    }

    /**
     * Modül istatistiklerini döndürür
     *
     * @return İstatistik string'i
     */
    public String getStatistics() {
        if (bookTracker == null) {
            return "Tracker başlatılmamış";
        }

        return String.format("Takip edilen chunk: %d, Toplam kitap: %d, Engellenen: %d",
            bookTracker.getTotalTrackedChunks(),
            bookTracker.getTotalBooks(),
            getBlockedCount());
    }

    /**
     * Book tracker'ı döndürür
     *
     * @return ChunkBookTracker instance
     */
    @NotNull
    public ChunkBookTracker getBookTracker() {
        return bookTracker;
    }
}
