package com.atomguard.forensics;

import com.atomguard.AtomGuard;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Supheli oyuncular icin paket kayit oturum yoneticisi.
 * Aktif kayit oturumlarini yonetir, paketleri kaydeder ve
 * diske JSON formatinda disari aktarir.
 *
 * @author AtomGuard Team
 * @version 1.3.0
 */
public class PacketRecorder {

    private final AtomGuard plugin;
    private final Map<UUID, RecordingSession> activeSessions = new ConcurrentHashMap<>();
    private final int bufferSize;
    private final int maxConcurrent;

    public PacketRecorder(AtomGuard plugin) {
        this.plugin = plugin;
        // ~20 paket/saniye tahmini ile tampon suresi hesabi
        this.bufferSize = plugin.getConfig()
                .getInt("forensik.paket-kaydi.tampon-suresi-saniye", 30) * 20;
        this.maxConcurrent = plugin.getConfig()
                .getInt("forensik.paket-kaydi.max-eszamanli-kayit", 10);
    }

    /**
     * Belirtilen oyuncu icin paket kaydini baslatir.
     *
     * @param playerId Kaydedilecek oyuncunun UUID'si
     * @param reason   Kayit nedeni
     * @return Kayit baslatildiysa true, eszamanli limit asildiysa false
     */
    public boolean startRecording(UUID playerId, String reason) {
        if (activeSessions.size() >= maxConcurrent) return false;
        activeSessions.putIfAbsent(playerId,
                new RecordingSession(playerId, reason, bufferSize));
        return true;
    }

    /**
     * Belirtilen oyuncu icin kaydi durdurur ve oturumu siler.
     *
     * @param playerId Oyuncu UUID'si
     */
    public void stopRecording(UUID playerId) {
        activeSessions.remove(playerId);
    }

    /**
     * Belirtilen oyuncunun kaydinin aktif olup olmadigini kontrol eder.
     *
     * @param playerId Oyuncu UUID'si
     * @return Kayit aktifse true
     */
    public boolean isRecording(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    /**
     * Aktif bir oturuma paket kaydeder.
     * Oyuncu icin aktif oturum yoksa islem yapilmaz.
     *
     * @param playerId   Oyuncu UUID'si
     * @param packetType Paket tipi adi
     * @param incoming   Gelen paket mi (true) yoksa giden mi (false)
     * @param dataSize   Paket veri boyutu (byte)
     * @param summary    Kisa ozet bilgisi
     */
    public void recordPacket(UUID playerId, String packetType, boolean incoming,
                             int dataSize, String summary) {
        RecordingSession session = activeSessions.get(playerId);
        if (session != null) {
            session.addRecording(new PacketRecording(
                    System.currentTimeMillis(), packetType, incoming, dataSize, summary));
        }
    }

    /**
     * Belirtilen oyuncunun aktif oturumunu dondurur.
     *
     * @param playerId Oyuncu UUID'si
     * @return Oturum varsa Optional ile sarili, yoksa empty
     */
    public Optional<RecordingSession> getSession(UUID playerId) {
        return Optional.ofNullable(activeSessions.get(playerId));
    }

    /**
     * Aktif bir kayit oturumunu JSON satirlari olarak dosyaya aktarir.
     *
     * @param playerId   Oyuncu UUID'si
     * @param outputFile Hedef dosya
     * @throws IOException Dosya yazma hatasi
     */
    public void exportRecording(UUID playerId, File outputFile) throws IOException {
        RecordingSession session = activeSessions.get(playerId);
        if (session == null) return;

        Files.createDirectories(outputFile.getParentFile().toPath());

        try (BufferedWriter writer = Files.newBufferedWriter(
                outputFile.toPath(), StandardCharsets.UTF_8)) {
            for (PacketRecording rec : session.getRecordings()) {
                writer.write(String.format(
                        "{\"ts\":%d,\"type\":\"%s\",\"in\":%b,\"size\":%d,\"summary\":\"%s\"}%n",
                        rec.timestamp(), rec.packetType(), rec.incoming(),
                        rec.dataSize(), rec.summary()));
            }
        }
    }

    /**
     * Temizlik islemi. Cevrimdisi oyuncularin oturumlarini kaldirmak icin
     * genisletilebilir.
     */
    public void cleanup() {
        // Cevrimdisi oyuncularin oturumlari burada kaldirılabilir
    }

    // ─── Getters ───

    public int getActiveSessionCount() { return activeSessions.size(); }
    public int getMaxConcurrent() { return maxConcurrent; }
}
