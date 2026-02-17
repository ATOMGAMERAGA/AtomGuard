package com.atomguard.velocity.module.antiddos;

import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ülke bazlı IP engelleme. MaxMind GeoIP2 Lite veritabanı kullanır (yansıma ile).
 */
public class GeoBlocker {

    private Object dbReader;
    private boolean available = false;
    private final Set<String> blockedCountries;
    private final Set<String> allowedCountries;
    private final boolean whitelistMode;
    private final Path dbPath;
    private final Logger logger;

    public GeoBlocker(Path dataDir, List<String> blocked, List<String> allowed,
                      boolean whitelistMode, Logger logger) {
        this.dbPath = dataDir.resolve("GeoLite2-Country.mmdb");
        this.blockedCountries = new HashSet<>(blocked);
        this.allowedCountries = new HashSet<>(allowed);
        this.whitelistMode = whitelistMode;
        this.logger = logger;
    }

    public void initialize() throws IOException {
        if (!Files.exists(dbPath)) {
            logger.warn("GeoIP veritabanı bulunamadı: {}", dbPath);
            return;
        }
        try {
            Class<?> builderClass = Class.forName("com.maxmind.geoip2.DatabaseReader$Builder");
            Object builder = builderClass.getConstructor(java.io.File.class).newInstance(dbPath.toFile());
            dbReader = builderClass.getMethod("build").invoke(builder);
            available = true;
            logger.info("GeoIP veritabanı yüklendi.");
        } catch (Exception e) {
            logger.warn("GeoIP yüklenemedi: {}", e.getMessage());
        }
    }

    public String getCountry(String ip) {
        if (!available || dbReader == null) return "XX";
        try {
            InetAddress addr = InetAddress.getByName(ip);
            Object response = dbReader.getClass().getMethod("country", InetAddress.class).invoke(dbReader, addr);
            Object country = response.getClass().getMethod("getCountry").invoke(response);
            Object iso = country.getClass().getMethod("getIsoCode").invoke(country);
            return iso != null ? iso.toString() : "XX";
        } catch (Exception e) { return "XX"; }
    }

    public boolean isBlocked(String ip) {
        if (!available) return false;
        String country = getCountry(ip);
        if ("XX".equals(country)) return false;
        if (whitelistMode) return !allowedCountries.contains(country);
        return blockedCountries.contains(country);
    }

    public boolean isAvailable() { return available; }

    public void close() {
        if (dbReader != null) {
            try { dbReader.getClass().getMethod("close").invoke(dbReader); } catch (Exception ignored) {}
            dbReader = null;
            available = false;
        }
    }
}
