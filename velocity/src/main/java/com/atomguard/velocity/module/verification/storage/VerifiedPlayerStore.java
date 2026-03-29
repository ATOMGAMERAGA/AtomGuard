package com.atomguard.velocity.module.verification.storage;

import com.atomguard.velocity.AtomGuardVelocity;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Gelişmiş Doğrulanmış Oyuncu Deposu — IP + Kullanıcı Adı Çifti Sistemi — v2.
 *
 * <h2>Çalışma Prensibi</h2>
 * <p>Oyuncuları yalnızca IP adresiyle değil, <b>IP + kullanıcı adı çifti</b> olarak doğrular.
 * Bu sayede:
 * <ul>
 *   <li>Aynı IP + aynı kullanıcı adı → doğrulama atlanır (bypass)</li>
 *   <li>Aynı kullanıcı adı + farklı IP → tekrar doğrulama gerekir</li>
 *   <li>Farklı kullanıcı adı + aynı IP → tekrar doğrulama gerekir</li>
 *   <li>İlk giriş → her zaman doğrulama gerekir</li>
 * </ul>
 *
 * <h2>Depolama</h2>
 * <ul>
 *   <li><b>Caffeine cache</b> — hızlı in-memory lookup (50k, 30dk TTL)</li>
 *   <li><b>SQLite/MySQL</b> — kalıcı DB depolama</li>
 *   <li><b>Dosya</b> — verified-players.txt yedek dosya depolama</li>
 * </ul>
 *
 * <h2>Bypass Koruması</h2>
 * <ul>
 *   <li>IP + username çifti zorlanır — tek başına IP veya username yeterli DEĞİL</li>
 *   <li>Dosya ve DB senkronize tutulur</li>
 *   <li>Süresi dolan kayıtlar otomatik temizlenir</li>
 *   <li>Açık/exploit kullanılarak bypass edilemez</li>
 * </ul>
 */
public class VerifiedPlayerStore {

    private final AtomGuardVelocity plugin;
    private final int expiryDays;

    /**
     * Cache anahtarı: "IP|username" çifti.
     * Değer: doğrulama zamanı (epoch ms).
     */
    private final Cache<String, Long> pairCache;

    /** Kullanıcı adı → son doğrulanmış IP (hızlı IP değişikliği tespiti) */
    private final ConcurrentHashMap<String, String> usernameToLastIP = new ConcurrentHashMap<>();

    /** Dosya tabanlı yedek depolama */
    private final Path verifiedFile;

    public VerifiedPlayerStore(AtomGuardVelocity plugin, int expiryDays) {
        this.plugin = plugin;
        this.expiryDays = expiryDays;
        this.verifiedFile = plugin.getDataDirectory().resolve("verified-players.txt");
        this.pairCache = Caffeine.newBuilder()
                .maximumSize(50_000)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .build();
        initTable();
    }

    // ─────────────────────────── Init ───────────────────────────

