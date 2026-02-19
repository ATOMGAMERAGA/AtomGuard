package com.atomguard.storage;

import com.atomguard.AtomGuard;
import com.atomguard.api.storage.IStorageProvider;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class SQLiteStorageProvider implements IStorageProvider {

    private final AtomGuard plugin;
    private final File dbFile;
    private HikariDataSource dataSource;
    private final ExecutorService executor;

    public SQLiteStorageProvider(AtomGuard plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "atomguard.db");
        this.executor = Executors.newFixedThreadPool(1); // SQLite handles better with 1 thread for writing
    }

    @Override
    public void connect() throws Exception {
        if (!dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setPoolName("AtomGuard-SQLite-Pool");
        config.setMaximumPoolSize(1); // Recommended for SQLite to avoid lock issues
        config.setConnectionTestQuery("SELECT 1");

        this.dataSource = new HikariDataSource(config);
        
        createTables();
    }

    private void createTables() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            // Player Data Table
            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS atomguard_player_data (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "data TEXT NOT NULL, " +
                            "last_updated DATETIME DEFAULT CURRENT_TIMESTAMP" +
                            ")")) {
                ps.executeUpdate();
            }

            // Statistics Table
            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS atomguard_statistics (" +
                            "stat_key VARCHAR(64) PRIMARY KEY, " +
                            "stat_value BIGINT DEFAULT 0" +
                            ")")) {
                ps.executeUpdate();
            }

            // Blocked IPs Table
            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS atomguard_blocked_ips (" +
                            "ip_address VARCHAR(45) PRIMARY KEY, " +
                            "reason VARCHAR(255), " +
                            "expiry BIGINT, " +
                            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                            ")")) {
                ps.executeUpdate();
            }
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
        return "SQLite";
    }

    @Override
    public CompletableFuture<Void> savePlayerData(@NotNull UUID uuid, @NotNull Map<String, Object> data) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "INSERT INTO atomguard_player_data (uuid, data) VALUES (?, ?) " +
                                 "ON CONFLICT(uuid) DO UPDATE SET data = ?, last_updated = CURRENT_TIMESTAMP")) {
                
                String jsonData = new JSONObject(data).toString();
                
                ps.setString(1, uuid.toString());
                ps.setString(2, jsonData);
                ps.setString(3, jsonData);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "SQLite error", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Map<String, Object>> loadPlayerData(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> data = new HashMap<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement ps = connection.prepareStatement("SELECT data FROM atomguard_player_data WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String jsonData = rs.getString("data");
                        if (jsonData != null && !jsonData.isEmpty()) {
                            JSONObject json = new JSONObject(jsonData);
                            data.putAll(json.toMap());
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "SQLite error", e);
            }
            return data;
        }, executor);
    }

    @Override
    public CompletableFuture<Void> saveStatistics(@NotNull Map<String, Object> statistics) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                String sql = "INSERT INTO atomguard_statistics (stat_key, stat_value) VALUES (?, ?) " +
                        "ON CONFLICT(stat_key) DO UPDATE SET stat_value = ?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    for (Map.Entry<String, Object> entry : statistics.entrySet()) {
                        if (entry.getValue() instanceof Number) {
                            ps.setString(1, entry.getKey());
                            ps.setLong(2, ((Number) entry.getValue()).longValue());
                            ps.setLong(3, ((Number) entry.getValue()).longValue());
                            ps.addBatch();
                        }
                    }
                    ps.executeBatch();
                }
                connection.commit();
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "SQLite error", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Map<String, Object>> loadStatistics() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> stats = new HashMap<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement ps = connection.prepareStatement("SELECT stat_key, stat_value FROM atomguard_statistics");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    stats.put(rs.getString("stat_key"), rs.getLong("stat_value"));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "SQLite error", e);
            }
            return stats;
        }, executor);
    }

    @Override
    public CompletableFuture<Void> saveBlockedIP(@NotNull String ipAddress, @NotNull String reason, long expiry) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "INSERT INTO atomguard_blocked_ips (ip_address, reason, expiry) VALUES (?, ?, ?) " +
                                 "ON CONFLICT(ip_address) DO UPDATE SET reason = ?, expiry = ?")) {
                ps.setString(1, ipAddress);
                ps.setString(2, reason);
                ps.setLong(3, expiry);
                ps.setString(4, reason);
                ps.setLong(5, expiry);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "SQLite error", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> removeBlockedIP(@NotNull String ipAddress) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement ps = connection.prepareStatement("DELETE FROM atomguard_blocked_ips WHERE ip_address = ?")) {
                ps.setString(1, ipAddress);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "SQLite error", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Set<String>> getBlockedIPs() {
        return CompletableFuture.supplyAsync(() -> {
            Set<String> ips = new HashSet<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement ps = connection.prepareStatement("SELECT ip_address FROM atomguard_blocked_ips");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ips.add(rs.getString("ip_address"));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "SQLite error", e);
            }
            return ips;
        }, executor);
    }
}
