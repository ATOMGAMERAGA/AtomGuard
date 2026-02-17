package com.atomguard.api.storage;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Depolama sağlayıcısı arayüzü.
 * MySQL, SQLite veya dosya tabanlı depolama implementasyonları için soyutlama.
 *
 * @author AtomGuard Team
 * @since 1.0.0
 */
public interface IStorageProvider {

    /**
     * Depolama bağlantısını başlatır.
     *
     * @throws Exception Bağlantı hatası
     */
    void connect() throws Exception;

    /**
     * Depolama bağlantısını kapatır.
     */
    void disconnect();

    /**
     * Bağlantının aktif olup olmadığını kontrol eder.
     *
     * @return Bağlı ise true
     */
    boolean isConnected();

    /**
     * Depolama tipi adını alır.
     *
     * @return Tip adı (örn: "MySQL", "SQLite", "File")
     */
    @NotNull
    String getTypeName();

    // ═══════════════════════════════════════
    // Oyuncu Verileri
    // ═══════════════════════════════════════

    /**
     * Oyuncu verisini asenkron kaydeder.
     *
     * @param uuid Oyuncu UUID
     * @param data Oyuncu veri haritası
     * @return Tamamlandığında CompletableFuture
     */
    CompletableFuture<Void> savePlayerData(@NotNull UUID uuid, @NotNull Map<String, Object> data);

    /**
     * Oyuncu verisini asenkron yükler.
     *
     * @param uuid Oyuncu UUID
     * @return Oyuncu veri haritası
     */
    CompletableFuture<Map<String, Object>> loadPlayerData(@NotNull UUID uuid);

    // ═══════════════════════════════════════
    // İstatistikler
    // ═══════════════════════════════════════

    /**
     * İstatistikleri asenkron kaydeder.
     *
     * @param statistics İstatistik veri haritası
     * @return Tamamlandığında CompletableFuture
     */
    CompletableFuture<Void> saveStatistics(@NotNull Map<String, Object> statistics);

    /**
     * İstatistikleri asenkron yükler.
     *
     * @return İstatistik veri haritası
     */
    CompletableFuture<Map<String, Object>> loadStatistics();

    // ═══════════════════════════════════════
    // Engelli IP'ler
    // ═══════════════════════════════════════

    /**
     * Engelli IP'yi asenkron kaydeder.
     *
     * @param ipAddress IP adresi
     * @param reason Engelleme sebebi
     * @param expiry Son kullanma zamanı (epoch ms), 0 = kalıcı
     * @return Tamamlandığında CompletableFuture
     */
    CompletableFuture<Void> saveBlockedIP(@NotNull String ipAddress, @NotNull String reason, long expiry);

    /**
     * Engelli IP'yi asenkron kaldırır.
     *
     * @param ipAddress IP adresi
     * @return Tamamlandığında CompletableFuture
     */
    CompletableFuture<Void> removeBlockedIP(@NotNull String ipAddress);

    /**
     * Tüm engelli IP'leri asenkron alır.
     *
     * @return Engelli IP seti
     */
    CompletableFuture<Set<String>> getBlockedIPs();
}
