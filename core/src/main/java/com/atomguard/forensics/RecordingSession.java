package com.atomguard.forensics;

import java.util.*;

/**
 * Bir oyuncu icin paket kayit oturumu.
 * Dairesel tampon (circular buffer) kullanarak bellek kullanimini sinirlar.
 * Thread-safe erisim icin synchronized bloklar kullanir.
 *
 * @author AtomGuard Team
 * @version 1.3.0
 */
public class RecordingSession {

    private final UUID playerId;
    private final String reason;
    private final long startTime;
    private final int maxEntries;
    private final Deque<PacketRecording> recordings;

    /**
     * @param playerId   Kaydedilen oyuncunun UUID'si
     * @param reason     Kayit baslama nedeni (ornegin "exploit-tespit", "supheli-trafik")
     * @param maxEntries Tampon kapasitesi (eski kayitlar otomatik silinir)
     */
    public RecordingSession(UUID playerId, String reason, int maxEntries) {
        this.playerId = playerId;
        this.reason = reason;
        this.startTime = System.currentTimeMillis();
        this.maxEntries = maxEntries;
        this.recordings = new ArrayDeque<>(maxEntries);
    }

    /**
     * Yeni bir paket kaydini tampona ekler.
     * Tampon doluysa en eski kaydi siler.
     *
     * @param recording Eklenecek paket kaydi
     */
    public synchronized void addRecording(PacketRecording recording) {
        if (recordings.size() >= maxEntries) {
            recordings.pollFirst();
        }
        recordings.addLast(recording);
    }

    /**
     * Tum kayitlarin degistirilemez kopyasini dondurur.
     *
     * @return Kayitlarin anlık kopyasi
     */
    public synchronized List<PacketRecording> getRecordings() {
        return List.copyOf(recordings);
    }

    // ─── Getters ───

    public UUID getPlayerId() { return playerId; }
    public String getReason() { return reason; }
    public long getStartTime() { return startTime; }
    public long getDurationMs() { return System.currentTimeMillis() - startTime; }
}
