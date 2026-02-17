package com.atomguard.data;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chunk başına kitap takip sistemi
 * Her chunk'taki kitap sayısını takip eder (crasher/dupe engelleme için)
 * Thread-safe ve ultra-optimize
 */
public class ChunkBookTracker {

    // ChunkKey -> Kitap Sayısı
    private final ConcurrentHashMap<ChunkKey, AtomicInteger> bookCounts;

    // İstatistikler
    private final AtomicInteger totalTrackedChunks;
    private final AtomicInteger totalBooks;

    public ChunkBookTracker() {
        this.bookCounts = new ConcurrentHashMap<>();
        this.totalTrackedChunks = new AtomicInteger(0);
        this.totalBooks = new AtomicInteger(0);
    }

    /**
     * Chunk'a kitap ekler
     *
     * @param chunk Chunk
     * @return Yeni kitap sayısı
     */
    public int addBook(@NotNull Chunk chunk) {
        ChunkKey key = new ChunkKey(chunk);
        AtomicInteger count = bookCounts.computeIfAbsent(key, k -> {
            totalTrackedChunks.incrementAndGet();
            return new AtomicInteger(0);
        });

        totalBooks.incrementAndGet();
        return count.incrementAndGet();
    }

    /**
     * Chunk'tan kitap kaldırır
     *
     * @param chunk Chunk
     * @return Yeni kitap sayısı
     */
    public int removeBook(@NotNull Chunk chunk) {
        ChunkKey key = new ChunkKey(chunk);
        AtomicInteger count = bookCounts.get(key);

        if (count == null) {
            return 0;
        }

        int newCount = Math.max(0, count.decrementAndGet());
        totalBooks.decrementAndGet();

        // Eğer chunk'ta hiç kitap kalmadıysa, chunk kaydını sil
        if (newCount == 0) {
            bookCounts.remove(key);
            totalTrackedChunks.decrementAndGet();
        }

        return newCount;
    }

    /**
     * Chunk'taki kitap sayısını döndürür
     *
     * @param chunk Chunk
     * @return Kitap sayısı
     */
    public int getBookCount(@NotNull Chunk chunk) {
        ChunkKey key = new ChunkKey(chunk);
        AtomicInteger count = bookCounts.get(key);
        return count != null ? count.get() : 0;
    }

    /**
     * Location'daki chunk'ın kitap sayısını döndürür
     *
     * @param location Location
     * @return Kitap sayısı
     */
    public int getBookCount(@NotNull Location location) {
        if (location.getWorld() == null) {
            return 0;
        }
        return getBookCount(location.getChunk());
    }

    /**
     * Chunk'ın kitap limitini aşıp aşmadığını kontrol eder
     *
     * @param chunk Chunk
     * @param maxBooks Maksimum kitap sayısı
     * @return Limit aşıldı mı?
     */
    public boolean isOverLimit(@NotNull Chunk chunk, int maxBooks) {
        return getBookCount(chunk) >= maxBooks;
    }

    /**
     * Chunk'ı temizler (tüm kitap kaydını siler)
     *
     * @param chunk Chunk
     */
    public void clearChunk(@NotNull Chunk chunk) {
        ChunkKey key = new ChunkKey(chunk);
        AtomicInteger count = bookCounts.remove(key);

        if (count != null) {
            totalBooks.addAndGet(-count.get());
            totalTrackedChunks.decrementAndGet();
        }
    }

    /**
     * Tüm kayıtları temizler
     */
    public void clearAll() {
        bookCounts.clear();
        totalTrackedChunks.set(0);
        totalBooks.set(0);
    }

    /**
     * Toplam takip edilen chunk sayısını döndürür
     *
     * @return Chunk sayısı
     */
    public int getTotalTrackedChunks() {
        return totalTrackedChunks.get();
    }

    /**
     * Toplam kitap sayısını döndürür
     *
     * @return Kitap sayısı
     */
    public int getTotalBooks() {
        return totalBooks.get();
    }

    /**
     * Memory optimization - kullanılmayan chunk kayıtlarını temizler
     * Periyodik olarak çağrılmalıdır
     */
    public void cleanup() {
        bookCounts.entrySet().removeIf(entry -> {
            int count = entry.getValue().get();
            if (count <= 0) {
                totalTrackedChunks.decrementAndGet();
                return true;
            }
            return false;
        });
    }

    /**
     * Chunk anahtarı sınıfı
     * World name, x, z koordinatlarını içerir
     * Hafif ve hızlı hashCode/equals implementasyonu
     */
    public static class ChunkKey {
        private final String worldName;
        private final int x;
        private final int z;
        private final int hashCode; // Pre-computed hash

        public ChunkKey(@NotNull Chunk chunk) {
            this.worldName = chunk.getWorld().getName();
            this.x = chunk.getX();
            this.z = chunk.getZ();
            this.hashCode = computeHashCode();
        }

        public ChunkKey(@NotNull String worldName, int x, int z) {
            this.worldName = worldName;
            this.x = x;
            this.z = z;
            this.hashCode = computeHashCode();
        }

        private int computeHashCode() {
            return Objects.hash(worldName, x, z);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ChunkKey other)) return false;

            return this.x == other.x &&
                   this.z == other.z &&
                   this.worldName.equals(other.worldName);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            return String.format("%s[%d,%d]", worldName, x, z);
        }

        public String getWorldName() {
            return worldName;
        }

        public int getX() {
            return x;
        }

        public int getZ() {
            return z;
        }
    }

    @Override
    public String toString() {
        return String.format(
            "ChunkBookTracker{chunks=%d, books=%d}",
            totalTrackedChunks.get(),
            totalBooks.get()
        );
    }
}
