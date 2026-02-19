package com.atomguard.velocity.module.firewall;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.geo.CountryFilterModule;
import com.atomguard.velocity.module.firewall.ExceptionType;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class WhitelistManager {

    private final AtomGuardVelocity plugin;
    private final Logger logger;
    private final File whitelistFile;
    private final List<WhitelistEntry> entries = new CopyOnWriteArrayList<>();
    private final Gson gson = new Gson();

    public WhitelistManager(AtomGuardVelocity plugin) {
        this.plugin = plugin;
        this.logger = plugin.getSlf4jLogger();
        this.whitelistFile = new File(plugin.getDataDirectory().toFile(), plugin.getConfigManager().getString("guvenlik-duvari.whitelist.beyaz-liste.dosya", "whitelist.json"));
    }

    public void load() {
        if (!whitelistFile.exists()) {
            save(); 
            return;
        }

        try (FileReader reader = new FileReader(whitelistFile)) {
            List<WhitelistEntry> loaded = gson.fromJson(reader, new TypeToken<List<WhitelistEntry>>(){}.getType());
            if (loaded != null) {
                entries.clear();
                entries.addAll(loaded);
                logger.info("Whitelist loaded: {} entries", entries.size());
            }
        } catch (IOException e) {
            logger.error("Failed to load whitelist", e);
        }
    }

    public void save() {
        try (FileWriter writer = new FileWriter(whitelistFile)) {
            gson.toJson(entries, writer);
        } catch (IOException e) {
            logger.error("Failed to save whitelist", e);
        }
    }

    public void add(WhitelistEntry entry) {
        entries.add(entry);
        save();
    }
    
    public void add(String ip) {
        add(new WhitelistEntry(ExceptionType.IP, ip, "Manual add", 0));
    }

    public void remove(String value) {
        entries.removeIf(e -> e.getValue().equals(value));
        save();
    }

    public boolean isWhitelisted(String ip) {
        cleanupExpired();
        for (WhitelistEntry entry : entries) {
            if (entry.getType() == ExceptionType.IP && entry.getValue().equals(ip)) return true;
            if (entry.getType() == ExceptionType.CIDR && checkCidr(ip, entry.getValue())) return true;
        }
        
        if (isWhitelistedByCountry(ip)) return true;
        
        return false;
    }

    public boolean isWhitelistedByCountry(String ip) {
        CountryFilterModule module = plugin.getModuleManager().getModule(CountryFilterModule.class);
        if (module != null && module.isEnabled()) {
            String country = module.getCountryCode(ip);
            if (country == null) return false;
            
            for (WhitelistEntry entry : entries) {
                if (entry.getType() == ExceptionType.COUNTRY && entry.getValue().equalsIgnoreCase(country)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    // CIDR check helper
    private boolean checkCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) return false;

            InetAddress ipAddr = InetAddress.getByName(ip);
            InetAddress cidrAddr = InetAddress.getByName(parts[0]);
            int prefix = Integer.parseInt(parts[1]);

            byte[] ipBytes = ipAddr.getAddress();
            byte[] cidrBytes = cidrAddr.getAddress();

            if (ipBytes.length != cidrBytes.length) return false;

            int byteIndex = 0;
            int bitIndex = 0;
            for (int i = 0; i < prefix; i++) {
                if ((ipBytes[byteIndex] & (1 << (7 - bitIndex))) != (cidrBytes[byteIndex] & (1 << (7 - bitIndex)))) {
                    return false;
                }
                bitIndex++;
                if (bitIndex == 8) {
                    bitIndex = 0;
                    byteIndex++;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void cleanupExpired() {
        if (entries.removeIf(WhitelistEntry::isExpired)) {
            save();
        }
    }
    
    public List<WhitelistEntry> getEntries() {
        return entries;
    }
}
