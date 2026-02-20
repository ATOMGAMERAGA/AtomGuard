package com.atomguard.velocity.module.antiddos;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CountryResponse;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ülke bazlı IP engelleme.
 * <p>
 * MaxMind GeoIP2 Lite veritabanını direkt API üzerinden kullanır.
 * Reflection kullanımı ortadan kaldırıldı — tip güvenliği sağlandı.
 * <p>
 * Veritabanı dosyası: {@code {dataDir}/GeoLite2-Country.mmdb}
 * <p>
 * İki mod:
 * <ul>
 *   <li>Kara liste modu (varsayılan): {@code blockedCountries} listesindeki ülkelerden gelen IP'ler engellenir</li>
 *   <li>Beyaz liste modu: {@code allowedCountries} listesi dışındaki tüm IP'ler engellenir</li>
 * </ul>
 */
public class GeoBlocker {

    private DatabaseReader dbReader;
    private boolean        available = false;

    private final Set<String> blockedCountries;
    private final Set<String> allowedCountries;
    private final boolean     whitelistMode;
    private final Path        dbPath;
    private final Logger      logger;

    /**
     * @param dataDir       Plugin data dizini (GeoLite2-Country.mmdb burada aranır)
     * @param blocked       Engellenen ülke ISO kodları (kara liste modu)
     * @param allowed       İzin verilen ülke ISO kodları (beyaz liste modu)
     * @param whitelistMode true ise beyaz liste modu
     * @param logger        SLF4J logger
     */
    public GeoBlocker(Path dataDir, List<String> blocked, List<String> allowed,
                      boolean whitelistMode, Logger logger) {
        this.dbPath          = dataDir.resolve("GeoLite2-Country.mmdb");
        this.blockedCountries = new HashSet<>(blocked);
        this.allowedCountries = new HashSet<>(allowed);
        this.whitelistMode   = whitelistMode;
        this.logger          = logger;
    }

    /**
     * Veritabanını başlat.
     *
     * @throws IOException Veritabanı okunamazsa
     */
    public void initialize() throws IOException {
        if (!Files.exists(dbPath)) {
            logger.warn("GeoIP veritabanı bulunamadı: {}", dbPath);
            return;
        }
        try {
            File dbFile = dbPath.toFile();
            dbReader  = new DatabaseReader.Builder(dbFile).build();
            available = true;
            logger.info("GeoIP veritabanı yüklendi: {}", dbPath.getFileName());
        } catch (IOException e) {
            logger.warn("GeoIP veritabanı yüklenemedi: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * IP adresinin ülke ISO kodunu döndür.
     *
     * @param ip IP adresi (IPv4 veya IPv6)
     * @return ISO 3166-1 alpha-2 ülke kodu veya "XX" (bilinmiyor/hata)
     */
    public String getCountry(String ip) {
        if (!available || dbReader == null) return "XX";
        try {
            InetAddress     addr     = InetAddress.getByName(ip);
            CountryResponse response = dbReader.country(addr);
            String          iso      = response.getCountry().getIsoCode();
            return iso != null ? iso : "XX";
        } catch (GeoIp2Exception | IOException e) {
            return "XX";
        }
    }

    /**
     * Bu IP engellenmiş mi?
     *
     * @param ip IP adresi
     * @return true ise engellenmeli
     */
    public boolean isBlocked(String ip) {
        if (!available) return false;
        String country = getCountry(ip);
        if ("XX".equals(country)) return false;

        if (whitelistMode) {
            return !allowedCountries.contains(country);
        }
        return blockedCountries.contains(country);
    }

    /**
     * Veritabanı hazır mı?
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Veritabanı okuyucusunu kapat.
     */
    public void close() {
        if (dbReader != null) {
            try {
                dbReader.close();
            } catch (IOException ignored) {
            } finally {
                dbReader  = null;
                available = false;
            }
        }
    }
}
