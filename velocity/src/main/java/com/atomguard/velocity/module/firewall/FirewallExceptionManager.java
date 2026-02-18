package com.atomguard.velocity.module.firewall;

import com.atomguard.velocity.AtomGuardVelocity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class FirewallExceptionManager {

    private final AtomGuardVelocity plugin;
    private final List<FirewallException> exceptions = new CopyOnWriteArrayList<>();
    private final File file;
    private final Gson gson = new Gson();

    public FirewallExceptionManager(AtomGuardVelocity plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), plugin.getConfigManager().getString("guvenlik-duvari.istisnalar.dosya", "firewall_exceptions.json"));
        load();
    }

    private void load() {
        if (!file.exists()) {
            save();
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            List<FirewallException> loaded = gson.fromJson(reader, new TypeToken<List<FirewallException>>(){}.getType());
            if (loaded != null) {
                exceptions.clear();
                exceptions.addAll(loaded);
            }
        } catch (IOException e) {
            plugin.getSlf4jLogger().error("Failed to load firewall exceptions", e);
        }
    }

    public void save() {
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(exceptions, writer);
        } catch (IOException e) {
            plugin.getSlf4jLogger().error("Failed to save firewall exceptions", e);
        }
    }

    public void addException(FirewallException exception) {
        exceptions.add(exception);
        save();
    }

    public void removeException(String value) {
        exceptions.removeIf(e -> e.getValue().equals(value));
        save();
    }

    public List<FirewallException> listExceptions() {
        return new ArrayList<>(exceptions);
    }

    public boolean isExcepted(String ip, String username) {
        // Cleanup expired
        boolean removed = exceptions.removeIf(FirewallException::isExpired);
        if (removed) save();

        for (FirewallException e : exceptions) {
            switch (e.getType()) {
                case IP:
                    if (e.getValue().equals(ip)) return true;
                    break;
                case USERNAME:
                    if (username != null && e.getValue().equalsIgnoreCase(username)) return true;
                    break;
                case COUNTRY:
                    // Requires CountryFilterModule
                    // Assuming country code is passed or resolved here?
                    // To avoid circular dependency or complex lookup here, 
                    // we might need CountryFilterModule to resolve IP to Country.
                    // Let's try to get it from plugin if available.
                    String country = resolveCountry(ip);
                    if (country != null && country.equalsIgnoreCase(e.getValue())) return true;
                    break;
                case CIDR:
                    if (checkCidr(ip, e.getValue())) return true;
                    break;
                case ASN:
                    // ASN check requires ASN database which might not be available.
                    // Placeholder or integration with VPNDetectionModule if it has ASN.
                    break;
            }
        }
        return false;
    }

    private String resolveCountry(String ip) {
        // Try to get CountryFilterModule
        try {
            // This assumes CountryFilterModule is registered and accessible
            // Since there is no direct getter in AtomGuardVelocity for CountryFilterModule yet (I need to add it),
            // this part is tricky.
            // I will add a getter for CountryFilterModule to AtomGuardVelocity later.
            // For now, I'll leave it as a placeholder that returns null or tries to access via module manager?
            // AtomGuardVelocity doesn't expose module map.
            // I will add the getter later.
            return null; 
        } catch (Exception e) {
            return null;
        }
    }

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

            // Check prefix bits
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
}
