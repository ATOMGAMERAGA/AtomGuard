package com.atomguard.velocity.util;

import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * GeoIP yardımcı sınıfı - MaxMind GeoIP2 Lite entegrasyonu.
 * GeoIP veritabanı yüklü değilse graceful degradation sağlar.
 */
public final class GeoIPUtil {

    private static Object databaseReader; // com.maxmind.geoip2.DatabaseReader (reflection-safe)
    private static boolean available = false;
    private static final Logger LOGGER_PLACEHOLDER = null;

    private GeoIPUtil() {}

    /**
     * GeoIP veritabanını belirtilen yoldan yükler.
     */
    public static boolean initialize(Path dbPath, Logger logger) {
        if (!Files.exists(dbPath)) {
            if (logger != null) logger.warn("GeoIP veritabanı bulunamadı: {}", dbPath);
            return false;
        }
        try {
            Class<?> readerClass = Class.forName("com.maxmind.geoip2.DatabaseReader");
            Class<?> builderClass = Class.forName("com.maxmind.geoip2.DatabaseReader$Builder");
            Object builder = builderClass.getConstructor(java.io.File.class).newInstance(dbPath.toFile());
            databaseReader = builderClass.getMethod("build").invoke(builder);
            available = true;
            if (logger != null) logger.info("GeoIP veritabanı yüklendi: {}", dbPath);
            return true;
        } catch (ClassNotFoundException e) {
            if (logger != null) logger.warn("GeoIP2 kütüphanesi bulunamadı. Ülke bazlı engelleme devre dışı.");
            return false;
        } catch (Exception e) {
            if (logger != null) logger.error("GeoIP veritabanı yüklenemedi: {}", e.getMessage());
            return false;
        }
    }

    /**
     * IP adresinin ülke kodunu döndürür (örn: "TR", "US").
     * GeoIP yüklü değilse "XX" döner.
     */
    public static String getCountryCode(String ip) {
        if (!available || databaseReader == null) return "XX";
        try {
            java.net.InetAddress addr = java.net.InetAddress.getByName(ip);
            Object response = databaseReader.getClass()
                .getMethod("country", java.net.InetAddress.class)
                .invoke(databaseReader, addr);
            Object country = response.getClass().getMethod("getCountry").invoke(response);
            Object isoCode = country.getClass().getMethod("getIsoCode").invoke(country);
            return isoCode != null ? isoCode.toString() : "XX";
        } catch (Exception e) {
            return "XX";
        }
    }

    /**
     * GeoIP'nin kullanılabilir olup olmadığını döndürür.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Veritabanını kapatır.
     */
    public static void close() {
        if (databaseReader != null) {
            try {
                databaseReader.getClass().getMethod("close").invoke(databaseReader);
            } catch (Exception ignored) {}
            databaseReader = null;
            available = false;
        }
    }

    /**
     * Ülke koduna göre Türkçe ülke adı döndürür (yaygın ülkeler için).
     */
    public static Optional<String> getCountryName(String isoCode) {
        return Optional.ofNullable(switch (isoCode.toUpperCase()) {
            case "TR" -> "Türkiye";
            case "US" -> "ABD";
            case "RU" -> "Rusya";
            case "CN" -> "Çin";
            case "DE" -> "Almanya";
            case "FR" -> "Fransa";
            case "GB" -> "İngiltere";
            case "NL" -> "Hollanda";
            case "UA" -> "Ukrayna";
            case "BR" -> "Brezilya";
            default -> null;
        });
    }
}
