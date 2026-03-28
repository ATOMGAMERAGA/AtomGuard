package com.atomguard.velocity.module.verification.storage;

import com.atomguard.velocity.AtomGuardVelocity;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Limbo doğrulamasından geçmiş oyuncuları IP+UUID çifti olarak saklar.
 *
 * <p>Bir kez doğrulanınca sonraki girişlerde bypass (Sonar prensibi).
 * Caffeine cache (50k, 30dk TTL) + SQLite/MySQL backend.
 * Süresi {@code expiryDays} gün sonra dolan kayıtlar temizlenir.
 */
public class VerifiedPlayerStore {

    private final AtomGuardVelocity plugin;
    private final int expiryDays;

    /** IP → verified flag hızlı lookup. 30 dakika TTL yeterli; DB her zaman kaynak. */
    private final Cache<String, Boolean> ipCache;

    public VerifiedPlayerStore(AtomGuardVelocity plugin, int expiryDays) {
        this.plugin = plugin;
        this.expiryDays = expiryDays;
        this.ipCache = Caffeine.newBuilder()
                .maximumSize(50_000)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .build();
        initTable();
    }

    // ───────────────────────────── Init ─────────────────────────────

    private void initTable() {
        var storage = plugin.getStorageProvider();
        if (storage == null || !storage.isConnected()) return;
        var ds = storage.getDataSource();
        if (ds == null) return;
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS ag_limbo_verified (" +
                "ip VARCHAR(45) NOT NULL, " +
                "uuid VARCHAR(36), " +
                "username VARCHAR(16), " +
                "verified_at BIGINT NOT NULL, " +
                "expires_at BIGINT NOT NULL, " +
                "PRIMARY KEY (ip))");
            stmt.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_limbo_expires ON ag_limbo_verified (expires_at)");
        } catch (Exception e) {
            plugin.getSlf4jLogger().error("ag_limbo_verified tablosu oluşturulamadı: {}", e.getMessage());
        }
    }

    // ───────────────────────────── API ─────────────────────────────

    /** IP doğrulanmış mı? Önce cache, sonra DB kontrol. */
    public boolean isVerified(String ip) {
        Boolean cached = ipCache.getIfPresent(ip);
        if (cached != null) return cached;

        boolean result = checkDB(ip);
        if (result) ipCache.put(ip, true);
        return result;
    }

    /** IP'yi doğrulanmış olarak kaydet (cache + async DB). */
    public void markVerified(String ip, UUID uuid, String username) {
        ipCache.put(ip, true);

        var storage = plugin.getStorageProvider();
        if (storage == null || !storage.isConnected()) return;
        var ds = storage.getDataSource();
        if (ds == null) return;

        long now = System.currentTimeMillis();
        long expires = now + (expiryDays * 86_400_000L);

        storage.getExecutor().execute(() -> {
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "REPLACE INTO ag_limbo_verified (ip, uuid, username, verified_at, expires_at) VALUES (?,?,?,?,?)")) {
                ps.setString(1, ip);
                ps.setString(2, uuid != null ? uuid.toString() : null);
                ps.setString(3, username);
                ps.setLong(4, now);
                ps.setLong(5, expires);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getSlf4jLogger().error("Limbo verified kaydedilemedi: {}", e.getMessage());
            }
        });
    }

    /** Başlangıçta DB'den tüm geçerli kayıtları cache'e yükle. */
    public void loadIntoCache() {
        var storage = plugin.getStorageProvider();
        if (storage == null || !storage.isConnected()) return;
        var ds = storage.getDataSource();
        if (ds == null) return;

        storage.getExecutor().execute(() -> {
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT ip FROM ag_limbo_verified WHERE expires_at > ?")) {
                ps.setLong(1, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        ipCache.put(rs.getString("ip"), true);
                        count++;
                    }
                    plugin.getSlf4jLogger().info("Limbo Doğrulama: {} kayıtlı verified oyuncu yüklendi.", count);
                }
            } catch (Exception e) {
                plugin.getSlf4jLogger().error("Limbo verified yüklenemedi: {}", e.getMessage());
            }
        });
    }

    /** Süresi dolmuş kayıtları DB'den temizle. */
    public void cleanup() {
        var storage = plugin.getStorageProvider();
        if (storage == null || !storage.isConnected()) return;
        var ds = storage.getDataSource();
        if (ds == null) return;

        storage.getExecutor().execute(() -> {
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM ag_limbo_verified WHERE expires_at < ?")) {
                ps.setLong(1, System.currentTimeMillis());
                int deleted = ps.executeUpdate();
                if (deleted > 0) {
                    plugin.getSlf4jLogger().debug("Limbo: {} süresi dolmuş verified kayıt silindi.", deleted);
                }
            } catch (Exception e) {
                plugin.getSlf4jLogger().warn("Limbo cleanup hatası: {}", e.getMessage());
            }
        });
    }

    /** Cache'den IP'yi çıkar (test/revoke için). */
    public void invalidate(String ip) {
        ipCache.invalidate(ip);
    }

    public long getCacheSize() {
        return ipCache.estimatedSize();
    }

    // ───────────────────────────── Internal ─────────────────────────────

    private boolean checkDB(String ip) {
        var storage = plugin.getStorageProvider();
        if (storage == null || !storage.isConnected()) return false;
        var ds = storage.getDataSource();
        if (ds == null) return false;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT 1 FROM ag_limbo_verified WHERE ip=? AND expires_at > ?")) {
            ps.setString(1, ip);
            ps.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getSlf4jLogger().warn("Limbo verified DB kontrol hatası: {}", e.getMessage());
            return false;
        }
    }
}
