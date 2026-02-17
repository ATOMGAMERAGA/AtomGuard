package com.atomguard.data;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Oyuncu veri takip sınıfı
 * Her oyuncu için paket sayısı, rate limiting vb. verileri tutar
 */
public class PlayerData {

    private final UUID uuid;
    private final String playerName;

    // Paket istatistikleri
    private final AtomicLong totalPacketsReceived;
    private final AtomicLong totalPacketsSent;
    private final AtomicInteger blockedPackets;

    // Rate limiting için
    private final ConcurrentHashMap<String, PacketRateTracker> packetRateTrackers;

    // Son aktivite zamanı
    private volatile long lastActivityTime;

    public PlayerData(@NotNull UUID uuid, @NotNull String playerName) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.totalPacketsReceived = new AtomicLong(0);
        this.totalPacketsSent = new AtomicLong(0);
        this.blockedPackets = new AtomicInteger(0);
        this.packetRateTrackers = new ConcurrentHashMap<>();
        this.lastActivityTime = System.currentTimeMillis();
    }

    /**
     * Alınan paket sayısını artırır
     */
    public void incrementReceivedPackets() {
        totalPacketsReceived.incrementAndGet();
        updateActivity();
    }

    /**
     * Gönderilen paket sayısını artırır
     */
    public void incrementSentPackets() {
        totalPacketsSent.incrementAndGet();
        updateActivity();
    }

    /**
     * Engellenen paket sayısını artırır
     */
    public void incrementBlockedPackets() {
        blockedPackets.incrementAndGet();
        updateActivity();
    }

    /**
     * Son aktivite zamanını günceller
     */
    public void updateActivity() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    /**
     * Belirli bir paket türü için rate tracker alır veya oluşturur
     *
     * @param packetType Paket türü
     * @return Rate tracker
     */
    @NotNull
    public PacketRateTracker getOrCreateRateTracker(@NotNull String packetType) {
        return packetRateTrackers.computeIfAbsent(packetType, k -> new PacketRateTracker());
    }

    /**
     * Oyuncunun paket rate'ini kontrol eder
     *
     * @param packetType Paket türü
     * @param maxPacketsPerSecond Saniyede maksimum paket
     * @return Rate limiti aşıldı mı?
     */
    public boolean isRateLimited(@NotNull String packetType, int maxPacketsPerSecond) {
        PacketRateTracker tracker = getOrCreateRateTracker(packetType);
        return tracker.checkRateLimit(maxPacketsPerSecond);
    }

    // Getters
    public UUID getUuid() {
        return uuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getTotalPacketsReceived() {
        return totalPacketsReceived.get();
    }

    public long getTotalPacketsSent() {
        return totalPacketsSent.get();
    }

    public int getBlockedPackets() {
        return blockedPackets.get();
    }

    public long getLastActivityTime() {
        return lastActivityTime;
    }

    /**
     * İstatistikleri sıfırlar
     */
    public void resetStatistics() {
        totalPacketsReceived.set(0);
        totalPacketsSent.set(0);
        blockedPackets.set(0);
        packetRateTrackers.clear();
    }

    /**
     * Paket rate takip sınıfı
     * Sliding window algoritması kullanır
     */
    public static class PacketRateTracker {
        private final ConcurrentHashMap<Long, AtomicInteger> packetsPerSecond;
        private static final long WINDOW_SIZE_MS = 1000; // 1 saniye

        public PacketRateTracker() {
            this.packetsPerSecond = new ConcurrentHashMap<>();
        }

        /**
         * Paket kaydeder ve rate limiti kontrol eder
         *
         * @param maxRate Maksimum rate
         * @return Limit aşıldı mı?
         */
        public boolean checkRateLimit(int maxRate) {
            long currentSecond = System.currentTimeMillis() / WINDOW_SIZE_MS;

            // Eski window'ları temizle
            packetsPerSecond.entrySet().removeIf(entry ->
                entry.getKey() < currentSecond - 1
            );

            // Mevcut saniye için paket sayısını artır
            AtomicInteger count = packetsPerSecond.computeIfAbsent(
                currentSecond,
                k -> new AtomicInteger(0)
            );

            int newCount = count.incrementAndGet();

            // Rate limiti kontrol et
            return newCount > maxRate;
        }

        /**
         * Geçerli saniyedeki paket sayısını döndürür
         *
         * @return Paket sayısı
         */
        public int getCurrentRate() {
            long currentSecond = System.currentTimeMillis() / WINDOW_SIZE_MS;
            AtomicInteger count = packetsPerSecond.get(currentSecond);
            return count != null ? count.get() : 0;
        }

        /**
         * Tracker'ı sıfırlar
         */
        public void reset() {
            packetsPerSecond.clear();
        }
    }

    @Override
    public String toString() {
        return String.format(
            "PlayerData{uuid=%s, name=%s, received=%d, sent=%d, blocked=%d}",
            uuid, playerName,
            totalPacketsReceived.get(),
            totalPacketsSent.get(),
            blockedPackets.get()
        );
    }
}
