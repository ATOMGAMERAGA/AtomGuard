package com.atomguard.velocity.module.geo;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.util.HttpClientUtil;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GeoIPUpdater {

    private final AtomGuardVelocity plugin;
    private final Logger logger;
    private final Path databaseFile;
    private final String updateUrlTemplate = "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-Country&license_key=%s&suffix=tar.gz";

    public GeoIPUpdater(AtomGuardVelocity plugin, Path databaseFile) {
        this.plugin = plugin;
        this.logger = plugin.getSlf4jLogger();
        this.databaseFile = databaseFile;
    }

    public void checkAndUpdate() {
        if (!plugin.getConfigManager().getBoolean("ulke-filtreleme.maxmind.otomatik-guncelle", true)) {
            return;
        }

        String licenseKey = plugin.getConfigManager().getString("ulke-filtreleme.maxmind.lisans-anahtari", "");
        if (licenseKey == null || licenseKey.isEmpty()) {
            // Only warn if module is active but key is missing
            if (plugin.getConfigManager().getBoolean("ulke-filtreleme.aktif", false)) {
                logger.warn("GeoIP updater enabled but license key is missing.");
            }
            return;
        }

        // Check file age (weekly update recommended)
        if (Files.exists(databaseFile)) {
            try {
                long age = System.currentTimeMillis() - Files.getLastModifiedTime(databaseFile).toMillis();
                long week = 7L * 24 * 60 * 60 * 1000;
                if (age < week) {
                    return; // Not old enough
                }
            } catch (IOException e) {
                logger.warn("Could not check GeoIP database age: " + e.getMessage());
            }
        }

        downloadDatabase(licenseKey);
    }

    public CompletableFuture<Boolean> downloadDatabase(String licenseKey) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Downloading GeoLite2-Country database...");
            try {
                String urlString = String.format(updateUrlTemplate, licenseKey);

                Path tempTarGz = plugin.getDataDirectory().resolve("GeoLite2-Country.tar.gz");
                HttpClientUtil.downloadToFile(urlString, tempTarGz,
                        Map.of(), Duration.ofSeconds(30));

                // Extract .mmdb from tar.gz
                boolean found = false;
                try (InputStream fi = Files.newInputStream(tempTarGz);
                     InputStream bi = new BufferedInputStream(fi);
                     InputStream gzi = new GzipCompressorInputStream(bi);
                     TarArchiveInputStream tarIn = new TarArchiveInputStream(gzi)) {

                    TarArchiveEntry entry;
                    while ((entry = tarIn.getNextTarEntry()) != null) {
                        if (entry.getName().endsWith(".mmdb")) {
                            Path tempMmdb = plugin.getDataDirectory().resolve("GeoLite2-Country.mmdb.tmp");
                            try (OutputStream out = Files.newOutputStream(tempMmdb)) {
                                byte[] buffer = new byte[1024];
                                int len;
                                while ((len = tarIn.read(buffer)) != -1) {
                                    out.write(buffer, 0, len);
                                }
                            }
                            Files.move(tempMmdb, databaseFile, StandardCopyOption.REPLACE_EXISTING);
                            found = true;
                            break;
                        }
                    }
                }

                Files.deleteIfExists(tempTarGz);

                if (found) {
                    logger.info("GeoLite2-Country database updated successfully.");
                    return true;
                } else {
                    logger.error("Could not find .mmdb file in downloaded archive.");
                    return false;
                }

            } catch (Exception e) {
                logger.error("Error updating GeoIP database", e);
                return false;
            }
        });
    }
}