package com.atomguard.velocity.storage;

import com.atomguard.api.storage.IStorageProvider;
import com.atomguard.velocity.AtomGuardVelocity;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VelocityStorageProvider implements IStorageProvider {

    private final AtomGuardVelocity plugin;
    private final Logger logger;
    private HikariDataSource dataSource;
    private final ExecutorService executor;

    public VelocityStorageProvider(AtomGuardVelocity plugin) {
        this.plugin = plugin;
        this.logger = plugin.getSlf4jLogger();
        this.executor = Executors.newFixedThreadPool(2);
    }

    @Override
    public void connect() throws Exception {
        HikariConfig config = new HikariConfig();

        String type = plugin.getConfigManager().getString("depolama.tip", "sqlite");
        if ("mysql".equalsIgnoreCase(type)) {
            config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s",
                plugin.getConfigManager().getString("depolama.mysql.host", "localhost"),
                plugin.getConfigManager().getInt("depolama.mysql.port", 3306),
                plugin.getConfigManager().getString("depolama.mysql.veritabani", "atomguard")));
            config.setUsername(plugin.getConfigManager().getString("depolama.mysql.kullanici", "root"));
            config.setPassword(plugin.getConfigManager().getString("depolama.mysql.sifre", ""));
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        } else {
            Path dbPath = plugin.getDataDirectory().resolve("atomguard-velocity.db");
            config.setJdbcUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
        }

        config.setMaximumPoolSize(5);
        config.setConnectionTimeout(5000);
        config.setPoolName("atomguard-velocity-pool");
        this.dataSource = new HikariDataSource(config);
        
        initTables();
    }

    private void initTables() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            // Standard Blocked IPs (ag_bans as suggested in plan)
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS ag_bans (" +
                "ip VARCHAR(45) PRIMARY KEY, " +
                "reason TEXT NOT NULL, " +
                "banned_at BIGINT NOT NULL, " +
                "expires_at BIGINT NOT NULL, " +
                "banned_by VARCHAR(32) DEFAULT 'system')");

            // Verified Players
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS ag_verified_players (" +
                "ip VARCHAR(45) PRIMARY KEY, " +
                "username VARCHAR(16), " +
                "verified_at BIGINT NOT NULL, " +
                "last_login BIGINT NOT NULL)");

            // IP Reputation
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS ag_ip_reputation (" +
                "ip VARCHAR(45) PRIMARY KEY, " +
                "score INT NOT NULL DEFAULT 50, " +
                "total_violations INT DEFAULT 0, " +
                "last_violation BIGINT DEFAULT 0, " +
                "last_success BIGINT DEFAULT 0)");

            // Audit Log
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS ag_audit_log (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "timestamp BIGINT NOT NULL, " +
                "event_type VARCHAR(32) NOT NULL, " +
                "ip VARCHAR(45), " +
                "username VARCHAR(16), " +
                "module VARCHAR(32), " +
                "detail TEXT, " +
                "severity VARCHAR(8) DEFAULT 'INFO')");

            // Attack Snapshots
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS ag_attack_snapshots (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "timestamp BIGINT NOT NULL, " +
                "duration BIGINT NOT NULL, " +
                "peak_rate INT NOT NULL, " +
                "blocked_count INT NOT NULL, " +
                "dominant_source TEXT, " +
                "triggered_mode BOOLEAN)");

            // Whitelist
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS ag_whitelist (" +
                "ip VARCHAR(45) PRIMARY KEY, " +
                "added_at BIGINT NOT NULL, " +
                "reason TEXT)");

            // Player Data (for IStorageProvider compatibility)
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS atomguard_player_data (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "data TEXT NOT NULL, " +
                "last_updated BIGINT NOT NULL)");

            // Statistics (for IStorageProvider compatibility)
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS atomguard_statistics (" +
                "stat_key VARCHAR(64) PRIMARY KEY, " +
                "stat_value BIGINT DEFAULT 0)");

        } catch (SQLException e) {
            logger.error("Veritabanı tabloları oluşturulamadı: {}", e.getMessage());
        }
    }

    @Override
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        executor.shutdown();
    }

    @Override
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    @Override
    public @NotNull String getTypeName() {
        return dataSource != null ? dataSource.getDriverClassName().contains("sqlite") ? "SQLite" : "MySQL" : "Unknown";
    }

    // ═══════════════════════════════════════
    // Audit Logging (Plan Item 1 & 10)
    // ═══════════════════════════════════════

    public void logAuditEvent(String eventType, String ip, String username,
                               String module, String detail, String severity) {
        executor.execute(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO ag_audit_log (timestamp, event_type, ip, username, module, detail, severity) VALUES (?,?,?,?,?,?,?)")) {
                ps.setLong(1, System.currentTimeMillis());
                ps.setString(2, eventType);
                ps.setString(3, ip);
                ps.setString(4, username);
                ps.setString(5, module);
                ps.setString(6, detail);
                ps.setString(7, severity);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.warn("Audit log yazılamadı: {}", e.getMessage());
            }
        });
    }

    public void saveAttackSnapshot(com.atomguard.velocity.data.AttackSnapshot snapshot) {
        executor.execute(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO ag_attack_snapshots (timestamp, duration, peak_rate, blocked_count, dominant_source, triggered_mode) VALUES (?,?,?,?,?,?)")) {
                ps.setLong(1, snapshot.getTimestamp());
                ps.setLong(2, snapshot.getDuration());
                ps.setInt(3, snapshot.getPeakConnectionRate());
                ps.setInt(4, snapshot.getBlockedConnections());
                ps.setString(5, snapshot.getDominantSourceCIDR());
                ps.setBoolean(6, snapshot.isAttackModeTriggered());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.error("Attack snapshot kaydedilemedi: {}", e.getMessage());
            }
        });
    }

    public void saveBehaviorProfile(com.atomguard.velocity.data.PlayerBehaviorProfile profile) {
        executor.execute(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "REPLACE INTO atomguard_player_data (uuid, data, last_updated) VALUES (?, ?, ?)")) {
                
                JSONObject json = new JSONObject();
                json.put("sessions", profile.getTotalSessions());
                json.put("logins", profile.getSuccessfulLogins());
                json.put("fails", profile.getFailedChecks());
                json.put("first", profile.getFirstSeen());
                json.put("names", new ArrayList<>(profile.getUsedUsernames()));
                
                ps.setString(1, profile.getIp()); // IP'yi UUID alanına yazıyoruz
                ps.setString(2, json.toString());
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.error("Davranış profili kaydedilemedi: {}", e.getMessage());
            }
        });
    }

    public CompletableFuture<Map<String, JSONObject>> loadBehaviorProfiles() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, JSONObject> profiles = new HashMap<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT uuid, data FROM atomguard_player_data")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        profiles.put(rs.getString("uuid"), new JSONObject(rs.getString("data")));
                    }
                }
            } catch (Exception e) {
                logger.error("Davranış profilleri yüklenemedi: {}", e.getMessage());
            }
            return profiles;
        }, executor);
    }

    public void saveIPReputation(String ip, int score) {
        executor.execute(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "REPLACE INTO ag_ip_reputation (ip, score) VALUES (?, ?)")) {
                ps.setString(1, ip);
                ps.setInt(2, score);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.error("IP itibarı kaydedilemedi: {}", e.getMessage());
            }
        });
    }

    public CompletableFuture<Map<String, Integer>> loadIPReputations() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Integer> reputations = new HashMap<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT ip, score FROM ag_ip_reputation")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        reputations.put(rs.getString("ip"), rs.getInt("score"));
                    }
                }
            } catch (Exception e) {
                logger.error("IP itibarları yüklenemedi: {}", e.getMessage());
            }
            return reputations;
        }, executor);
    }

    public void saveWhitelistIP(String ip, String reason) {
        executor.execute(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "REPLACE INTO ag_whitelist (ip, added_at, reason) VALUES (?, ?, ?)")) {
                ps.setString(1, ip);
                ps.setLong(2, System.currentTimeMillis());
                ps.setString(3, reason);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.error("Whitelist IP kaydedilemedi: {}", e.getMessage());
            }
        });
    }

    public CompletableFuture<Set<String>> loadWhitelist() {
        return CompletableFuture.supplyAsync(() -> {
            Set<String> ips = new HashSet<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT ip FROM ag_whitelist")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ips.add(rs.getString("ip"));
                    }
                }
            } catch (Exception e) {
                logger.error("Whitelist yüklenemedi: {}", e.getMessage());
            }
            return ips;
        }, executor);
    }

    public void batchInsertAudit(List<com.atomguard.velocity.audit.AuditLogger.AuditEntry> entries) {
        if (entries == null || entries.isEmpty()) return;
        
        executor.execute(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ag_audit_log (timestamp, event_type, ip, username, module, detail, severity) VALUES (?,?,?,?,?,?,?)")) {
                    
                    for (com.atomguard.velocity.audit.AuditLogger.AuditEntry entry : entries) {
                        ps.setLong(1, entry.timestamp());
                        ps.setString(2, entry.type().name());
                        ps.setString(3, entry.ip());
                        ps.setString(4, entry.username());
                        ps.setString(5, entry.module());
                        ps.setString(6, entry.detail());
                        ps.setString(7, entry.severity().name());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();
            } catch (SQLException e) {
                logger.error("Batch audit insert hatası: {}", e.getMessage());
            }
        });
    }

    // ═══════════════════════════════════════
    // Verified Players (Plan Item 1)
    // ═══════════════════════════════════════

    public CompletableFuture<Void> saveVerifiedPlayer(String ip, String username, long verifiedAt) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "REPLACE INTO ag_verified_players (ip, username, verified_at, last_login) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, ip);
                ps.setString(2, username);
                ps.setLong(3, verifiedAt);
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.error("Doğrulanmış oyuncu kaydedilemedi: {}", e.getMessage());
            }
        }, executor);
    }

    // ═══════════════════════════════════════
    // IStorageProvider Implementation
    // ═══════════════════════════════════════

    @Override
    public CompletableFuture<Void> savePlayerData(@NotNull UUID uuid, @NotNull Map<String, Object> data) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "REPLACE INTO atomguard_player_data (uuid, data, last_updated) VALUES (?, ?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, new JSONObject(data).toString());
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.error("Oyuncu verisi kaydedilemedi: {}", e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Map<String, Object>> loadPlayerData(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> data = new HashMap<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT data FROM atomguard_player_data WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String jsonData = rs.getString("data");
                        if (jsonData != null && !jsonData.isEmpty()) {
                            data.putAll(new JSONObject(jsonData).toMap());
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("Oyuncu verisi yüklenemedi: {}", e.getMessage());
            }
            return data;
        }, executor);
    }

    @Override
    public CompletableFuture<Void> saveStatistics(@NotNull Map<String, Object> statistics) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(
                    "REPLACE INTO atomguard_statistics (stat_key, stat_value) VALUES (?, ?)")) {
                    for (Map.Entry<String, Object> entry : statistics.entrySet()) {
                        if (entry.getValue() instanceof Number) {
                            ps.setString(1, entry.getKey());
                            ps.setLong(2, ((Number) entry.getValue()).longValue());
                            ps.addBatch();
                        }
                    }
                    ps.executeBatch();
                }
                conn.commit();
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                logger.error("İstatistikler kaydedilemedi: {}", e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Map<String, Object>> loadStatistics() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> stats = new HashMap<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT stat_key, stat_value FROM atomguard_statistics");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    stats.put(rs.getString("stat_key"), rs.getLong("stat_value"));
                }
            } catch (SQLException e) {
                logger.error("İstatistikler yüklenemedi: {}", e.getMessage());
            }
            return stats;
        }, executor);
    }

    @Override
    public CompletableFuture<Void> saveBlockedIP(@NotNull String ipAddress, @NotNull String reason, long expiry) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "REPLACE INTO ag_bans (ip, reason, banned_at, expires_at) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, ipAddress);
                ps.setString(2, reason);
                ps.setLong(3, System.currentTimeMillis());
                ps.setLong(4, expiry);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.error("Ban kaydedilemedi: {}", e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> removeBlockedIP(@NotNull String ipAddress) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM ag_bans WHERE ip = ?")) {
                ps.setString(1, ipAddress);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.error("Ban kaldırılamadı: {}", e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Set<String>> getBlockedIPs() {
        return CompletableFuture.supplyAsync(() -> {
            Set<String> ips = new HashSet<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT ip FROM ag_bans WHERE expires_at > ? OR expires_at = 0")) {
                ps.setLong(1, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ips.add(rs.getString("ip"));
                    }
                }
            } catch (SQLException e) {
                logger.error("Ban'lar yüklenemedi: {}", e.getMessage());
            }
            return ips;
        }, executor);
    }

    public CompletableFuture<Map<String, Long>> loadActiveBansWithExpiry() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Long> bans = new HashMap<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT ip, expires_at FROM ag_bans WHERE expires_at > ? OR expires_at = 0")) {
                ps.setLong(1, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        bans.put(rs.getString("ip"), rs.getLong("expires_at"));
                    }
                }
            } catch (SQLException e) {
                logger.error("Detaylı ban listesi yüklenemedi: {}", e.getMessage());
            }
            return bans;
        }, executor);
    }

    public CompletableFuture<Set<String>> loadVerifiedPlayers() {
        return CompletableFuture.supplyAsync(() -> {
            Set<String> ips = new HashSet<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT ip FROM ag_verified_players")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ips.add(rs.getString("ip"));
                    }
                }
            } catch (SQLException e) {
                logger.error("Doğrulanmış oyuncular yüklenemedi: {}", e.getMessage());
            }
            return ips;
        }, executor);
    }
}