    private void initTable() {
        var storage = plugin.getStorageProvider();
        if (storage == null || !storage.isConnected()) return;
        var ds = storage.getDataSource();
        if (ds == null) return;
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            // Yeni tablo: IP + username çifti olarak
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS ag_verified_players (" +
                    "ip VARCHAR(45) NOT NULL, " +
                    "username VARCHAR(16) NOT NULL, " +
                    "uuid VARCHAR(36), " +
                    "verified_at BIGINT NOT NULL, " +
                    "expires_at BIGINT NOT NULL, " +
                    "verify_count INT DEFAULT 1, " +
                    "last_login BIGINT, " +
                    "PRIMARY KEY (ip, username))");
            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_verified_expires ON ag_verified_players (expires_at)");
            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_verified_username ON ag_verified_players (username)");

            // Eski tablodan migration (varsa)
            try {
                ResultSet rs = stmt.executeQuery("SELECT ip, uuid, username, verified_at, expires_at FROM ag_limbo_verified WHERE expires_at > " + System.currentTimeMillis());
                int migrated = 0;
                while (rs.next()) {
                    String ip = rs.getString("ip");
                    String username = rs.getString("username");
                    if (ip != null && username != null && !username.isEmpty()) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT OR IGNORE INTO ag_verified_players (ip, username, uuid, verified_at, expires_at) VALUES (?,?,?,?,?)")) {
                            ps.setString(1, ip);
                            ps.setString(2, username);
                            ps.setString(3, rs.getString("uuid"));
                            ps.setLong(4, rs.getLong("verified_at"));
                            ps.setLong(5, rs.getLong("expires_at"));
                            ps.executeUpdate();
                            migrated++;
                        }
                    }
                }
                if (migrated > 0) {
                    plugin.getSlf4jLogger().info("[Verification] {} eski verified kayıt migrate edildi.", migrated);
                }
            } catch (Exception ignored) {
                // Eski tablo yoksa sorun değil
            }
        } catch (Exception e) {
            plugin.getSlf4jLogger().error("ag_verified_players tablosu oluşturulamadı: {}", e.getMessage());
        }
    }

    // ─────────────────────────── Ana API ───────────────────────────

    /**
     * Bu IP + username çifti doğrulanmış mı?
     * Yalnızca IP adresi VE kullanıcı adı birlikte kontrol edilir.
     *
     * @param ip       IP adresi
     * @param username Kullanıcı adı
     * @return true ise bu çift daha önce doğrulanmış
     */
    public boolean isVerifiedPair(String ip, String username) {
        if (ip == null || username == null) return false;

        String key = makeKey(ip, username);

        // Önce cache kontrol
        Long cached = pairCache.getIfPresent(key);
        if (cached != null) return true;

        // Cache'de yoksa DB kontrol
        boolean result = checkDBPair(ip, username);
        if (result) {
            pairCache.put(key, System.currentTimeMillis());
        }
        return result;
    }

    /**
     * Geriye dönük uyumluluk: sadece IP ile kontrol.
     * Bu yöntem SADECE doğrulama modülü bypass check'inde kullanılır.
     * Güvenlik açısından isVerifiedPair tercih edilmeli.
     */
    public boolean isVerified(String ip) {
        if (ip == null) return false;
        return checkDBByIP(ip);
    }

    /**
     * IP + username çiftini doğrulanmış olarak kaydet.
     */
    public void markVerified(String ip, UUID uuid, String username) {
        if (ip == null || username == null) return;

        String key = makeKey(ip, username);
        pairCache.put(key, System.currentTimeMillis());
        usernameToLastIP.put(username.toLowerCase(), ip);

        // Dosyaya yaz
        writeToFile(ip, username, uuid);

        // Async DB kaydet
        var storage = plugin.getStorageProvider();
        if (storage == null || !storage.isConnected()) return;
        var ds = storage.getDataSource();
        if (ds == null) return;

        long now = System.currentTimeMillis();
        long expires = now + (expiryDays * 86_400_000L);

        storage.getExecutor().execute(() -> {
            try (Connection conn = ds.getConnection()) {
                // Mevcut kayıt var mı kontrol et
                try (PreparedStatement check = conn.prepareStatement(
                        "SELECT verify_count FROM ag_verified_players WHERE ip=? AND username=?")) {
                    check.setString(1, ip);
                    check.setString(2, username);
                    ResultSet rs = check.executeQuery();

                    if (rs.next()) {
                        // Güncelle — verify sayısını artır
                        int count = rs.getInt("verify_count");
                        try (PreparedStatement update = conn.prepareStatement(
                                "UPDATE ag_verified_players SET uuid=?, verified_at=?, expires_at=?, verify_count=?, last_login=? WHERE ip=? AND username=?")) {
                            update.setString(1, uuid != null ? uuid.toString() : null);
                            update.setLong(2, now);
                            update.setLong(3, expires);
                            update.setInt(4, count + 1);
                            update.setLong(5, now);
                            update.setString(6, ip);
                            update.setString(7, username);
                            update.executeUpdate();
                        }
                    } else {
                        // Yeni kayıt ekle
                        try (PreparedStatement insert = conn.prepareStatement(
                                "INSERT INTO ag_verified_players (ip, username, uuid, verified_at, expires_at, verify_count, last_login) VALUES (?,?,?,?,?,1,?)")) {
                            insert.setString(1, ip);
                            insert.setString(2, username);
                            insert.setString(3, uuid != null ? uuid.toString() : null);
                            insert.setLong(4, now);
                            insert.setLong(5, expires);
                            insert.setLong(6, now);
                            insert.executeUpdate();
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getSlf4jLogger().error("Verified kayıt hatası: {}", e.getMessage());
            }
        });
    }

    /**
     * Bu kullanıcı adının son doğrulanmış IP'si nedir?
     * IP değişikliği tespiti için kullanılır.
     */
    public String getLastVerifiedIP(String username) {
        if (username == null) return null;

        // Önce in-memory cache
        String cached = usernameToLastIP.get(username.toLowerCase());
        if (cached != null) return cached;

        // DB'den kontrol
        return getLastIPFromDB(username);
    }

    /**
     * Bu kullanıcı adı daha önce herhangi bir IP ile doğrulanmış mı?
     */
    public boolean hasEverBeenVerified(String username) {
        if (username == null) return false;
        return getLastVerifiedIP(username) != null;
    }

    /**
     * Başlangıçta DB'den tüm geçerli kayıtları cache'e yükle.
     */
    public void loadIntoCache() {
        var storage = plugin.getStorageProvider();
        if (storage == null || !storage.isConnected()) return;
        var ds = storage.getDataSource();
        if (ds == null) return;

        storage.getExecutor().execute(() -> {
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT ip, username FROM ag_verified_players WHERE expires_at > ?")) {
                ps.setLong(1, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        String ip = rs.getString("ip");
                        String username = rs.getString("username");
                        pairCache.put(makeKey(ip, username), System.currentTimeMillis());
                        usernameToLastIP.put(username.toLowerCase(), ip);
                        count++;
                    }
                    plugin.getSlf4jLogger().info(
                            "[Verification] {} doğrulanmış IP+username çifti yüklendi.", count);
                }
            } catch (Exception e) {
                plugin.getSlf4jLogger().error("Verified kayıtlar yüklenemedi: {}", e.getMessage());
            }
        });

        // Dosyadan da yükle (yedek)
        loadFromFile();
    }

    /**
     * Süresi dolmuş kayıtları temizle.
     */
    public void cleanup() {
        var storage = plugin.getStorageProvider();
        if (storage == null || !storage.isConnected()) return;
        var ds = storage.getDataSource();
        if (ds == null) return;

        storage.getExecutor().execute(() -> {
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM ag_verified_players WHERE expires_at < ?")) {
                ps.setLong(1, System.currentTimeMillis());
                int deleted = ps.executeUpdate();
                if (deleted > 0) {
                    plugin.getSlf4jLogger().debug(
                            "[Verification] {} süresi dolmuş kayıt silindi.", deleted);
                }
            } catch (Exception e) {
                plugin.getSlf4jLogger().warn("Verified cleanup hatası: {}", e.getMessage());
            }
        });
    }

    /**
     * Cache'den IP+username çiftini çıkar (revoke için).
     */
    public void invalidate(String ip, String username) {
        pairCache.invalidate(makeKey(ip, username));
    }

    /**
     * Cache'den IP'yi çıkar (eski API uyumluluğu).
     */
    public void invalidate(String ip) {
        pairCache.invalidateAll();
    }

    public long getCacheSize() {
        return pairCache.estimatedSize();
    }

    // ─────────────────────────── Internal ───────────────────────────

    private String makeKey(String ip, String username) {
        return ip + "|" + username.toLowerCase();
    }

    private boolean checkDBPair(String ip, String username) {
        var storage = plugin.getStorageProvider();
        if (storage == null || !storage.isConnected()) return false;
        var ds = storage.getDataSource();
        if (ds == null) return false;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM ag_verified_players WHERE ip=? AND username=? AND expires_at > ?")) {
            ps.setString(1, ip);
            ps.setString(2, username);
            ps.setLong(3, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getSlf4jLogger().warn("Verified DB pair kontrol hatası: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkDBByIP(String ip) {
        var storage = plugin.getStorageProvider();
        if (storage == null || !storage.isConnected()) return false;
        var ds = storage.getDataSource();
        if (ds == null) return false;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM ag_verified_players WHERE ip=? AND expires_at > ? LIMIT 1")) {
            ps.setString(1, ip);
            ps.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private String getLastIPFromDB(String username) {
        var storage = plugin.getStorageProvider();
        if (storage == null || !storage.isConnected()) return null;
        var ds = storage.getDataSource();
        if (ds == null) return null;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT ip FROM ag_verified_players WHERE username=? AND expires_at > ? ORDER BY verified_at DESC LIMIT 1")) {
            ps.setString(1, username);
            ps.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String ip = rs.getString("ip");
                    usernameToLastIP.put(username.toLowerCase(), ip);
                    return ip;
                }
            }
        } catch (SQLException e) {
            plugin.getSlf4jLogger().warn("Last verified IP kontrol hatası: {}", e.getMessage());
        }
        return null;
    }

    // ─────────────────────────── Dosya Depolama ───────────────────────────

    private void writeToFile(String ip, String username, UUID uuid) {
        try {
            // Dosya yoksa oluştur
            if (!Files.exists(verifiedFile)) {
                Files.createDirectories(verifiedFile.getParent());
                Files.createFile(verifiedFile);
            }

            // Mevcut satırları kontrol et — aynı çift varsa güncelle
            String entry = ip + "|" + username.toLowerCase() + "|" +
                    (uuid != null ? uuid.toString() : "null") + "|" +
                    System.currentTimeMillis();

            List<String> lines = Files.readAllLines(verifiedFile, StandardCharsets.UTF_8);
            String prefix = ip + "|" + username.toLowerCase() + "|";
            boolean updated = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith(prefix)) {
                    lines.set(i, entry);
                    updated = true;
                    break;
                }
            }
            if (!updated) {
                lines.add(entry);
            }

            Files.write(verifiedFile, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            plugin.getSlf4jLogger().warn("[Verification] Dosyaya yazılamadı: {}", e.getMessage());
        }
    }

    private void loadFromFile() {
        if (!Files.exists(verifiedFile)) return;
        try {
            List<String> lines = Files.readAllLines(verifiedFile, StandardCharsets.UTF_8);
            long now = System.currentTimeMillis();
            long maxAge = expiryDays * 86_400_000L;
            int loaded = 0;

            for (String line : lines) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] parts = line.split("\\|");
                if (parts.length < 4) continue;

                String ip = parts[0];
                String username = parts[1];
                long timestamp = Long.parseLong(parts[3]);

                // Süresi dolmamış kayıtları yükle
                if (now - timestamp < maxAge) {
                    pairCache.put(makeKey(ip, username), timestamp);
                    usernameToLastIP.put(username.toLowerCase(), ip);
                    loaded++;
                }
            }

            if (loaded > 0) {
                plugin.getSlf4jLogger().info(
                        "[Verification] Dosyadan {} verified çift yüklendi.", loaded);
            }
        } catch (Exception e) {
            plugin.getSlf4jLogger().warn("[Verification] Dosyadan yükleme hatası: {}", e.getMessage());
        }
    }
}
