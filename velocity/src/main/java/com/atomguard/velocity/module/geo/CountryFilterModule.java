package com.atomguard.velocity.module.geo;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;
import com.atomguard.velocity.module.firewall.FirewallModule;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CountryFilterModule extends VelocityModule {

    private final Path databaseFile;
    private final GeoIPUpdater updater;
    private DatabaseReader databaseReader;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public CountryFilterModule(AtomGuardVelocity plugin) {
        super(plugin, "ulke-filtreleme");
        this.databaseFile = plugin.getDataDirectory().resolve("GeoLite2-Country.mmdb");
        this.updater = new GeoIPUpdater(plugin, databaseFile);
    }

    @Override
    public void onEnable() {
        try {
            // Start update check
            updater.checkAndUpdate();
            
            // Initial load
            loadDatabase();
            
            // Weekly check
            scheduler.scheduleAtFixedRate(updater::checkAndUpdate, 1, 7, TimeUnit.DAYS);
            
            logger.info("Country Filter module enabled.");
        } catch (Exception e) {
            logger.error("Failed to enable Country Filter module", e);
        }
    }

    @Override
    public void onDisable() {
        try {
            if (databaseReader != null) {
                databaseReader.close();
            }
            scheduler.shutdownNow();
        } catch (IOException e) {
            logger.error("Error closing GeoIP database", e);
        }
        logger.info("Country Filter module disabled.");
    }

    private void loadDatabase() {
        if (!Files.exists(databaseFile)) {
            logger.warn("GeoIP database not found at " + databaseFile + ". Country filtering will be disabled until downloaded.");
            return;
        }

        try {
            if (databaseReader != null) {
                databaseReader.close();
            }
            databaseReader = new DatabaseReader.Builder(databaseFile.toFile()).build();
            logger.info("GeoIP database loaded successfully.");
        } catch (IOException e) {
            logger.error("Failed to load GeoIP database", e);
        }
    }

    public String getCountryCode(String ip) {
        if (databaseReader == null) return null;
        try {
            InetAddress ipAddress = InetAddress.getByName(ip);
            return databaseReader.country(ipAddress).getCountry().getIsoCode();
        } catch (IOException | GeoIp2Exception e) {
            // IP not found or error
            return null;
        }
    }

    public CountryFilterResult check(String ip) {
        if (!isEnabled()) return CountryFilterResult.allow();
        
        // Whitelist exemption
        if (getConfigBoolean("beyaz-liste-muafiyeti", true)) {
            FirewallModule firewall = plugin.getFirewallModule();
            if (firewall != null && firewall.getWhitelistManager() != null) {
                if (firewall.getWhitelistManager().isWhitelisted(ip)) {
                    return CountryFilterResult.allow();
                }
            }
        }

        String countryCode = getCountryCode(ip);
        String unknownPolicy = getConfigString("bilinmeyen-ulke", "izin-ver");
        
        if (countryCode == null) {
            if ("engelle".equalsIgnoreCase(unknownPolicy)) {
                return CountryFilterResult.deny("Bilinmeyen Ülke");
            }
            return CountryFilterResult.allow();
        }

        String mode = getConfigString("mod", "blacklist");
        Set<String> blockedCountries = new HashSet<>(getConfigStringList("engelli-ulkeler"));
        Set<String> allowedCountries = new HashSet<>(getConfigStringList("izinli-ulkeler"));

        if ("whitelist".equalsIgnoreCase(mode)) {
            if (!allowedCountries.contains(countryCode)) {
                return CountryFilterResult.deny("Ülkeniz izinli değil (" + countryCode + ")");
            }
        } else {
            if (blockedCountries.contains(countryCode)) {
                return CountryFilterResult.deny("Ülkeniz engelli (" + countryCode + ")");
            }
        }

        return CountryFilterResult.allow();
    }
    
    public void reloadDatabaseFile() {
        loadDatabase();
    }
}